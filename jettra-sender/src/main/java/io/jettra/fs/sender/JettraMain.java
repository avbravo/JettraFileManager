package io.jettra.fs.sender;

import io.jettra.fs.receptor.JettraFileSystemReceptor;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

/**
 * Clase principal que gestiona el ecosistema JettraFileSystem.
 */
public class JettraMain {
    private static JettraFileSystemReceptor currentReceptor;

    public static int assignedPort = 8080;
    private static String targetDrivePath = "/home/avbravo/NetBeansProjects/jettrastack_local/JettraWorkspace/JettraFileManager/simulated_drive";
    private static com.jettra.server.JettraServer jettraServer;

    public static JettraFileSystemReceptor getCurrentReceptor() {
        return currentReceptor;
    }

    public static void main(String[] args) throws Exception {
        // Simulación de detección de unidad en directorio local accesible
        String targetDrive = "/home/avbravo/NetBeansProjects/jettrastack_local/JettraWorkspace/JettraFileManager/simulated_drive";
        java.io.File simDir = new java.io.File(targetDrive);
        if (!simDir.exists()) simDir.mkdirs();
        
        System.out.println("Detectando unidad de destino: " + targetDrive);
        
        // Dynamic Port Discovery
        assignedPort = findAvailablePort(8080);
        System.out.println("--- PUERTO ASIGNADO AL RECEPTOR/SENDER: " + assignedPort + " ---");
        
        boolean startShell = false;
        boolean startFX = false;
        // Override from command line
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                assignedPort = Integer.parseInt(args[i + 1]);
            }
            if ("--shell".equals(args[i])) {
                startShell = true;
            }
            if ("--fx".equals(args[i])) {
                startFX = true;
            }
        }

        // 1. No arrancar automáticamente el receptor si vamos a usar FX
        if (!startFX) {
            startReceptorOnDrive(targetDrive, assignedPort);
        }
        targetDrivePath = targetDrive;

        // 2. Iniciar el Emisor JettraFileManager orientado al receptor
        JettraFileSystem sender = new JettraFileSystem("localhost", assignedPort);

        // 3. Simular envío de un archivo grande
        File largeFile = new File("/home/avbravo/Videos/peli_grande.mkv");
        if (largeFile.exists()) {
            sender.sendFile(largeFile);
        } else {
            System.out.println("Archivo de prueba no encontrado, simulando entorno...");
        }

        System.out.println("Interfaz 3D JettraWUI activada. Visualizando recipientes y objetos...");
        
        if (startFX) {
            System.out.println("Iniciando Interfaz JavaFX...");
            new Thread(() -> {
                io.jettra.fs.fx.JettraFileManagerFX.main(args);
            }).start();
        }
        
        if (startShell) {
            io.jettra.fs.shell.JettraShell shell = new io.jettra.fs.shell.JettraShell(targetDrive);
            shell.start();
        }
    }

    private static int findAvailablePort(int defaultPort) {
        try (ServerSocket socket = new ServerSocket(defaultPort)) {
            return defaultPort;
        } catch (IOException e) {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException ex) {
                return defaultPort + (int)(Math.random() * 1000); // Fallback
            }
        }
    }

    public static void stopReceptor() {
        if (jettraServer != null) {
            System.out.println("Deteniendo Receptor JettraServer...");
            jettraServer.stop();
            jettraServer = null;
        }
    }

    public static void startReceptor() throws IOException {
        if (jettraServer == null) {
            startReceptorOnDrive(targetDrivePath, assignedPort);
        }
    }

    public static void startReceptorOnDrive(String drivePath, int port) throws IOException {
        System.out.println("Instalando JettraFileSystemReceptor en: " + drivePath);
        System.out.println("Arrancando Receptor JettraServer en puerto: " + port);

        jettraServer = new com.jettra.server.JettraServer();
        System.setProperty("jettra.server.port", String.valueOf(port));
        
        // Registrar Hook de Apagado para liberar el puerto
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (jettraServer != null) {
                System.out.println("Cerrando servidor Jettra y liberando puerto " + port + "...");
                jettraServer.stop();
            }
        }));
        
        currentReceptor = new JettraFileSystemReceptor(drivePath);
        jettraServer.addHandler("/" + currentReceptor.getServiceName(), io.jettra.grpc.JettraGRPCServer.createHandler(currentReceptor));
        
        // Registrar la Interfaz Web 3D Futurista
        jettraServer.addHandler("/", new io.jettra.fs.wui.FileManagerPage());
        
        // Registrar Endpoint para Operaciones de Archivos (Copiar, Pegar, Mover, Renombrar, Eliminar)
        jettraServer.addHandler("/api/fs", new io.jettra.fs.sender.FileOperationsRest());

        new Thread(() -> {
            try {
                jettraServer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
