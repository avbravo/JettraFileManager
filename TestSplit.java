import java.io.File;
import io.jettra.fs.chunks.ChunkManager;

public class TestSplit {
    public static void main(String[] args) throws Exception {
        File src = new File("test.dat");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(src)) {
            byte[] data = new byte[1024 * 1024 * 5]; // 5MB
            for (int i=0; i<data.length; i++) data[i] = (byte) i;
            fos.write(data);
        }
        File destDir = new File(".temp_chunks");
        System.out.println("Splitting...");
        ChunkManager.splitFile(src, destDir);
        System.out.println("Done splitting.");
        
        File[] chunks = destDir.listFiles();
        if (chunks != null) {
            for (File f : chunks) {
                System.out.println(f.getName() + ": " + f.length());
            }
        }
    }
}
