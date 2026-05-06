package io.jettra.fs.receptor;

import io.jettra.fs.chunks.ChunkManager;

import io.jettra.grpc.JettraObserver;
import io.jettra.fs.grpc.JettraChunk;
import io.jettra.fs.grpc.JettraTransferService;
import io.jettra.fs.grpc.TransferStatus;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class JettraFileSystemReceptor implements JettraTransferService {
    private final Map<String, RandomAccessFile> activeFiles = new ConcurrentHashMap<>();
    private final String baseDir;

    public JettraFileSystemReceptor(String baseDir) {
        this.baseDir = baseDir;
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
                    if (name.equals("node_modules") || name.equals(".m2") || name.equals(".cache") || name.equals(".npm") || name.equals(".git") || name.equals("target") || name.equals("build") || name.equals(".gemini") || name.equals(".var") || name.equals(".local")) {
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
                java.nio.file.Files.walk(source).forEach(s -> {
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
    public void sendChunk(JettraChunk chunk, JettraObserver<TransferStatus> responseObserver) {
        try {
            String fileId = chunk.getFileId();
            byte[] data = chunk.getData();
            
            if (chunk.getIsCompressed()) {
                data = ChunkManager.decompress(data);
            }

            RandomAccessFile raf = activeFiles.computeIfAbsent(fileId, id -> {
                try {
                    Path path = Paths.get(baseDir, chunk.getFileName());
                    RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw");
                    f.setLength(chunk.getFileSize());
                    return f;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            synchronized (raf) {
                raf.seek((long) chunk.getChunkIndex() * ChunkManager.CHUNK_SIZE);
                raf.write(data);
            }

            boolean isLast = chunk.getChunkIndex() == chunk.getTotalChunks() - 1;
            if (isLast) {
                // Podríamos esperar a que todos los anteriores lleguen, pero RandomAccessFile permite huecos.
                // En una implementación real, verificaríamos integridad.
                raf.close();
                activeFiles.remove(fileId);
            }

            responseObserver.onNext(TransferStatus.newBuilder()
                    .setSuccess(true)
                    .setMessage("Chunk " + chunk.getChunkIndex() + " recibido")
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            TransferStatus errorStatus = TransferStatus.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
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
