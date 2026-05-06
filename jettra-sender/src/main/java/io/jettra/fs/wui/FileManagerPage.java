package io.jettra.fs.wui;

import io.jettra.wui.complex.Center;
import io.jettra.wui.complex.Dashboard;
import io.jettra.wui.complex.Left;
import io.jettra.wui.complex.Top;
import io.jettra.wui.core.JettraDashboardPage;
import io.jettra.wui.components.Div;
import io.jettra.wui.components.Header;
import io.jettra.wui.components.FolderSelector;
import io.jettra.wui.complex.Tree;
import java.util.Map;

/**
 * Interface web 3D futurista para la administración de archivos y directorios en JettraFileManager.
 */
public class FileManagerPage extends JettraDashboardPage {

    public FileManagerPage() {
        super("Jettra 3D File Manager");
    }

    private Tree fileTreeLeft;
    private Tree fileTreeRight;
    // private io.jettra.wui.complex.ContextMenu contextMenu;
    private boolean showHiddenFiles = false;
    
    // Estado para operaciones de archivos
    private String selectedPath = "";
    private String clipboardPath = "";
    private boolean isCutOperation = false;

    @Override
    protected void onInit(Map<String, String> params) {
        // Bypass auth for simulation
        initLayout("Admin Jettra", params);
        
        // Manejar cambio de unidad via parámetro
        String driveParam = params.get("drive");
        if (driveParam != null && !driveParam.isEmpty()) {
             io.jettra.fs.receptor.JettraFileSystemReceptor receptor = io.jettra.fs.sender.JettraMain.getCurrentReceptor();
             if (receptor != null) {
                 // En una implementación real, podríamos reconfigurar el receptor o solo la vista
                 // Para esta interfaz, simulamos que la vista cambia a la unidad seleccionada
                 System.out.println("Cambiando vista a unidad: " + driveParam);
             }
        }
        
        refreshFileTree();
    }

    private void refreshFileTree() {
        if (fileTreeLeft != null) fileTreeLeft.clearTree();
        if (fileTreeRight != null) fileTreeRight.clearTree();

        java.util.Map<String, Object> allDevices = new java.util.LinkedHashMap<>();
        
        // Dispositivo actual (Simulado o seleccionado)
        io.jettra.fs.receptor.JettraFileSystemReceptor receptor = io.jettra.fs.sender.JettraMain.getCurrentReceptor();
        if (receptor != null) {
            String dirName = new java.io.File(receptor.getBaseDir()).getName();
            if (!dirName.contains("simulated_drive")) {
                allDevices.put("💾 " + dirName + " (Actual)", receptor.listFiles(showHiddenFiles, 20));
            }
        }

        // Agregar disco local (user.home)
        java.io.File homeDir = new java.io.File(System.getProperty("user.home"));
        if (homeDir.exists() && homeDir.isDirectory()) {
             io.jettra.fs.receptor.JettraFileSystemReceptor tmpHome = new io.jettra.fs.receptor.JettraFileSystemReceptor(homeDir.getAbsolutePath());
             allDevices.put("💾 Disco Local (" + homeDir.getName() + ")", tmpHome.listFiles(showHiddenFiles, 8));
        }

        // Otros dispositivos físicos en /media/ o /run/media/
        String username = System.getProperty("user.name");
        String[] mediaPaths = {"/media/" + username, "/run/media/" + username};
        
        for (String basePath : mediaPaths) {
            java.io.File mediaDir = new java.io.File(basePath);
            if (mediaDir.exists() && mediaDir.isDirectory()) {
                java.io.File[] drives = mediaDir.listFiles();
                if (drives != null) {
                    for (java.io.File drive : drives) {
                        if (drive.isDirectory() && (receptor == null || !drive.getAbsolutePath().equals(receptor.getBaseDir()))) {
                            io.jettra.fs.receptor.JettraFileSystemReceptor tmp = new io.jettra.fs.receptor.JettraFileSystemReceptor(drive.getAbsolutePath());
                            allDevices.put("💾 " + drive.getName(), tmp.listFiles(showHiddenFiles, 20));
                        }
                    }
                }
            }
        }
        
        // Simular red
        allDevices.put("🖧 Jettra-NAS-01", new java.util.HashMap<>());

        if (fileTreeLeft != null) populateTree(fileTreeLeft, allDevices);
        if (fileTreeRight != null) populateTree(fileTreeRight, allDevices);
    }

    private void populateTree(io.jettra.wui.core.UIComponent parent, java.util.Map<String, Object> files) {
        populateTree(parent, files, "");
    }

    private void populateTree(io.jettra.wui.core.UIComponent parent, java.util.Map<String, Object> files, String parentPath) {
        for (Map.Entry<String, Object> entry : files.entrySet()) {
            String key = entry.getKey();
            String currentPath = parentPath;
            boolean isRoot = false;
            
            if (key.startsWith("💾 ") || key.startsWith("🖧 ")) {
                currentPath = key.substring(3).split(" \\(Actual\\)")[0];
                isRoot = true;
            } else {
                currentPath = parentPath.endsWith("/") ? parentPath + key : parentPath + "/" + key;
            }

            String displayLabel = key;
            if (!isRoot) {
                if (entry.getValue() instanceof Map) {
                    displayLabel = "📂 " + key;
                } else {
                    displayLabel = getFileIcon(key) + " " + key;
                }
            }

            Tree.TreeItem item = new Tree.TreeItem(displayLabel, currentPath);
            final String pathForEvent = currentPath;
            
/*
            item.addContextMenuListener(() -> {
                selectedPath = pathForEvent;
                updateContextMenu();
            });
*/

            parent.add(item);
            if (entry.getValue() instanceof Map) {
                populateTree(item, (Map<String, Object>) entry.getValue(), currentPath);
            }
        }
    }

    private String getFileIcon(String filename) {
        String ext = "";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            ext = filename.substring(i + 1).toLowerCase();
        }
        
        switch (ext) {
            case "txt": case "md": case "csv": return "📝";
            case "jpg": case "jpeg": case "png": case "gif": case "svg": case "webp": return "🖼️";
            case "mp3": case "wav": case "ogg": return "🎵";
            case "mp4": case "avi": case "mkv": case "mov": return "🎥";
            case "pdf": return "📕";
            case "zip": case "tar": case "gz": case "rar": case "7z": return "📦";
            case "java": case "jar": case "class": return "☕";
            case "html": case "css": case "js": case "json": case "xml": case "properties": return "🌐";
            case "sh": case "bat": case "exe": case "bin": return "⚙️";
            default: return "📄";
        }
    }

    @Override
    protected void initCenter(Center center, String username) {
        Div container = new Div();
        container.setStyle("padding", "30px").setStyle("animation", "jtFadeIn 0.8s ease-out");
        
        Header h1 = new Header(1, "Jettra Explorer 3D");
        h1.setStyle("text-shadow", "0 0 20px var(--jettra-glow)")
          .setStyle("letter-spacing", "2px")
          .setStyle("color", "var(--jettra-accent)");
        container.add(h1);
        
        // El nuevo grid para los dos paneles
        Div dualGrid = new Div();
        dualGrid.setStyle("display", "grid").setStyle("grid-template-columns", "1fr 1fr").setStyle("gap", "25px").setStyle("margin-top", "20px");
        
        // Panel Izquierdo
        Div viewPanelLeft = new Div();
        viewPanelLeft.addClass("j-3d-effect")
                  .setStyle("background", "rgba(0,0,0,0.4)")
                  .setStyle("border", "1px solid rgba(0,255,255,0.2)")
                  .setStyle("padding", "25px")
                  .setStyle("border-radius", "15px")
                  .setStyle("min-height", "450px")
                  .setStyle("overflow-y", "auto");
                  
        Header hViewLeft = new Header(4, "Origen");
        hViewLeft.setStyle("color", "var(--jettra-accent)").setStyle("margin-top", "0").setStyle("border-bottom", "1px solid rgba(0,255,255,0.2)").setStyle("padding-bottom", "10px");
        
        io.jettra.wui.components.Button btnHiddenL = new io.jettra.wui.components.Button(showHiddenFiles ? "🙈 Ocultar Ocultos" : "👁️ Mostrar Ocultos");
        btnHiddenL.setId("btn-hidden-files");
        btnHiddenL.setStyle("background", "transparent").setStyle("color", "var(--jettra-accent)").setStyle("border", "1px solid var(--jettra-accent)").setStyle("padding", "2px 8px").setStyle("border-radius", "4px").setStyle("cursor", "pointer").setStyle("float", "right").setStyle("font-size", "12px");
        btnHiddenL.addClickListener(() -> {
            showHiddenFiles = !showHiddenFiles;
            btnHiddenL.setContent(showHiddenFiles ? "🙈 Ocultar Ocultos" : "👁️ Mostrar Ocultos");
            refreshFileTree();
            btnHiddenL.setUpdate("file-tree-3d-left,file-tree-3d-right,btn-hidden-files");
        });
        hViewLeft.add(btnHiddenL);
        
        fileTreeLeft = new Tree();
        fileTreeLeft.setId("file-tree-3d-left");
        viewPanelLeft.add(hViewLeft).add(fileTreeLeft);

        // Panel Derecho
        Div viewPanelRight = new Div();
        viewPanelRight.addClass("j-3d-effect")
                  .setStyle("background", "rgba(0,0,0,0.4)")
                  .setStyle("border", "1px solid rgba(0,255,255,0.2)")
                  .setStyle("padding", "25px")
                  .setStyle("border-radius", "15px")
                  .setStyle("min-height", "450px")
                  .setStyle("overflow-y", "auto");
                  
        Header hViewRight = new Header(4, "Destino");
        hViewRight.setStyle("color", "var(--jettra-accent)").setStyle("margin-top", "0").setStyle("border-bottom", "1px solid rgba(0,255,255,0.2)").setStyle("padding-bottom", "10px");
        
        fileTreeRight = new Tree();
        fileTreeRight.setId("file-tree-3d-right");
        viewPanelRight.add(hViewRight).add(fileTreeRight);

        dualGrid.add(viewPanelLeft).add(viewPanelRight);
        
        container.add(dualGrid);
        
        // Inicializar el Menú Contextual (Desactivado por falta de componente en JettraWUI)
        // contextMenu = new io.jettra.wui.complex.ContextMenu();
        // container.add(contextMenu);
        
        center.add(container);
        
        // Carga inicial de datos
        refreshFileTree();
    }

/*
    private void updateContextMenu() {
        contextMenu.getChildren().clear();
        ...
    }
*/

    private void doPaste() {
        io.jettra.fs.receptor.JettraFileSystemReceptor receptor = io.jettra.fs.sender.JettraMain.getCurrentReceptor();
        if (receptor == null) return;
        
        java.io.File source = new java.io.File(clipboardPath);
        java.io.File target = new java.io.File(selectedPath);
        
        // Si pegamos sobre un archivo, lo hacemos en su carpeta padre
        String destFolder = target.isDirectory() ? target.getAbsolutePath() : target.getParent();
        String destPath = new java.io.File(destFolder, source.getName()).getAbsolutePath();
        
        receptor.copyPath(clipboardPath, destPath);
        if (isCutOperation) {
            receptor.deletePath(clipboardPath);
            clipboardPath = "";
        }
    }
    
    @Override
    protected void setupLeft(Left left, String username) {
        initMenuBuilder();
        addCategory("DISPOSITIVOS", new String[]{}, "");
        
        // Detectar unidades reales en /media/ o /run/media/
        String currentUsername = System.getProperty("user.name");
        String[] mediaPaths = {"/media/" + currentUsername, "/run/media/" + currentUsername};
        
        for (String basePath : mediaPaths) {
            java.io.File mediaDir = new java.io.File(basePath);
            if (mediaDir.exists() && mediaDir.isDirectory()) {
                java.io.File[] drives = mediaDir.listFiles();
                if (drives != null) {
                    for (java.io.File drive : drives) {
                        if (drive.isDirectory()) {
                            appendMenuItem(drive.getName(), "/?drive=" + drive.getAbsolutePath(), "💾");
                        }
                    }
                }
            }
        }
        
        // Unidades de Red Simuladas
        appendMenuItem("Jettra-NAS-01", "/?drive=nas1", "🖧");
        
        addCategory("EXPLORADOR", new String[]{}, "");
        appendMenuItem("Vista 3D", "/", "🌐");
        appendMenuItem("Unidades Remotas", "/remotedrive", "📡");
        
        addCategory("OPERACIONES", new String[]{}, "");
        appendMenuItem("Fragmentación", "/chunks", "🧩");
        appendMenuItem("Compresión GZIP", "/compression", "🗜️");
        
        addCategory("SISTEMA", new String[]{}, "");
        appendMenuItem("Logs de Servidor", "/logs", "📋");
        appendMenuItem("Configuración", "/config", "⚙️");
        
        finishMenuBuilder(left);
    }
    
    @Override
    protected String getLoggedUser(com.sun.net.httpserver.HttpExchange exchange) {
        // En el entorno local de JettraFileManager permitimos acceso directo
        return "admin"; 
    }
}
