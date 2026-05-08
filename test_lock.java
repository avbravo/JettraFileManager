import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class test_lock {
    public static void main(String[] args) {
        String baseDir = "/media/avbravo/7804-6CF5";
        try {
            File tempDir = new File(baseDir, ".jettra_receptor_temp");
            if (!tempDir.exists()) {
                if (!tempDir.mkdirs()) {
                    System.out.println("Could not create dirs");
                    return;
                }
            }

            File lockFile = new File(tempDir, ".lock");
            FileChannel lockChannel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock instanceLock = lockChannel.tryLock();

            if (instanceLock == null) {
                System.err.println("Error: Ya hay otra instancia.");
                return;
            }

            Files.walk(tempDir.toPath())
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .filter(f -> !f.getName().equals(".lock") && !f.equals(tempDir))
                 .forEach(File::delete);
            System.out.println("Limpieza completada.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
