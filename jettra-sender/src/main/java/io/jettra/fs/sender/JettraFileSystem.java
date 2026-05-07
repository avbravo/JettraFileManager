package io.jettra.fs.sender;

import io.jettra.fs.chunks.ChunkManager;

import io.jettra.fs.grpc.JettraChunk;
import io.jettra.fs.grpc.TransferStatus;
import io.jettra.grpc.JettraGRPCClient;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class JettraFileSystem {
    private final JettraGRPCClient client;
    private final ExecutorService executor; // Usaremos Virtual Threads

    public JettraFileSystem(String host, int port) {
        this.client = new JettraGRPCClient(host, port);
        // Java 25 / Virtual Threads
        this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }

    public void sendFile(File file) throws Exception {
        String fileId = UUID.randomUUID().toString();
        long fileSize = file.length();
        int totalChunks = ChunkManager.calculateTotalChunks(fileSize);
        String fileName = file.getName();

        System.out.println("Iniciando transferencia de " + fileName + " (" + totalChunks + " trozos de 1.5MB)");

        // Cada archivo inicia su propio proceso en un hilo virtual
        executor.submit(() -> {
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.READ)) {
                for (int i = 0; i < totalChunks; i++) {
                    final int index = i;
                    long position = (long) index * ChunkManager.CHUNK_SIZE;
                    int size = (int) Math.min(ChunkManager.CHUNK_SIZE, fileSize - position);

                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(size);
                    channel.read(buffer, position);
                    buffer.flip();
                    byte[] actualData = buffer.array();

                    // Enviamos el trozo en paralelo usando otro hilo virtual
                    executor.submit(() -> {
                        try {
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
                            System.out.print(".");
                        } catch (Exception e) {
                            System.err.println("\nError enviando trozo " + index + " de " + fileName + ": " + e.getMessage());
                        }
                    });
                }
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
