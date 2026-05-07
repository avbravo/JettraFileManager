import java.io.File;
import io.jettra.fs.chunks.ChunkManager;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TestDirect {
    public static void main(String[] args) throws Exception {
        File s = new File("test.dat");
        File d = new File("dest_dir");
        d.mkdirs();
        
        long sz = s.length(); int n = ChunkManager.calculateTotalChunks(sz);
        File destFile = new File(d, "test.dat");
        if (destFile.getParentFile() != null) destFile.getParentFile().mkdirs();
        
        final String fileId = UUID.randomUUID().toString();
        File tempSrcDir = new File(System.getProperty("user.home"), ".jettra_sender_temp/" + fileId);
        System.out.println("Splitting...");
        ChunkManager.splitFile(s, tempSrcDir);

        File tempDestDir = new File(d, ".jettra_receptor_temp/" + fileId);
        tempDestDir.mkdirs();

        CountDownLatch fileLatch = new CountDownLatch(n);
        AtomicInteger chunksDoneForFile = new AtomicInteger(0);
        Semaphore chunkLimit = new Semaphore(64);
        
        ExecutorService transferExecutor = Executors.newVirtualThreadPerTaskExecutor();

        System.out.println("Transferring...");
        for (int i = 0; i < n; i++) {
            chunkLimit.acquire();
            final int idx = i;
            transferExecutor.submit(() -> {
                try {
                    File srcChunk = new File(tempSrcDir, "chunk_" + idx + ".jtra");
                    File destChunk = new File(tempDestDir, "chunk_" + idx + ".jtra");
                    Files.copy(srcChunk.toPath(), destChunk.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    fileLatch.countDown();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    fileLatch.countDown();
                } finally {
                    chunkLimit.release();
                }
            });
        }
        
        fileLatch.await();
        System.out.println("Merging...");
        ChunkManager.mergeFiles(destFile, tempDestDir, n);
        System.out.println("Done! File size: " + destFile.length());
        
        transferExecutor.shutdown();
    }
}
