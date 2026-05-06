package io.jettra.fs.fx;

import io.jettra.fs.chunks.ChunkManager;
import io.jettra.fs.grpc.JettraChunk;
import io.jettra.fs.grpc.TransferStatus;
import io.jettra.fs.receptor.JettraFileSystemReceptor;
import io.jettra.fs.sender.JettraMain;
import io.jettra.grpc.JettraObserver;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class JettraFileManagerFX extends Application {

    private TreeView<FileNode> treeLeft;
    private TreeView<FileNode> treeRight;
    private ListView<String> deviceList;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button btnCancel;
    private Button btnPaste;
    private boolean showHidden = false;
    private String clipboardPath = "";
    private boolean isCut = false;
    private TreeItem<FileNode> lastActiveTargetItem = null;
    private Task<Void> currentTransferTask;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // Top Header
        VBox topBox = new VBox(10);
        topBox.setPadding(new Insets(20));
        Label title = new Label("Jettra Explorer 3D - Native Management");
        title.getStyleClass().add("header-label");
        
        HBox toolbar = new HBox(15);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button btnRefresh = new Button("🔄 Actualizar");
        btnRefresh.setOnAction(e -> refreshAll());
        
        btnPaste = new Button("📋 Pegar");
        btnPaste.setDisable(true);
        btnPaste.getStyleClass().add("btn-paste");
        btnPaste.setOnAction(e -> {
            if (!clipboardPath.isEmpty() && lastActiveTargetItem != null) {
                startTransfer(clipboardPath, lastActiveTargetItem);
            } else {
                statusLabel.setText("⚠️ Selecciona una carpeta de destino primero.");
            }
        });
        
        ToggleButton btnHidden = new ToggleButton("👁️ Ocultos");
        btnHidden.setOnAction(e -> { showHidden = btnHidden.isSelected(); refreshAll(); });
        
        toolbar.getChildren().addAll(btnRefresh, btnPaste, btnHidden);
        topBox.getChildren().addAll(title, toolbar);
        root.setTop(topBox);

        // Sidebar
        VBox sidebar = new VBox(10);
        sidebar.setPrefWidth(240);
        sidebar.getStyleClass().add("sidebar");
        Label devHeader = new Label("ACCESO RÁPIDO");
        devHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #00ffff;");
        
        deviceList = new ListView<>();
        sidebar.getChildren().addAll(devHeader, deviceList);
        root.setLeft(sidebar);

        // Center
        SplitPane splitPane = new SplitPane();
        VBox leftPane = createFilePane("Origen", true);
        VBox rightPane = createFilePane("Destino", false);
        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.5);
        root.setCenter(splitPane);

        // Bottom
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        
        statusLabel = new Label("Listo | Puerto: " + JettraMain.assignedPort);
        statusLabel.setPrefWidth(350);
        
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.setVisible(false);
        
        btnCancel = new Button("🛑 Cancelar");
        btnCancel.setVisible(false);
        btnCancel.setOnAction(e -> { if (currentTransferTask != null) currentTransferTask.cancel(); });
        
        statusBar.getChildren().addAll(statusLabel, progressBar, btnCancel);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1300, 850);
        String css = Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm();
        scene.getStylesheets().add(css);

        primaryStage.setTitle("Jettra File Manager FX - Port " + JettraMain.assignedPort);
        primaryStage.setOnCloseRequest(e -> { Platform.exit(); System.exit(0); });
        primaryStage.setScene(scene);
        primaryStage.show();

        loadDevices();
        refreshAll();
    }

    private VBox createFilePane(String title, boolean isLeft) {
        VBox pane = new VBox(5);
        pane.setPadding(new Insets(10));
        Label header = new Label(title);
        header.getStyleClass().add("pane-header");
        
        TreeView<FileNode> tree = new TreeView<>();
        tree.setShowRoot(false);
        if (isLeft) treeLeft = tree; else treeRight = tree;
        
        tree.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null) {
                lastActiveTargetItem = nv;
                if (!clipboardPath.isEmpty()) btnPaste.setDisable(false);
            }
        });

        tree.setCellFactory(new Callback<>() {
            @Override
            public TreeCell<FileNode> call(TreeView<FileNode> param) { return new FileTreeCell(); }
        });

        VBox.setVgrow(tree, Priority.ALWAYS);
        pane.getChildren().addAll(header, tree);
        return pane;
    }

    private void loadDevices() {
        deviceList.getItems().clear();
        deviceList.getItems().add("📂 Raíz (/)");
        deviceList.getItems().add("🏠 Home");
        deviceList.getItems().add("🖧 NAS-01");
    }

    private void refreshAll() {
        populateMultiUnitTree(treeLeft);
        populateMultiUnitTree(treeRight);
    }

    private void populateMultiUnitTree(TreeView<FileNode> tree) {
        TreeItem<FileNode> rootItem = new TreeItem<>(new FileNode("Computer", "", true));
        rootItem.getChildren().add(createUnitNode("Raíz (/)", "/", "📂"));
        rootItem.getChildren().add(createUnitNode("Home", System.getProperty("user.home"), "🏠"));
        
        File media = new File("/media/" + System.getProperty("user.name"));
        if (media.exists()) {
            File[] drives = media.listFiles();
            if (drives != null) for (File d : drives) rootItem.getChildren().add(createUnitNode("USB: " + d.getName(), d.getAbsolutePath(), "🔌"));
        }
        rootItem.getChildren().add(createUnitNode("NAS-Remote", "/home/avbravo/NAS_Sim", "🖧"));
        tree.setRoot(rootItem);
    }

    private TreeItem<FileNode> createUnitNode(String label, String absPath, String icon) {
        FileNode node = new FileNode(label, absPath, true);
        node.setUnitIcon(icon);
        TreeItem<FileNode> item = new TreeItem<>(node);
        item.getChildren().add(new TreeItem<>(new FileNode("Loading...", "", false)));
        item.expandedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && item.getChildren().size() == 1 && item.getChildren().get(0).getValue().name.equals("Loading...")) {
                loadLevel(item, "", new JettraFileSystemReceptor(absPath));
            }
        });
        return item;
    }

    private void loadLevel(TreeItem<FileNode> parent, String rel, JettraFileSystemReceptor receptor) {
        parent.getChildren().clear();
        Map<String, Object> children = receptor.listPath(rel, showHidden);
        for (Map.Entry<String, Object> entry : children.entrySet()) {
            String name = entry.getKey();
            String childRel = rel.isEmpty() ? name : rel + "/" + name;
            boolean isDir = entry.getValue() instanceof Map;
            TreeItem<FileNode> childItem = new TreeItem<>(new FileNode(name, childRel, isDir));
            if (isDir) {
                childItem.getChildren().add(new TreeItem<>(new FileNode("Loading...", "", false)));
                childItem.expandedProperty().addListener((o, ov, nv) -> {
                    if (nv && childItem.getChildren().size() == 1 && childItem.getChildren().get(0).getValue().name.equals("Loading...")) {
                        loadLevel(childItem, childRel, receptor);
                    }
                });
            }
            parent.getChildren().add(childItem);
        }
    }

    static class FileNode {
        String name; String path; boolean isDirectory; String unitIcon = null;
        FileNode(String n, String p, boolean d) { this.name = n; this.path = p; this.isDirectory = d; }
        void setUnitIcon(String i) { this.unitIcon = i; }
    }

    class FileTreeCell extends TreeCell<FileNode> {
        @Override
        protected void updateItem(FileNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null); setGraphic(null); setContextMenu(null);
            } else {
                String icon = item.unitIcon != null ? item.unitIcon : (item.isDirectory ? "📂" : getFileIcon(item.name));
                setText(icon + " " + item.name);
                
                ContextMenu menu = new ContextMenu();
                MenuItem copy = new MenuItem("Copiar");
                copy.setOnAction(e -> { clipboardPath = getAbs(item, getTreeItem()); isCut = false; btnPaste.setDisable(false); statusLabel.setText("Copiado: " + item.name); });
                
                MenuItem cut = new MenuItem("Cortar");
                cut.setOnAction(e -> { clipboardPath = getAbs(item, getTreeItem()); isCut = true; btnPaste.setDisable(false); statusLabel.setText("Cortado: " + item.name); });

                MenuItem paste = new MenuItem("Pegar aquí");
                paste.setDisable(clipboardPath.isEmpty());
                paste.setOnAction(e -> startTransfer(clipboardPath, getTreeItem()));

                menu.getItems().addAll(copy, cut, paste, new SeparatorMenuItem(), new MenuItem("Eliminar"));
                setContextMenu(menu);
            }
        }
        private String getFileIcon(String n) { return n.endsWith(".java") ? "☕" : "📄"; }
    }

    private String getAbs(FileNode node, TreeItem<FileNode> item) {
        TreeItem<FileNode> curr = item;
        while (curr.getParent() != null && curr.getParent().getParent() != null) curr = curr.getParent();
        String unitBase = curr.getValue().path;
        return (item == curr) ? unitBase : new File(unitBase, node.path).getAbsolutePath();
    }

    private void startTransfer(String srcAbs, TreeItem<FileNode> targetItem) {
        File src = new File(srcAbs);
        String targetAbs = getAbs(targetItem.getValue(), targetItem);
        File targetFile = new File(targetAbs);
        File destDir = targetFile.isDirectory() ? targetFile : targetFile.getParentFile();
        
        // El targetItem real para el refresh es el directorio
        TreeItem<FileNode> refreshItem = targetFile.isDirectory() ? targetItem : targetItem.getParent();

        currentTransferTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Transfiriendo " + src.getName());
                long fileSize = src.length();
                int totalChunks = ChunkManager.calculateTotalChunks(fileSize);
                String fileId = UUID.randomUUID().toString();
                JettraFileSystemReceptor receptor = new JettraFileSystemReceptor(destDir.getAbsolutePath());
                
                try (RandomAccessFile raf = new RandomAccessFile(src, "r")) {
                    for (int i = 0; i < totalChunks; i++) {
                        if (isCancelled()) break;
                        long pos = (long) i * ChunkManager.CHUNK_SIZE;
                        int len = (int) Math.min(ChunkManager.CHUNK_SIZE, fileSize - pos);
                        byte[] buffer = new byte[len];
                        raf.seek(pos); raf.readFully(buffer);
                        JettraChunk chunk = JettraChunk.newBuilder().setFileId(fileId).setFileName(src.getName()).setChunkIndex(i).setTotalChunks(totalChunks).setFileSize(fileSize).setData(buffer).build();
                        receptor.sendChunk(chunk, new JettraObserver<>() {
                            @Override public void onNext(TransferStatus v) {}
                            @Override public void onError(Throwable t) {}
                            @Override public void onCompleted() {}
                        });
                        updateProgress(i + 1, totalChunks);
                    }
                }
                if (!isCancelled() && isCut) src.delete();
                return null;
            }
        };

        progressBar.progressProperty().bind(currentTransferTask.progressProperty());
        currentTransferTask.setOnRunning(e -> { progressBar.setVisible(true); btnCancel.setVisible(true); });
        currentTransferTask.setOnSucceeded(e -> { 
            finalizeTransfer("Éxito"); 
            refreshSpecificNode(refreshItem);
            if (isCut) { clipboardPath = ""; btnPaste.setDisable(true); }
        });
        currentTransferTask.setOnCancelled(e -> finalizeTransfer("Cancelado"));
        currentTransferTask.setOnFailed(e -> finalizeTransfer("Error"));
        new Thread(currentTransferTask).start();
    }

    private void refreshSpecificNode(TreeItem<FileNode> item) {
        if (item == null) return;
        // Obtenemos la base de la unidad
        TreeItem<FileNode> curr = item;
        while (curr.getParent() != null && curr.getParent().getParent() != null) curr = curr.getParent();
        String unitBase = curr.getValue().path;
        JettraFileSystemReceptor receptor = new JettraFileSystemReceptor(unitBase);
        
        Platform.runLater(() -> {
            loadLevel(item, item.getValue().path, receptor);
            item.setExpanded(true);
        });
    }

    private void finalizeTransfer(String msg) {
        progressBar.setVisible(false); btnCancel.setVisible(false);
        statusLabel.textProperty().unbind();
        statusLabel.setText(msg + " | Puerto: " + JettraMain.assignedPort);
    }

    public static void main(String[] args) { launch(args); }
}
