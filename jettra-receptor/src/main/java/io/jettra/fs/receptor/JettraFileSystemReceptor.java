package io.jettra.fs.receptor;

import io.jettra.fs.chunks.ChunkManager;

import io.jettra.grpc.JettraObserver;
import io.jettra.fs.grpc.JettraChunk;
import io.jettra.fs.grpc.JettraTransferService;
import io.jettra.fs.grpc.TransferStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class JettraFileSystemReceptor implements JettraTransferService {
    private final Map<String, FileChannel> activeChannels = new ConcurrentHashMap<>();
    private final Map<String, Integer> chunkTracker = new ConcurrentHashMap<>();
    private final String baseDir;
    private FileLock instanceLock;
    private FileChannel lockChannel;

    public JettraFileSystemReceptor(String baseDir) {
        this.baseDir = baseDir;
        initializeInstance();
    }

    private void initializeInstance() {
        try {
            File tempDir = new File(baseDir, ".jettra_receptor_temp");
            if (!tempDir.exists()) {
                if (!tempDir.mkdirs()) {
                    // If we cannot create the directory, we just return early
                    // This happens when browsing read-only directories like "/"
                    return;
                }
            }

            File lockFile = new File(tempDir, ".lock");
            lockChannel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            
            try {
                instanceLock = lockChannel.tryLock();
            } catch (java.nio.channels.OverlappingFileLockException e) {
                // Ya tenemos un lock en esta misma JVM (múltiples instancias locales de JettraFileSystemReceptor)
                // Esto es seguro ignorarlo.
                return;
            }

            if (instanceLock == null) {
                System.err.println("Error: Ya hay otra instancia de JettraFileSystem Receptor en ejecución.");
                // For a UI component we might not want to System.exit(1), but since we can't change architecture let's leave it
                // Or maybe just don't exit if it's just browsing? Actually if it's already running, another instance doing listFiles is fine.
                // Let's just return.
                return;
            }

            // Limpieza inicial: Borrar todo en tempDir excepto el archivo .lock
            Files.walk(tempDir.toPath())
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .filter(f -> !f.getName().equals(".lock") && !f.equals(tempDir))
                 .forEach(File::delete);
            System.out.println("Limpieza de .jettra_receptor_temp completada en " + baseDir);
        } catch (Exception e) {
            System.err.println("Advertencia: No se pudo inicializar instancia receptor en " + baseDir + " - " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public String getBaseDir() {
        return baseDir;
    }

    public java.util.Map<String, Object> listFiles() {
        return listFiles(false, 3); // Default depth 3
    }

    public java.util.Map<String, Object> listFiles(boolean showHidden) {
        return listFiles(showHidden, 3);
    }
    
    public java.util.Map<String, Object> listFiles(boolean showHidden, int maxDepth) {
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        java.io.File dir = new java.io.File(baseDir);
        if (dir.exists() && dir.isDirectory()) {
            addFilesToMap(dir, root, showHidden, 0, maxDepth);
        }
        return root;
    }

    private void addFilesToMap(java.io.File dir, java.util.Map<String, Object> map, boolean showHidden, int currentDepth, int maxDepth) {
        if (currentDepth >= maxDepth) return;
        
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (java.io.File f : files) {
                if (!showHidden && f.isHidden()) continue;
                if (f.isDirectory()) {
                    String name = f.getName();
                    if (name.equals("node_modules") || name.equals(".m2") || name.equals(".cache") || name.equals(".npm") || name.equals(".git") || name.equals("target") || name.equals("build") || name.equals(".gemini") || name.equals(".var") || name.equals(".local") || name.equals(".jettra_sender_temp") || name.equals(".jettra_receptor_temp")) {
                        map.put(f.getName(), new java.util.LinkedHashMap<>());
                    } else {
                        java.util.Map<String, Object> child = new java.util.LinkedHashMap<>();
                        map.put(f.getName(), child);
                        addFilesToMap(f, child, showHidden, currentDepth + 1, maxDepth);
                    }
                } else {
                    map.put(f.getName(), null);
                }
            }
        }
    }

    public boolean deletePath(String relativePath) {
        try {
            java.io.File file = new java.io.File(baseDir, relativePath);
            if (file.isDirectory()) {
                return deleteDirectory(file);
            }
            return file.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean deleteDirectory(java.io.File directory) {
        java.io.File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (java.io.File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directory.delete();
    }

    public boolean renamePath(String oldPath, String newName) {
        try {
            java.io.File oldFile = new java.io.File(baseDir, oldPath);
            java.io.File newFile = new java.io.File(oldFile.getParentFile(), newName);
            return oldFile.renameTo(newFile);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean copyPath(String sourcePath, String destPath) {
        try {
            Path source = Paths.get(baseDir, sourcePath);
            Path dest = Paths.get(baseDir, destPath);
            
            if (java.nio.file.Files.isDirectory(source)) {
                java.nio.file.Files.walk(source)
                    .filter(s -> !s.toString().contains(".jettra_sender_temp") && !s.toString().contains(".jettra_receptor_temp"))
                    .forEach(s -> {
                    try {
                        java.nio.file.Files.copy(s, dest.resolve(source.relativize(s)), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                java.nio.file.Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void call(String methodName, byte[] requestData, io.jettra.grpc.JettraObserver<byte[]> responseObserver) {
        if ("sendChunk".equals(methodName)) {
            processRawChunk(requestData, new io.jettra.grpc.JettraObserver<TransferStatus>() {
                @Override
                public void onNext(TransferStatus value) {
                    try {
                        responseObserver.onNext(value.toByteArray());
                    } catch (IOException e) {
                        responseObserver.onError(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    responseObserver.onError(t);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            });
        } else {
            responseObserver.onError(new UnsupportedOperationException("Method " + methodName + " not implemented"));
        }
    }

    @Override
    public void sendChunk(JettraChunk chunk, io.jettra.grpc.JettraObserver<TransferStatus> responseObserver) {
        // This method might be called directly or via reflection/dispatch
        processChunk(chunk, responseObserver);
    }

    public void processRawChunk(byte[] data, JettraObserver<TransferStatus> responseObserver) {
        try {
            JettraChunk chunk = JettraChunk.fromByteArray(data);
            processChunk(chunk, responseObserver);
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private void processChunk(JettraChunk chunk, JettraObserver<TransferStatus> responseObserver) {
        try {
            String fileId = chunk.getFileId();
            byte[] data = chunk.getData();
            
            if (chunk.getIsCompressed()) {
                data = ChunkManager.decompress(data);
            }

            // Crear directorio temporal para el archivo (UUID)
            Path tempDir = Paths.get(baseDir, ".jettra_receptor_temp", fileId);
            if (!java.nio.file.Files.exists(tempDir)) {
                java.nio.file.Files.createDirectories(tempDir);
            }

            // Guardar el chunk como archivo físico
            File chunkFile = new File(tempDir.toFile(), "chunk_" + chunk.getChunkIndex() + ".jtra");
            try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
                fos.write(data);
            }

            int total = chunk.getTotalChunks();
            int currentDone = chunkTracker.merge(fileId, 1, Integer::sum);
            
            boolean isAllReceived = currentDone == total;
            
            if (isAllReceived) {
                // Reconstruir el archivo original
                File finalFile = new File(baseDir, chunk.getFileName());
                ChunkManager.mergeFiles(finalFile, tempDir.toFile(), total);
                
                // Limpieza: Borrar chunks y carpeta UUID
                deleteDirectory(tempDir.toFile());
                chunkTracker.remove(fileId);
                
                System.out.println("Archivo reconstruido y chunks eliminados: " + chunk.getFileName());
            }

            TransferStatus status = TransferStatus.newBuilder()
                    .setSuccess(true)
                    .setMessage("Chunk " + chunk.getChunkIndex() + " recibido físicamente")
                    .build();
            
            responseObserver.onNext(status);
            responseObserver.onCompleted();

        } catch (Exception e) {
            TransferStatus errorStatus = TransferStatus.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error en recepción física: " + e.getMessage())
                    .build();
            responseObserver.onNext(errorStatus);
            responseObserver.onError(e);
        }
    }

    public java.util.Map<String, Object> listPath(String relativePath, boolean showHidden) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.io.File dir = relativePath.isEmpty() ? new java.io.File(baseDir) : new java.io.File(baseDir, relativePath);
        
        if (dir.exists() && dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                java.util.Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (java.io.File f : files) {
                    if (!showHidden && f.isHidden()) continue;
                    if (f.isDirectory()) {
                        String name = f.getName();
                        if (name.equals(".jettra_sender_temp") || name.equals(".jettra_receptor_temp")) continue;
                        result.put(f.getName(), new java.util.LinkedHashMap<>());
                    } else {
                        result.put(f.getName(), null);
                    }
                }
            }
        }
        return result;
    }
}
