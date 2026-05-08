import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

public class test_lock2 {
    public static void main(String[] args) {
        String baseDir = "/media/avbravo/7804-6CF5";
        try {
            File tempDir = new File(baseDir, ".jettra_receptor_temp");
            tempDir.mkdirs();

            File lockFile = new File(tempDir, ".lock");
            FileChannel lockChannel1 = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock instanceLock1 = lockChannel1.tryLock();

            FileChannel lockChannel2 = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock instanceLock2 = lockChannel2.tryLock();
            
        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
