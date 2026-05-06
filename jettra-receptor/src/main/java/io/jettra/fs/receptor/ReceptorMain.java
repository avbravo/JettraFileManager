package io.jettra.fs.receptor;

import com.jettra.server.JettraServer;
import io.jettra.grpc.JettraGRPCServer;
import java.io.IOException;

/**
 * Main entry point for JettraFileSystem Receptor using JettraServer.
 */
public class ReceptorMain {
    public static void main(String[] args) throws IOException {
        System.out.println("Starting JettraFileSystem Receptor on JettraServer...");
        
        // 1. Load Configuration
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream is = new java.io.FileInputStream("jettrafilemanagercongif.properties")) {
            props.load(is);
        } catch (IOException e) {
            System.out.println("Warning: jettrafilemanagercongif.properties not found, using defaults.");
        }

        int port = Integer.parseInt(props.getProperty("jettra.server.port", "8080"));
        
        // Override from command line
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
            }
        }

        System.out.println("Configured port: " + port);

        // 2. Initialize JettraServer
        JettraServer server = new JettraServer();
        // server.setPort(port); // Assuming setPort exists or using system property if JettraServer reads it
        System.setProperty("jettra.server.port", String.valueOf(port));
        
        // 3. Register JettraFileSystemReceptor service using the JettraGRPC bridge
        String storagePath = System.getProperty("user.home") + "/JettraStorage";
        JettraFileSystemReceptor receptor = new JettraFileSystemReceptor(storagePath);
        
        server.addHandler("/" + receptor.getServiceName(), JettraGRPCServer.createHandler(receptor));
        
        // 4. Start the server
        server.start();
        
        System.out.println("Receptor is listening for optimized gRPC transfers.");
    }
}
