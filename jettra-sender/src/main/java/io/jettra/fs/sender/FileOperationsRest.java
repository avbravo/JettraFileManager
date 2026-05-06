package io.jettra.fs.sender;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class FileOperationsRest implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        StringBuilder sb = new StringBuilder();
        try (InputStream ios = exchange.getRequestBody()) {
            int i;
            while ((i = ios.read()) != -1) {
                sb.append((char) i);
            }
        }

        String payload = sb.toString();
        // Simple JSON parsing
        String action = extractJsonField(payload, "action");
        String source = extractJsonField(payload, "source");
        String target = extractJsonField(payload, "target");

        String responseMessage = "";
        try {
            if ("copy".equals(action)) {
                if (Files.isDirectory(Paths.get(source))) {
                    copyFolder(Paths.get(source), Paths.get(target, new java.io.File(source).getName()));
                } else {
                    Files.copy(Paths.get(source), Paths.get(target, new java.io.File(source).getName()), StandardCopyOption.REPLACE_EXISTING);
                }
                responseMessage = "Copiado exitosamente: " + source;
            } else if ("move".equals(action)) {
                Files.move(Paths.get(source), Paths.get(target, new java.io.File(source).getName()), StandardCopyOption.REPLACE_EXISTING);
                responseMessage = "Movido exitosamente: " + source;
            } else if ("rename".equals(action)) {
                Files.move(Paths.get(source), Paths.get(new java.io.File(source).getParent(), target));
                responseMessage = "Renombrado a: " + target;
            } else if ("delete".equals(action)) {
                if (Files.isDirectory(Paths.get(source))) {
                    deleteFolder(Paths.get(source));
                } else {
                    Files.delete(Paths.get(source));
                }
                responseMessage = "Eliminado exitosamente: " + source;
            } else {
                responseMessage = "Acción desconocida: " + action;
            }
        } catch (Exception e) {
            responseMessage = "Error en operación: " + e.getMessage();
        }

        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, responseMessage.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseMessage.getBytes());
        }
    }

    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
    
    private void copyFolder(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteFolder(Path folder) throws IOException {
        Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
