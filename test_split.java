import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.UUID;

public class test_split {
    public static final int CHUNK_SIZE = 2 * 1024 * 1024;
    public static void main(String[] args) throws Exception {
        File src = new File("dummy_file.txt");
        try (FileOutputStream fos = new FileOutputStream(src)) {
            for(int i=0; i<3*1024*1024; i++) {
                fos.write('A');
            }
        }
        File tempSrcDir = new File(".temp_src_" + UUID.randomUUID().toString());
        tempSrcDir.mkdirs();
        
        long size = src.length();
        int chunks = (int) Math.ceil((double) size / CHUNK_SIZE);
        System.out.println("Chunks: " + chunks);
        
        try (FileChannel srcChannel = new FileInputStream(src).getChannel();
             java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < chunks; i++) {
                final int idx = i;
                executor.submit(() -> {
                    long pos = (long) idx * CHUNK_SIZE;
                    long len = Math.min(CHUNK_SIZE, size - pos);
                    File chunkFile = new File(tempSrcDir, "chunk_" + idx + ".jtra");
                    try (FileChannel destChannel = new FileOutputStream(chunkFile).getChannel()) {
                        long transferred = srcChannel.transferTo(pos, len, destChannel);
                        System.out.println("Chunk " + idx + " transferred " + transferred + " bytes");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
        
        System.out.println("Files in temp directory:");
        for (File f : tempSrcDir.listFiles()) {
            System.out.println(f.getName() + " - " + f.length());
        }
        
        // cleanup
        for(File f: tempSrcDir.listFiles()) f.delete();
        tempSrcDir.delete();
        src.delete();
    }
}
