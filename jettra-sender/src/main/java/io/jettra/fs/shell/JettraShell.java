package io.jettra.fs.shell;

import io.jettra.fs.receptor.JettraFileSystemReceptor;
import io.jettra.fs.sender.JettraMain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class JettraShell {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_CLEAR = "\033[H\033[2J";
    private static final String ANSI_BOLD = "\u001B[1m";

    private String leftDir;
    private String rightDir;
    private int activePanel = 0; // 0 = Left, 1 = Right
    private JettraFileSystemReceptor receptor;

    public JettraShell(String initialDir) {
        this.leftDir = initialDir;
        this.rightDir = System.getProperty("user.home");
        this.receptor = JettraMain.getCurrentReceptor();
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            render();
            System.out.print(ANSI_CYAN + "jettra-shell> " + ANSI_RESET);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) break;
            handleCommand(input);
        }
    }

    private void render() {
        System.out.print(ANSI_CLEAR);
        System.out.println(ANSI_CYAN + ANSI_BOLD + "================================================================================");
        System.out.println("  JETTRA FILE MANAGER - INTERACTIVE SHELL (DUAL PANEL)");
        System.out.println("================================================================================" + ANSI_RESET);

        String[] leftFiles = getFileList(leftDir);
        String[] rightFiles = getFileList(rightDir);

        int maxRows = Math.max(leftFiles.length, rightFiles.length);
        
        // Header de paneles
        System.out.printf("%-38s | %-38s\n", 
            (activePanel == 0 ? ANSI_GREEN + "> " : "  ") + leftDir + ANSI_RESET,
            (activePanel == 1 ? ANSI_GREEN + "> " : "  ") + rightDir + ANSI_RESET);
        System.out.println("---------------------------------------+---------------------------------------");

        for (int i = 0; i < maxRows; i++) {
            String left = i < leftFiles.length ? leftFiles[i] : "";
            String right = i < rightFiles.length ? rightFiles[i] : "";
            
            // Truncar si son muy largos
            if (left.length() > 35) left = left.substring(0, 32) + "...";
            if (right.length() > 35) right = right.substring(0, 32) + "...";

            String leftStr = (activePanel == 0) ? ANSI_GREEN + String.format("%-38s", left) + ANSI_RESET : String.format("%-38s", left);
            String rightStr = (activePanel == 1) ? ANSI_GREEN + String.format("%-38s", right) + ANSI_RESET : String.format("%-38s", right);

            System.out.printf("%s | %s\n", leftStr, rightStr);
        }

        System.out.println("---------------------------------------+---------------------------------------");
        System.out.println(ANSI_BOLD + " [tab] Cambiar | [cd] Navegar | [ls] Listar | [cp] Copiar | [mkdir] Carpeta | [?] Ayuda " + ANSI_RESET);
    }

    private String[] getFileList(String path) {
        File f = new File(path);
        if (!f.exists() || !f.isDirectory()) return new String[]{"[Error: Dir no encontrado]"};
        
        File[] files = f.listFiles();
        if (files == null) return new String[]{"[Acceso denegado]"};
        
        List<String> list = new ArrayList<>();
        list.add("..");
        for (File file : files) {
            if (file.isDirectory()) {
                list.add("📂 " + file.getName());
            } else {
                list.add("📄 " + file.getName());
            }
        }
        return list.toArray(new String[0]);
    }

    private void handleCommand(String input) {
        String[] parts = input.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "tab":
            case "switch":
                activePanel = 1 - activePanel;
                break;
            case "cd":
                navigate(arg);
                break;
            case "cp":
            case "copy":
                copyFile(arg);
                break;
            case "rm":
            case "del":
                deleteFile(arg);
                break;
            case "mkdir":
                makeDir(arg);
                break;
            case "ls":
            case "list":
                // Render se ejecuta automáticamente al inicio del bucle
                break;
            case "help":
            case "?":
                showHelp();
                break;
            default:
                if (!cmd.isEmpty()) {
                    System.out.println(ANSI_RED + "Comando desconocido: " + cmd + ". Escribe 'help' para ver la lista." + ANSI_RESET);
                    try { Thread.sleep(1000); } catch (InterruptedException e) {}
                }
        }
    }

    private void showHelp() {
        System.out.print(ANSI_CLEAR);
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== AYUDA DE JETTRA SHELL ===" + ANSI_RESET);
        System.out.println("tab / switch   : Cambia entre el panel izquierdo y derecho.");
        System.out.println("ls / list      : Refresca la lista de archivos.");
        System.out.println("cd <dir>       : Entra en un directorio (ej: cd Documentos).");
        System.out.println("cd ..          : Sube un nivel de directorio.");
        System.out.println("cp <file>      : Copia un archivo del panel activo al otro.");
        System.out.println("mkdir <name>   : Crea una nueva carpeta.");
        System.out.println("rm <file>      : Elimina un archivo o carpeta.");
        System.out.println("help / ?       : Muestra esta pantalla.");
        System.out.println("exit / quit    : Sale de la consola.");
        System.out.println("\nPresiona ENTER para volver...");
        new Scanner(System.in).nextLine();
    }

    private void makeDir(String arg) {
        if (arg.isEmpty()) return;
        String current = activePanel == 0 ? leftDir : rightDir;
        File newDir = new File(current, arg);
        if (newDir.mkdirs()) {
            System.out.println(ANSI_GREEN + "Carpeta creada: " + arg + ANSI_RESET);
        } else {
            System.out.println(ANSI_RED + "Error al crear carpeta." + ANSI_RESET);
        }
        try { Thread.sleep(500); } catch (InterruptedException e) {}
    }

    private void navigate(String arg) {
        String current = activePanel == 0 ? leftDir : rightDir;
        File next;
        if (arg.equals("..")) {
            next = new File(current).getParentFile();
        } else {
            next = new File(current, arg);
        }

        if (next != null && next.exists() && next.isDirectory()) {
            if (activePanel == 0) leftDir = next.getAbsolutePath();
            else rightDir = next.getAbsolutePath();
        }
    }

    private void copyFile(String arg) {
        String sourceBase = activePanel == 0 ? leftDir : rightDir;
        String destBase = activePanel == 0 ? rightDir : leftDir;
        
        File source = new File(sourceBase, arg);
        File dest = new File(destBase, arg);
        
        if (source.exists()) {
            System.out.println(ANSI_GREEN + "Copiando " + arg + "..." + ANSI_RESET);
            receptor.copyPath(source.getAbsolutePath(), dest.getAbsolutePath());
        }
    }

    private void deleteFile(String arg) {
        String current = activePanel == 0 ? leftDir : rightDir;
        receptor.deletePath(new File(current, arg).getAbsolutePath());
    }
}
