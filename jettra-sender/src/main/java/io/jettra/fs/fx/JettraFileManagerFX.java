package io.jettra.fs.fx;

import io.jettra.fs.receptor.JettraFileSystemReceptor;
import io.jettra.fs.sender.JettraMain;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public class JettraFileManagerFX extends Application {

    private TreeView<FileNode> treeLeft;
    private TreeView<FileNode> treeRight;
    private ListView<String> deviceList;
    private Label statusLabel;
    private boolean showHidden = false;
    private String clipboardPath = "";
    private boolean isCut = false;
    private String currentBaseDir = "";

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // Top Header
        VBox topBox = new VBox(10);
        topBox.setPadding(new Insets(20));
        Label title = new Label("Jettra Explorer 3D");
        title.getStyleClass().add("header-label");
        
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button btnRefresh = new Button("🔄 Actualizar");
        btnRefresh.setOnAction(e -> refreshAll());
        
        ToggleButton btnHidden = new ToggleButton("👁️ Mostrar Ocultos");
        btnHidden.setOnAction(e -> {
            showHidden = btnHidden.isSelected();
            btnHidden.setText(showHidden ? "🙈 Ocultar Ocultos" : "👁️ Mostrar Ocultos");
            refreshAll();
        });
        
        toolbar.getChildren().addAll(btnRefresh, btnHidden);
        topBox.getChildren().addAll(title, toolbar);
        root.setTop(topBox);

        // Sidebar (Devices)
        VBox sidebar = new VBox(10);
        sidebar.setPrefWidth(250);
        sidebar.getStyleClass().add("sidebar");
        Label devHeader = new Label("DISPOSITIVOS");
        devHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        
        deviceList = new ListView<>();
        deviceList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadDevice(newVal);
            }
        });
        
        sidebar.getChildren().addAll(devHeader, deviceList);
        root.setLeft(sidebar);

        // Center (Dual Pane)
        SplitPane splitPane = new SplitPane();
        
        VBox leftPane = createFilePane("Origen", true);
        VBox rightPane = createFilePane("Destino", false);
        
        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.5);
        root.setCenter(splitPane);

        // Bottom (Status Bar)
        HBox statusBar = new HBox(10);
        statusBar.getStyleClass().add("status-bar");
        statusLabel = new Label("Listo");
        statusBar.getChildren().add(statusLabel);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1200, 800);
        String css = Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm();
        scene.getStylesheets().add(css);

        primaryStage.setTitle("Jettra File Manager FX");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initial Load
        loadDevices();
        refreshAll();
    }

    private VBox createFilePane(String title, boolean isLeft) {
        VBox pane = new VBox(5);
        pane.setPadding(new Insets(10));
        Label header = new Label(title);
        header.getStyleClass().add("pane-header");
        header.setMaxWidth(Double.MAX_VALUE);
        
        TreeView<FileNode> tree = new TreeView<>();
        tree.setShowRoot(false);
        if (isLeft) treeLeft = tree; else treeRight = tree;
        
        tree.setCellFactory(new Callback<>() {
            @Override
            public TreeCell<FileNode> call(TreeView<FileNode> param) {
                return new FileTreeCell();
            }
        });

        VBox.setVgrow(tree, Priority.ALWAYS);
        pane.getChildren().addAll(header, tree);
        return pane;
    }

    private void loadDevices() {
        deviceList.getItems().clear();
        deviceList.getItems().add("📂 Raíz (/)");
        deviceList.getItems().add("🏠 Home (" + System.getProperty("user.name") + ")");
        deviceList.getItems().add("💾 Simulated Drive");
        
        // Detectar unidades reales en /media/ o /run/media/
        String username = System.getProperty("user.name");
        String[] mediaPaths = {"/media/" + username, "/run/media/" + username};
        for (String basePath : mediaPaths) {
            File mediaDir = new File(basePath);
            if (mediaDir.exists() && mediaDir.isDirectory()) {
                File[] drives = mediaDir.listFiles();
                if (drives != null) {
                    for (File drive : drives) {
                        if (drive.isDirectory()) {
                            deviceList.getItems().add("🔌 " + drive.getName());
                        }
                    }
                }
            }
        }
    }

    private void loadDevice(String device) {
        statusLabel.setText("Cambiando a: " + device);
        String path = "";
        if (device.contains("Raíz")) path = "/";
        else if (device.contains("Home")) path = System.getProperty("user.home");
        else if (device.contains("Simulated")) path = "/home/avbravo/NetBeansProjects/jettrastack_local/JettraWorkspace/JettraFileManager/simulated_drive";
        else if (device.startsWith("🔌 ")) {
             String username = System.getProperty("user.name");
             String driveName = device.substring(3);
             path = new File("/media/" + username, driveName).exists() ? "/media/" + username + "/" + driveName : "/run/media/" + username + "/" + driveName;
        }

        if (!path.isEmpty()) {
            currentBaseDir = path;
            refreshAll();
        }
    }

    private void refreshAll() {
        if (currentBaseDir.isEmpty()) {
            currentBaseDir = "/home/avbravo/NetBeansProjects/jettrastack_local/JettraWorkspace/JettraFileManager/simulated_drive";
        }
        
        JettraFileSystemReceptor receptor = new JettraFileSystemReceptor(currentBaseDir);
        
        populateTreeLazy(treeLeft, receptor);
        populateTreeLazy(treeRight, receptor);
        statusLabel.setText("Explorador en: " + currentBaseDir);
    }

    private void populateTreeLazy(TreeView<FileNode> tree, JettraFileSystemReceptor receptor) {
        TreeItem<FileNode> rootItem = new TreeItem<>(new FileNode("Root", "", true));
        
        Map<String, Object> children = receptor.listPath("", showHidden);
        for (Map.Entry<String, Object> entry : children.entrySet()) {
            rootItem.getChildren().add(createLazyTreeItem(entry.getKey(), entry.getKey(), entry.getValue() instanceof Map, receptor));
        }
        
        tree.setRoot(rootItem);
    }

    private TreeItem<FileNode> createLazyTreeItem(String name, String path, boolean isDir, JettraFileSystemReceptor receptor) {
        TreeItem<FileNode> item = new TreeItem<>(new FileNode(name, path, isDir));
        if (isDir) {
            // Add a dummy child to make it expandable
            item.getChildren().add(new TreeItem<>(new FileNode("Loading...", "", false)));
            
            item.expandedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal && item.getChildren().size() == 1 && item.getChildren().get(0).getValue().name.equals("Loading...")) {
                    item.getChildren().clear();
                    Map<String, Object> children = receptor.listPath(path, showHidden);
                    for (Map.Entry<String, Object> entry : children.entrySet()) {
                        String childPath = path.isEmpty() ? entry.getKey() : path + "/" + entry.getKey();
                        item.getChildren().add(createLazyTreeItem(entry.getKey(), childPath, entry.getValue() instanceof Map, receptor));
                    }
                }
            });
        }
        return item;
    }

    // Inner classes for Tree view
    static class FileNode {
        String name;
        String path;
        boolean isDirectory;

        FileNode(String name, String path, boolean isDirectory) {
            this.name = name;
            this.path = path;
            this.isDirectory = isDirectory;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    class FileTreeCell extends TreeCell<FileNode> {
        @Override
        protected void updateItem(FileNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
            } else {
                String icon = item.isDirectory ? "📂" : getFileIcon(item.name);
                setText(icon + " " + item.name);
                
                ContextMenu menu = new ContextMenu();
                MenuItem copy = new MenuItem("Copiar");
                copy.setOnAction(e -> {
                    clipboardPath = item.path;
                    isCut = false;
                    statusLabel.setText("Copiado: " + item.path);
                });
                
                MenuItem paste = new MenuItem("Pegar aquí");
                paste.setDisable(clipboardPath.isEmpty());
                paste.setOnAction(e -> {
                    doPaste(item.path);
                    refreshAll();
                });
                
                MenuItem rename = new MenuItem("Renombrar");
                rename.setOnAction(e -> {
                    JettraFileSystemReceptor r = new JettraFileSystemReceptor(currentBaseDir);
                    r.renamePath(item.path, item.name + "_new");
                    refreshAll();
                });
                
                MenuItem delete = new MenuItem("Eliminar");
                delete.setOnAction(e -> {
                    JettraFileSystemReceptor r = new JettraFileSystemReceptor(currentBaseDir);
                    r.deletePath(item.path);
                    refreshAll();
                });
                
                menu.getItems().addAll(copy, paste, new SeparatorMenuItem(), rename, delete);
                setContextMenu(menu);
            }
        }

        private String getFileIcon(String filename) {
            String ext = "";
            int i = filename.lastIndexOf('.');
            if (i > 0) ext = filename.substring(i + 1).toLowerCase();
            switch (ext) {
                case "txt": case "md": return "📝";
                case "jpg": case "png": case "gif": return "🖼️";
                case "mp3": case "wav": return "🎵";
                case "mp4": case "avi": return "🎥";
                case "pdf": return "📕";
                case "zip": case "rar": return "📦";
                case "java": case "jar": return "☕";
                default: return "📄";
            }
        }
    }

    private void doPaste(String targetPath) {
        JettraFileSystemReceptor receptor = new JettraFileSystemReceptor(currentBaseDir);
        File source = new File(clipboardPath);
        File target = new File(targetPath);
        
        String destFolder = target.isDirectory() ? target.getAbsolutePath() : target.getParent();
        String destPath = target.isDirectory() ? targetPath + "/" + source.getName() : new File(target.getParent(), source.getName()).getPath();
        
        receptor.copyPath(clipboardPath, destPath);
        if (isCut) {
            receptor.deletePath(clipboardPath);
            clipboardPath = "";
        }
        statusLabel.setText("Operación completada");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
