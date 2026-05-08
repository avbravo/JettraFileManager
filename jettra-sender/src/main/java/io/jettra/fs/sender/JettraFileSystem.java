package io.jettra.fs.sender;

import io.jettra.fs.chunks.ChunkManager;

import io.jettra.fs.grpc.JettraChunk;
import io.jettra.fs.grpc.TransferStatus;
import io.jettra.grpc.JettraGRPCClient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class JettraFileSystem {
    private final JettraGRPCClient client;
    private final ExecutorService executor; // Usaremos Virtual Threads
    private FileLock instanceLock;
    private FileChannel lockChannel;

    public JettraFileSystem(String host, int port) {
        this.client = new JettraGRPCClient(host, port);
        // Java 25 / Virtual Threads
        this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        initializeInstance();
    }

    private void initializeInstance() {
        try {
            File tempDir = new File(".jettra_sender_temp");
            if (!tempDir.exists()) tempDir.mkdirs();

            File lockFile = new File(tempDir, ".lock");
            lockChannel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            instanceLock = lockChannel.tryLock();

            if (instanceLock == null) {
                System.err.println("Error: Ya hay otra instancia de JettraFileSystem Sender en ejecución.");
                System.exit(1);
            }

            // Limpieza inicial: Borrar todo en tempDir excepto el archivo .lock
            Files.walk(tempDir.toPath())
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .filter(f -> !f.getName().equals(".lock") && !f.equals(tempDir))
                 .forEach(File::delete);
            System.out.println("Limpieza de .jettra_sender_temp completada.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFile(File file) throws Exception {
        String fileId = UUID.randomUUID().toString();
        long fileSize = file.length();
        int totalChunks = ChunkManager.calculateTotalChunks(fileSize);
        String fileName = file.getName();

        System.out.println("Iniciando transferencia de " + fileName + " (" + totalChunks + " trozos físicos)");

        // Cada archivo inicia su propio proceso en un hilo virtual
        executor.submit(() -> {
            try {
                File senderTempDir = new File(".jettra_sender_temp", fileId);
                
                // 1. Dividir archivo en chunks físicos
                ChunkManager.splitFile(file, senderTempDir);

                // 2. Enviar chunks limitando la concurrencia a 10 hilos por archivo
                Semaphore semaphore = new Semaphore(10);
                CountDownLatch latch = new CountDownLatch(totalChunks);

                for (int i = 0; i < totalChunks; i++) {
                    final int index = i;
                    semaphore.acquire();
                    
                    // Enviamos el trozo en paralelo usando otro hilo virtual
                    executor.submit(() -> {
                        try {
                            File chunkFile = new File(senderTempDir, "chunk_" + index + ".jtra");
                            byte[] actualData = Files.readAllBytes(chunkFile.toPath());
                            byte[] compressedData = ChunkManager.compress(actualData);

                            JettraChunk chunk = JettraChunk.newBuilder()
                                    .setFileId(fileId)
                                    .setFileName(fileName)
                                    .setChunkIndex(index)
                                    .setTotalChunks(totalChunks)
                                    .setFileSize(fileSize)
                                    .setData(compressedData)
                                    .setIsCompressed(true)
                                    .build();

                            // Realizamos la llamada gRPC nativa con el chunk serializado
                            client.call("JettraTransferService", "sendChunk", chunk.toByteArray());
                            
                            // Una vez recibido en el receptor, se elimina del sender
                            Files.deleteIfExists(chunkFile.toPath());
                            System.out.print(".");
                        } catch (Exception e) {
                            System.err.println("\nError enviando trozo " + index + " de " + fileName + ": " + e.getMessage());
                        } finally {
                            semaphore.release();
                            latch.countDown();
                        }
                    });
                }
                
                // Esperar a que se envíen todos los chunks del archivo
                latch.await();
                
                // 3. Eliminar directorio temporal UUID
                Files.deleteIfExists(senderTempDir.toPath());
                System.out.println("\nTransferencia de " + fileName + " finalizada y temporales eliminados.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void waitForCompletion() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        System.out.println("\nTodas las transferencias completadas.");
    }
}
