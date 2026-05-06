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

        System.out.println("Iniciando transferencia de " + fileName + " (" + totalChunks + " trozos)");

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            for (int i = 0; i < totalChunks; i++) {
                final int index = i;
                // Cada trozo se envía en un Virtual Thread para máxima concurrencia
                executor.submit(() -> {
                    try {
                        byte[] buffer = new byte[ChunkManager.CHUNK_SIZE];
                        int bytesRead;
                        synchronized (raf) {
                            raf.seek((long) index * ChunkManager.CHUNK_SIZE);
                            bytesRead = raf.read(buffer);
                        }

                        if (bytesRead > 0) {
                            byte[] actualData = bytesRead == ChunkManager.CHUNK_SIZE ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
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

                            // Realizamos la llamada gRPC nativa
                            client.call("JettraTransferService", "sendChunk", new byte[0]); // MOCKED serialized data for now
                            System.out.print(".");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        System.out.println("\nTransferencia completada.");
    }

}
