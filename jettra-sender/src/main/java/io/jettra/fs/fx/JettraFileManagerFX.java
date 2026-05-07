package io.jettra.fs.fx;

import io.jettra.fs.chunks.ChunkManager;
import io.jettra.fs.grpc.JettraChunk;
import io.jettra.fs.grpc.TransferStatus;
import io.jettra.fs.receptor.JettraFileSystemReceptor;
import io.jettra.fs.sender.JettraMain;
import io.jettra.grpc.JettraObserver;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.effect.Glow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public class JettraFileManagerFX extends Application {

    private TreeView<FileNode> treeLeft;
    private TreeView<FileNode> treeRight;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button btnCancel;
    private Button btnPaste;
    private Pane animPane;
    private Timeline chunkTimeline;
    private long startTime;
    private boolean showHidden = false;
    private String clipboardPath = "";
    private boolean isCut = false;
    private TreeItem<FileNode> lastActiveTargetItem = null;
    private Task<Void> currentTransferTask;
    private final List<String> transferErrors = new CopyOnWriteArrayList<>();
    private Label timerLabel;
    private Timeline timerTimeline;
    private TabPane transferTabPane;
    private ComboBox<String> comboProtocol;
    private Label memLabel;
    private ProgressBar memBar;
    private Label vThreadLabel;
    private LineChart<String, Number> vThreadChart;
    private XYChart.Series<String, Number> vThreadSeries;
    private ExecutorService transferExecutor;
    private final TrackedThreadFactory vThreadFactory = new TrackedThreadFactory(Thread.ofVirtual().factory());
    private final ExecutorService virtualExecutor = Executors.newThreadPerTaskExecutor(vThreadFactory);


    @Override
    public void start(Stage primaryStage) {
        JettraMain.checkSingleInstanceAndCleanup();
        initCharts();
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        VBox topBox = new VBox(10);
        topBox.setPadding(new Insets(20));
        Label title = new Label("Jettra Ultra-Explorer - Protocolo X-Stream Parallel (Java 25)");
        title.getStyleClass().add("header-label");
        
        HBox toolbar = new HBox(15);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button btnRefresh = new Button("🔄 Actualizar");
        btnRefresh.setOnAction(e -> refreshAll());
        btnPaste = new Button("📋 Pegar");
        btnPaste.setDisable(true);
        btnPaste.getStyleClass().add("btn-paste");
        btnPaste.setOnAction(e -> { if (!clipboardPath.isEmpty() && lastActiveTargetItem != null) startTransfer(clipboardPath, lastActiveTargetItem); });
        
        Button btnExit = new Button("❌ Cerrar");
        btnExit.getStyleClass().add("btn-exit");
        btnExit.setOnAction(e -> shutdown());
        
        Button btnKill = new Button("💥 Terminar Todo");
        btnKill.getStyleClass().add("btn-kill");
        btnKill.setOnAction(e -> {
            System.err.println("!!! TERMINACIÓN FORZADA SOLICITADA !!!");
            System.exit(0);
        });

        
        comboProtocol = new ComboBox<>();
        comboProtocol.getItems().addAll("JettraStream (Direct)", "X-Stream (gRPC)");
        comboProtocol.setValue("JettraStream (Direct)");
        comboProtocol.setPrefWidth(180);
        
        comboProtocol.valueProperty().addListener((obs, old, newVal) -> {
            virtualExecutor.submit(() -> {
                try {
                    if ("X-Stream (gRPC)".equals(newVal)) {
                        JettraMain.startReceptor();
                        Platform.runLater(() -> statusLabel.setText("Modo gRPC Activado | Puerto: " + JettraMain.assignedPort));
                    } else {
                        JettraMain.stopReceptor();
                        Platform.runLater(() -> statusLabel.setText("Modo Directo Activado (Offline)"));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });
        
        // Inicialización: En modo Directo (predeterminado), el receptor debe estar apagado
        virtualExecutor.submit(() -> { try { JettraMain.stopReceptor(); } catch(Exception ex){} });
        
        toolbar.getChildren().addAll(btnRefresh, btnPaste, btnExit, btnKill, new Label("Protocolo:"), comboProtocol);

        topBox.getChildren().addAll(title, toolbar);
        root.setTop(topBox);

        VBox sidebar = new VBox(10);
        sidebar.setPrefWidth(260);
        sidebar.getStyleClass().add("sidebar");
        sidebar.getChildren().add(new Label("ACCESO RÁPIDO"));
        ListView<HBox> quickAccess = new ListView<>();
        loadQuickAccess(quickAccess);
        sidebar.getChildren().add(quickAccess);
        root.setLeft(sidebar);

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(createFilePane("Origen", true), createFilePane("Destino", false));
        splitPane.setDividerPositions(0.5);
        
        TitledPane statsPane = new TitledPane("Monitor de Hilos Virtuales", vThreadChart);
        statsPane.setExpanded(false);
        statsPane.getStyleClass().add("stats-pane");
        
        VBox centerBox = new VBox(splitPane, statsPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        root.setCenter(centerBox);

        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        statusLabel = new Label("Modo Directo Activado (Offline)");
        statusLabel.setPrefWidth(450);
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        animPane = new Pane();
        animPane.setPrefSize(80, 20);
        animPane.setVisible(false);

        btnCancel = new Button("🛑 Detener");
        btnCancel.setVisible(false);
        btnCancel.setOnAction(e -> { 
            if (currentTransferTask != null) {
                currentTransferTask.cancel(true); 
                statusLabel.textProperty().unbind();
                statusLabel.setText("🛑 Deteniendo transferencia... Limpiando hilos.");
                System.out.println(">>> SOLICITUD DE CANCELACIÓN ENVIADA AL TASK <<<");
            }
        });

        transferTabPane = new TabPane();
        transferTabPane.setPrefHeight(280);
        transferTabPane.setVisible(false);
        transferTabPane.setStyle("-fx-background: transparent;");
        
        timerLabel = new Label("00:00:00.000");
        timerLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 16px; -fx-text-fill: #00ff00; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        memLabel = new Label("MEM: --/--");
        memBar = new ProgressBar(0);
        memBar.setPrefWidth(100);
        memBar.getStyleClass().add("mem-bar");

        vThreadLabel = new Label("V-Threads: 0");
        vThreadLabel.setStyle("-fx-text-fill: #ff00ff; -fx-font-weight: bold;");

        statusBar.getChildren().addAll(statusLabel, progressBar, timerLabel, animPane, btnCancel, spacer, vThreadLabel, memLabel, memBar);
        
        VBox bottomContainer = new VBox(0, transferTabPane, statusBar);
        root.setBottom(bottomContainer);

        Scene scene = new Scene(root, 1300, 850);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm());
        primaryStage.setTitle("Jettra File Manager FX");
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.setScene(scene);
        primaryStage.show();
        startResourceMonitoring();
        refreshAll();
    }

    private void initCharts() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Tiempo");
        yAxis.setLabel("Hilos");
        vThreadChart = new LineChart<>(xAxis, yAxis);
        vThreadChart.setTitle("Hilos Virtuales JVM");
        vThreadChart.setCreateSymbols(false);
        vThreadChart.setAnimated(false);
        vThreadChart.setPrefHeight(200);
        vThreadSeries = new XYChart.Series<>();
        vThreadSeries.setName("Hilos Activos");
        vThreadChart.getData().add(vThreadSeries);
    }

    private void startResourceMonitoring() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            Runtime rt = Runtime.getRuntime();
            long total = rt.totalMemory();
            long free = rt.freeMemory();
            long used = total - free;
            long max = rt.maxMemory();
            double pct = (double) used / max;
            memLabel.setText(String.format("MEM: %d MB", used / (1024 * 1024)));
            memBar.setProgress(pct);
            if (pct > 0.8) memBar.setStyle("-fx-accent: #ff4444;");
            else memBar.setStyle("-fx-accent: #00ffff;");

            int activeVThreads = vThreadFactory.getActiveCount();
            vThreadLabel.setText("V-Threads: " + activeVThreads);
            
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            vThreadSeries.getData().add(new XYChart.Data<>(timestamp, activeVThreads));
            if (vThreadSeries.getData().size() > 30) vThreadSeries.getData().remove(0);
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void loadQuickAccess(ListView<HBox> list) {
        list.getItems().add(createAccessItem("📂 Raíz (/)", "/", "📂"));
        list.getItems().add(createAccessItem("🏠 Home", System.getProperty("user.home"), "🏠"));
        for (File usb : getUSBUnits()) list.getItems().add(createAccessItem("🔌 " + usb.getName(), usb.getAbsolutePath(), "🔌"));
        list.getItems().add(createAccessItem("🖧 NAS-01", "/home/avbravo/NAS_Sim", "🖧"));
    }

    private HBox createAccessItem(String name, String path, String icon) {
        HBox box = new HBox(10); box.setAlignment(Pos.CENTER_LEFT);
        Button btnGo = new Button("➔");
        btnGo.setOnAction(e -> { treeLeft.setRoot(createUnitNode(name, path, icon)); treeRight.setRoot(createUnitNode(name, path, icon)); });
        box.getChildren().addAll(new Label(name), btnGo); return box;
    }

    private List<File> getUSBUnits() {
        List<File> units = new ArrayList<>();
        String[] paths = {"/media/" + System.getProperty("user.name"), "/run/media/" + System.getProperty("user.name"), "/mnt"};
        for (String p : paths) {
            File d = new File(p);
            if (d.exists() && d.isDirectory()) {
                File[] fs = d.listFiles(); if (fs != null) for (File f : fs) if (f.isDirectory()) units.add(f);
            }
        }
        return units;
    }

    private VBox createFilePane(String title, boolean isLeft) {
        VBox pane = new VBox(5); pane.setPadding(new Insets(10));
        pane.getChildren().add(new Label(title));
        TreeView<FileNode> tree = new TreeView<>(); tree.setShowRoot(false);
        if (isLeft) treeLeft = tree; else treeRight = tree;
        tree.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null) { lastActiveTargetItem = nv; if (!clipboardPath.isEmpty()) btnPaste.setDisable(false); }
        });
        tree.setCellFactory(p -> new FileTreeCell());
        VBox.setVgrow(tree, Priority.ALWAYS); pane.getChildren().add(tree);
        return pane;
    }

    private void refreshAll() {
        virtualExecutor.submit(() -> {
            TreeItem<FileNode> rootL = populateRoot();
            TreeItem<FileNode> rootR = populateRoot();
            Platform.runLater(() -> {
                treeLeft.setRoot(rootL);
                treeRight.setRoot(rootR);
            });
        });
    }

    private TreeItem<FileNode> populateRoot() {
        TreeItem<FileNode> root = new TreeItem<>(new FileNode("Computer", "", true));
        root.getChildren().add(createUnitNode("Raíz", "/", "📂"));
        root.getChildren().add(createUnitNode("Home", System.getProperty("user.home"), "🏠"));
        for (File usb : getUSBUnits()) root.getChildren().add(createUnitNode("USB: " + usb.getName(), usb.getAbsolutePath(), "🔌"));
        root.getChildren().add(createUnitNode("NAS-Remote", "/home/avbravo/NAS_Sim", "🖧"));
        return root;
    }

    private TreeItem<FileNode> createUnitNode(String label, String absPath, String icon) {
        FileNode node = new FileNode(label, absPath, true); node.setUnitIcon(icon);
        TreeItem<FileNode> item = new TreeItem<>(node); item.getChildren().add(new TreeItem<>(new FileNode("Loading...", "", false)));
        item.expandedProperty().addListener((obs, old, newVal) -> {
            if (newVal && item.getChildren().size() == 1 && item.getChildren().get(0).getValue().name.equals("Loading...")) {
                loadLevelAsync(item, "", new JettraFileSystemReceptor(absPath));
            }
        });
        return item;
    }

    private void loadLevelAsync(TreeItem<FileNode> parent, String rel, JettraFileSystemReceptor receptor) {
        virtualExecutor.submit(() -> {
            try {
                Map<String, Object> children = receptor.listPath(rel, showHidden);
                List<TreeItem<FileNode>> items = new ArrayList<>();
                for (Map.Entry<String, Object> entry : children.entrySet()) {
                    String name = entry.getKey();
                    String childRel = rel.isEmpty() ? name : rel + "/" + name;
                    boolean isDir = entry.getValue() instanceof Map;
                    TreeItem<FileNode> childItem = new TreeItem<>(new FileNode(name, childRel, isDir));
                    if (isDir) {
                        childItem.getChildren().add(new TreeItem<>(new FileNode("Loading...", "", false)));
                        childItem.expandedProperty().addListener((o, ov, nv) -> {
                            if (nv && childItem.getChildren().size() == 1 && childItem.getChildren().get(0).getValue().name.equals("Loading...")) {
                                loadLevelAsync(childItem, childRel, receptor);
                            }
                        });
                    }
                    items.add(childItem);
                }
                Platform.runLater(() -> {
                    parent.getChildren().setAll(items);
                    parent.setExpanded(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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
            if (empty || item == null) { setText(null); setGraphic(null); setContextMenu(null); }
            else {
                setText((item.unitIcon != null ? item.unitIcon : (item.isDirectory ? "📂" : "📄")) + " " + item.name);
                ContextMenu menu = new ContextMenu();
                MenuItem copy = new MenuItem("Copiar");
                copy.setOnAction(e -> { clipboardPath = getAbs(item, getTreeItem()); isCut = false; btnPaste.setDisable(false); });
                MenuItem cut = new MenuItem("Cortar");
                cut.setOnAction(e -> { clipboardPath = getAbs(item, getTreeItem()); isCut = true; btnPaste.setDisable(false); });
                MenuItem paste = new MenuItem("Pegar aquí");
                paste.setOnAction(e -> startTransfer(clipboardPath, getTreeItem()));
                menu.setOnShowing(e -> paste.setDisable(clipboardPath.isEmpty()));
                
                MenuItem delete = new MenuItem("Eliminar");
                delete.getStyleClass().add("menu-delete");
                delete.setOnAction(e -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "¿Seguro que desea eliminar '" + item.name + "'?", ButtonType.YES, ButtonType.NO);
                    if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                        performDelete(item, getTreeItem());
                    }
                });
                menu.getItems().addAll(copy, cut, paste, new SeparatorMenuItem(), delete); setContextMenu(menu);
            }
        }
    }

    private void performDelete(FileNode node, TreeItem<FileNode> item) {
        String path = getAbs(node, item);
        virtualExecutor.submit(() -> {
            try {
                if ("JettraStream (Direct)".equals(comboProtocol.getValue())) {
                    deleteRec(Paths.get(path));
                } else {
                    // Usar receptor gRPC
                    TreeItem<FileNode> curr = item; while (curr.getParent() != null && curr.getParent().getParent() != null) curr = curr.getParent();
                    new JettraFileSystemReceptor(curr.getValue().path).deletePath(node.path);
                }
                Platform.runLater(() -> {
                    TreeItem<FileNode> parent = item.getParent();
                    if (parent != null) {
                        parent.getChildren().remove(item);
                        refreshSpecificNode(parent);
                    }
                });
            } catch (Exception ex) {
                transferErrors.add("Error al eliminar: " + ex.getMessage());
                showTransferSummary();
            }
        });
    }

    private String getAbs(FileNode node, TreeItem<FileNode> item) {
        TreeItem<FileNode> curr = item;
        while (curr.getParent() != null && curr.getParent().getParent() != null) curr = curr.getParent();
        return (item == curr) ? curr.getValue().path : new File(curr.getValue().path, node.path).getAbsolutePath();
    }

    private void startTransfer(String srcAbs, TreeItem<FileNode> targetItem) {
        // ---------------------------------------------------------------
        // 1️⃣  SINGLE‑INSTANCE DETECTION
        // ---------------------------------------------------------------
        try {
            java.nio.file.Path lockPath = java.nio.file.Path.of(System.getProperty("user.home"), ".jettra_sender.lock");
            if (java.nio.file.Files.exists(lockPath)) {
                String pidStr = java.nio.file.Files.readString(lockPath).trim();
                if (!pidStr.isEmpty()) {
                    long pid = Long.parseLong(pidStr);
                    if (pid != ProcessHandle.current().pid() && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                        transferErrors.add("Otra instancia de JettraFileSystem está en ejecución (PID=" + pid + "). Operación cancelada.");
                        showTransferSummary();
                        return;
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[WARN] No se pudo verificar el lock: " + ex.getMessage());
        }

        File src = new File(srcAbs);
        if (!src.exists()) {
            transferErrors.add("El origen no existe: " + srcAbs);
            showTransferSummary();
            return;
        }
        String targetAbs = getAbs(targetItem.getValue(), targetItem);
        File destBase = new File(targetAbs).isDirectory() ? new File(targetAbs) : new File(targetAbs).getParentFile();
        final TreeItem<FileNode> refreshItem = targetItem.getValue().isDirectory ? targetItem : targetItem.getParent();
        File conflict = new File(destBase, src.getName());
        
        if (conflict.getAbsolutePath().equals(src.getAbsolutePath())) {
            transferErrors.add("No se puede copiar un archivo o carpeta sobre sí mismo.");
            showTransferSummary();
            return;
        }
        
        if (src.isDirectory() && destBase.getAbsolutePath().startsWith(src.getAbsolutePath() + File.separator)) {
            transferErrors.add("No se puede copiar una carpeta dentro de sí misma.");
            showTransferSummary();
            return;
        }
        
        if (conflict.exists()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setHeaderText("Reemplazar existente");
            alert.setContentText("¿Confirmas el reemplazo de '" + src.getName() + "'?");
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            try { deleteRec(conflict.toPath()); } catch (IOException ex) { ex.printStackTrace(); }
        }
        
        startTimer();
        startChunkAnimation();
        Platform.runLater(() -> {
            transferTabPane.getTabs().clear();
            transferTabPane.setVisible(true);
        });

        currentTransferTask = new Task<>() {
            private final AtomicInteger filesDone = new AtomicInteger(0);
            private final AtomicLong chunksDoneTotal = new AtomicLong(0);
            private long totalChunksAllFiles = 0;
            private int totalFilesCount = 0;
            private final Map<File, TransferRow> rowMap = new ConcurrentHashMap<>();

            @Override
            protected Void call() throws Exception {
                File src = new File(srcAbs);
                if (!src.exists()) throw new NoSuchFileException(srcAbs);
                
                String absDest = getAbs(targetItem.getValue(), targetItem);
                File destBase = new File(absDest);

                transferErrors.clear();
                transferExecutor = Executors.newThreadPerTaskExecutor(vThreadFactory);
                List<File> allFiles = new ArrayList<>();
                if (src.isDirectory()) collectFiles(src, allFiles); else allFiles.add(src);
                totalFilesCount = allFiles.size();
                
                for (File f : allFiles) {
                    long fSize = f.length();
                    int totalChunks = ChunkManager.calculateTotalChunks(fSize);
                    totalChunksAllFiles += totalChunks;
                    TransferRow row = new TransferRow(f.getName(), totalChunks);
                    rowMap.put(f, row);
                    Platform.runLater(() -> {
                        Tab tab = new Tab(f.getName());
                        tab.setContent(new ScrollPane(row));
                        transferTabPane.getTabs().add(tab);
                    });
                }
                
                Semaphore semaphore = new Semaphore(16); 
                CountDownLatch totalLatch = new CountDownLatch(totalFilesCount);

                for (File f : allFiles) {
                    if (isCancelled()) break;
                    semaphore.acquire();
                    
                    final TransferRow row = rowMap.get(f);
                    transferExecutor.submit(() -> {
                        try {
                            if (isCancelled()) return;
                            String relPath = src.isDirectory() ? src.getName() + "/" + src.toPath().relativize(f.toPath()).toString() : f.getName();
                            updateMessage(String.format("Procesando: %d/%d | %s", filesDone.get() + 1, totalFilesCount, f.getName()));
                            
                            if ("JettraStream (Direct)".equals(comboProtocol.getValue())) {
                                transferFileDirect(f, destBase, relPath, refreshItem, chunksDoneTotal, totalChunksAllFiles, this::isCancelled, this::updateProgress, row);
                            } else {
                                transferFileXStream(f, destBase, relPath, refreshItem, chunksDoneTotal, totalChunksAllFiles, this::isCancelled, this::updateProgress, row);
                            }
                            
                            filesDone.incrementAndGet();
                            updateMessage(String.format("Transferido: %d/%d | %s", filesDone.get(), totalFilesCount, f.getName()));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        } catch (Exception ex) { 
                            if (!isCancelled()) {
                                transferErrors.add("Fallo en " + f.getName() + ": " + ex.getMessage());
                                ex.printStackTrace();
                            }
                        }
                        finally { semaphore.release(); totalLatch.countDown(); }
                    });
                }
                try {
                    totalLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (!isCancelled() && isCut) deleteRec(src.toPath());
                return null;
            }

            private void collectFiles(File dir, List<File> list) {
                if (dir.getName().equals(".jettra_sender_temp") || dir.getName().equals(".jettra_receptor_temp")) return;
                File[] fs = dir.listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        if (f.isDirectory()) {
                            collectFiles(f, list);
                        } else {
                            list.add(f);
                        }
                    }
                }
            }

            private void transferFileXStream(File s, File d, String fName, TreeItem<FileNode> targetNode, AtomicLong chunksDoneTotal, long totalChunks, BooleanSupplier isCancelled, BiConsumer<Long, Long> progress, TransferRow row) throws Exception {
                if (!s.exists()) throw new NoSuchFileException(s.getAbsolutePath());
                long sz = s.length(); int n = ChunkManager.calculateTotalChunks(sz);
                
                final String fileId = UUID.randomUUID().toString();
                File tempDir = new File(System.getProperty("user.home"), ".jettra_sender_temp/" + fileId);
                tempDir.mkdirs();
                
                updateMessage("Dividiendo: " + s.getName());
                ChunkManager.splitFile(s, tempDir);
                
                JettraFileSystemReceptor r = new JettraFileSystemReceptor(d.getAbsolutePath());
                CountDownLatch fileLatch = new CountDownLatch(n);
                AtomicBoolean firstChunkSent = new AtomicBoolean(false);
                AtomicInteger chunksDoneForFile = new AtomicInteger(0);
                
                Semaphore chunkLimit = new Semaphore(totalFilesCount < 5 ? 64 : 16);

                for (int i = 0; i < n; i++) {
                    if (isCancelled.getAsBoolean()) break;
                    chunkLimit.acquire();
                    
                    final int idx = i;
                    File chunkFile = new File(tempDir, "chunk_" + idx + ".jtra");
                    byte[] bytes = Files.readAllBytes(chunkFile.toPath());
                    
                    final byte[] finalData;
                    final boolean isCompressed;
                    if (bytes.length > 1024) {
                        finalData = ChunkManager.compress(bytes);
                        isCompressed = true;
                    } else {
                        finalData = bytes;
                        isCompressed = false;
                    }

                    transferExecutor.submit(() -> {
                        try {
                            if (isCancelled.getAsBoolean()) {
                                chunkLimit.release();
                                fileLatch.countDown();
                                return;
                            }
                            r.sendChunk(JettraChunk.newBuilder()
                                    .setFileId(fileId)
                                    .setFileName(fName)
                                    .setChunkIndex(idx)
                                    .setTotalChunks(n)
                                    .setFileSize(sz)
                                    .setData(finalData)
                                    .setIsCompressed(isCompressed)
                                    .build(), new JettraObserver<>() {
                                @Override public void onNext(TransferStatus v) {} 
                                @Override public void onError(Throwable t) { 
                                    transferErrors.add("Fallo de red en " + fName + ": " + t.getMessage());
                                    chunkLimit.release();
                                    fileLatch.countDown(); 
                                }
                                @Override public void onCompleted() { 
                                    fileLatch.countDown();
                                    chunkLimit.release();
                                    int done = chunksDoneForFile.incrementAndGet();
                                    row.updateProgress((double) done / n, idx, n);
                                    progress.accept(chunksDoneTotal.incrementAndGet(), totalChunks);
                                    if (firstChunkSent.compareAndSet(false, true)) Platform.runLater(() -> refreshSpecificNode(targetNode));
                                }
                            });
                        } catch (Exception ex) { 
                            transferErrors.add("Error en envío de bloque de " + fName + ": " + ex.getMessage());
                            chunkLimit.release();
                            fileLatch.countDown(); 
                        }
                    });
                }
                
                fileLatch.await(300, TimeUnit.SECONDS);
                
                deleteRec(tempDir.toPath());
                
                row.markComplete();
                Platform.runLater(() -> refreshSpecificNode(targetNode));
            }

            private void transferFileDirect(File s, File d, String fName, TreeItem<FileNode> targetNode, AtomicLong chunksDoneTotal, long totalChunks, BooleanSupplier isCancelled, BiConsumer<Long, Long> progress, TransferRow row) throws Exception {
                if (!s.exists()) throw new NoSuchFileException(s.getAbsolutePath());
                long sz = s.length(); int n = ChunkManager.calculateTotalChunks(sz);
                
                File destFile = new File(d, fName);
                if (destFile.getParentFile() != null) destFile.getParentFile().mkdirs();
                
                final String fileId = UUID.randomUUID().toString();
                File tempSrcDir = new File(System.getProperty("user.home"), ".jettra_sender_temp/" + fileId);
                ChunkManager.splitFile(s, tempSrcDir);

                File tempDestDir = new File(d, ".jettra_receptor_temp/" + fileId);
                tempDestDir.mkdirs();

                CountDownLatch fileLatch = new CountDownLatch(n);
                AtomicInteger chunksDoneForFile = new AtomicInteger(0);

                Semaphore chunkLimit = new Semaphore(totalFilesCount < 5 ? 64 : 16);

                for (int i = 0; i < n; i++) {
                    if (isCancelled.getAsBoolean()) break;
                    chunkLimit.acquire();
                    
                    final int idx = i;
                    transferExecutor.submit(() -> {
                        try {
                            if (isCancelled.getAsBoolean()) {
                                fileLatch.countDown();
                                return;
                            }
                            File srcChunk = new File(tempSrcDir, "chunk_" + idx + ".jtra");
                            File destChunk = new File(tempDestDir, "chunk_" + idx + ".jtra");
                            Files.copy(srcChunk.toPath(), destChunk.toPath(), StandardCopyOption.REPLACE_EXISTING);

                            fileLatch.countDown();
                            int done = chunksDoneForFile.incrementAndGet();
                            row.updateProgress((double) done / n, idx, n);
                            progress.accept(chunksDoneTotal.incrementAndGet(), totalChunks);
                        } catch (Exception ex) {
                            transferErrors.add("Error JettraStream en " + fName + ": " + ex.getMessage());
                            fileLatch.countDown();
                        } finally {
                            chunkLimit.release();
                        }
                    });
                }
                
                fileLatch.await(300, TimeUnit.SECONDS);
                
                if (isCancelled.getAsBoolean()) {
                    deleteRec(tempSrcDir.toPath());
                    deleteRec(tempDestDir.toPath());
                    return;
                }

                ChunkManager.mergeFiles(destFile, tempDestDir, n);
                deleteRec(tempSrcDir.toPath());
                deleteRec(tempDestDir.toPath());
                
                row.markComplete();
                Platform.runLater(() -> refreshSpecificNode(targetNode));
            }
        };

        progressBar.progressProperty().bind(currentTransferTask.progressProperty());
        statusLabel.textProperty().bind(currentTransferTask.messageProperty());
        currentTransferTask.setOnRunning(e -> { progressBar.setVisible(true); animPane.setVisible(true); btnCancel.setVisible(true); });
        currentTransferTask.setOnSucceeded(e -> { 
            finalizeTransfer("X-Stream Parallel finalizado"); 
            refreshSpecificNode(refreshItem); 
            if (isCut && transferErrors.isEmpty()) { 
                virtualExecutor.submit(() -> {
                    try { deleteRec(src.toPath()); } catch(Exception ex) {}
                });
                clipboardPath = ""; btnPaste.setDisable(true); 
            }
            if (!transferErrors.isEmpty()) showTransferSummary();
        });
        currentTransferTask.setOnFailed(e -> {
            finalizeTransfer("Error crítico en Protocolo");
            showTransferSummary();
            if (transferExecutor != null) transferExecutor.shutdownNow();
        });
        currentTransferTask.setOnCancelled(e -> {
            finalizeTransfer("Transferencia cancelada por el usuario");
            if (transferExecutor != null) transferExecutor.shutdownNow();
            statusLabel.setText("🛑 Transferencia cancelada. Recursos liberados.");
        });
        Thread t = new Thread(currentTransferTask);
        t.setDaemon(true);
        t.start();
    }


    private void showTransferSummary() {
        Platform.runLater(() -> {
            Alert alert = new Alert(transferErrors.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
            alert.setTitle("Resumen de Transferencia");
            alert.setHeaderText(transferErrors.isEmpty() ? "Transferencia Exitosa" : "Transferencia completada con errores");
            
            VBox content = new VBox(10);
            if (!transferErrors.isEmpty()) {
                TextArea area = new TextArea(String.join("\n", transferErrors));
                area.setEditable(false);
                area.setPrefHeight(200);
                content.getChildren().addAll(new Label("Se encontraron los siguientes problemas:"), area);
            } else {
                content.getChildren().add(new Label("Todos los archivos se procesaron correctamente."));
            }
            
            alert.getDialogPane().setContent(content);
            alert.showAndWait();
        });
    }

    private void startChunkAnimation() {
        animPane.getChildren().clear(); chunkTimeline = new Timeline(); chunkTimeline.setCycleCount(Timeline.INDEFINITE);
        for (int i = 0; i < 5; i++) {
            Rectangle c = new Rectangle(10, 10, Color.CYAN); c.setArcHeight(3); c.setArcWidth(3); c.setTranslateX(-20 * i); animPane.getChildren().add(c);
            chunkTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1 + (i * 0.2)), new KeyValue(c.translateXProperty(), 80)));
        }
        chunkTimeline.play();
    }

    private void refreshSpecificNode(TreeItem<FileNode> item) {
        if (item == null) return;
        TreeItem<FileNode> curr = item; while (curr.getParent() != null && curr.getParent().getParent() != null) curr = curr.getParent();
        final TreeItem<FileNode> rootUnit = curr;
        loadLevelAsync(item, item.getValue().path, new JettraFileSystemReceptor(rootUnit.getValue().path));
    }

    private void finalizeTransfer(String msg) {
        stopTimer();
        progressBar.setVisible(false); animPane.setVisible(false); btnCancel.setVisible(false);
        if (chunkTimeline != null) chunkTimeline.stop();
        statusLabel.textProperty().unbind(); statusLabel.setText(msg + " | Puerto: " + JettraMain.assignedPort);
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.seconds(5));
            pause.setOnFinished(ev -> transferTabPane.setVisible(false));
            pause.play();
        });
    }

    private void deleteRec(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { Files.delete(dir); return FileVisitResult.CONTINUE; }
        });
    }
    private String formatDuration(long millis) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
    }

    private void startTimer() {
        startTime = System.currentTimeMillis();
        if (timerTimeline != null) timerTimeline.stop();
        timerTimeline = new Timeline(new KeyFrame(Duration.millis(10), e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            timerLabel.setText(formatDuration(elapsed));
        }));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        timerTimeline.play();
    }

    private void stopTimer() {
        if (timerTimeline != null) timerTimeline.stop();
    }

    static class TransferRow extends VBox {
        ProgressBar bar;
        Label label;
        FlowPane senderBlocks;
        FlowPane receptorBlocks;
        Rectangle[] senderRects;
        Rectangle[] receptorRects;

        TransferRow(String fileName, int totalChunks) {
            setSpacing(15);
            setAlignment(Pos.TOP_CENTER);
            setPadding(new Insets(15));
            setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 10; -fx-border-color: rgba(0, 255, 255, 0.2); -fx-border-radius: 10;");

            label = new Label("Transfiriendo: " + fileName);
            label.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 16px; -fx-font-weight: bold;");

            bar = new ProgressBar(0);
            bar.setPrefWidth(400);
            bar.setStyle("-fx-accent: #00ffff;");

            HBox cubesBox = new HBox(40);
            cubesBox.setAlignment(Pos.CENTER);

            VBox senderBox = new VBox(5);
            senderBox.setAlignment(Pos.CENTER);
            Label lblSender = new Label("Sender (Descomposición)");
            lblSender.setStyle("-fx-text-fill: #00ffff;");
            senderBox.getChildren().add(lblSender);
            senderBlocks = new FlowPane(3, 3);
            senderBlocks.setPrefWrapLength(300);
            
            VBox receptorBox = new VBox(5);
            receptorBox.setAlignment(Pos.CENTER);
            Label lblReceptor = new Label("Receptor (Unión)");
            lblReceptor.setStyle("-fx-text-fill: #00ff00;");
            receptorBox.getChildren().add(lblReceptor);
            receptorBlocks = new FlowPane(3, 3);
            receptorBlocks.setPrefWrapLength(300);

            int displayChunks = Math.min(totalChunks, 200);
            senderRects = new Rectangle[displayChunks];
            receptorRects = new Rectangle[displayChunks];
            
            for (int i = 0; i < displayChunks; i++) {
                Rectangle sr = new Rectangle(10, 10, Color.CYAN);
                sr.setArcHeight(3); sr.setArcWidth(3);
                senderRects[i] = sr;
                senderBlocks.getChildren().add(sr);
                
                Rectangle rr = new Rectangle(10, 10, Color.web("#333333"));
                rr.setArcHeight(3); rr.setArcWidth(3);
                receptorRects[i] = rr;
                receptorBlocks.getChildren().add(rr);
            }

            senderBox.getChildren().add(senderBlocks);
            receptorBox.getChildren().add(receptorBlocks);

            Label arrow = new Label("➔");
            arrow.setStyle("-fx-font-size: 36px; -fx-text-fill: #555555;");

            cubesBox.getChildren().addAll(senderBox, arrow, receptorBox);

            getChildren().addAll(label, bar, cubesBox);
        }

        void updateProgress(double progress, int chunkIndex, int totalChunks) {
            Platform.runLater(() -> {
                bar.setProgress(progress);
                int rectIdx = (int) (((double) chunkIndex / totalChunks) * senderRects.length);
                if (rectIdx >= 0 && rectIdx < senderRects.length) {
                    Rectangle sr = senderRects[rectIdx];
                    Rectangle rr = receptorRects[rectIdx];
                    
                    FadeTransition ftOut = new FadeTransition(Duration.millis(300), sr);
                    ftOut.setToValue(0.1);
                    ScaleTransition stOut = new ScaleTransition(Duration.millis(300), sr);
                    stOut.setToX(0.5); stOut.setToY(0.5);
                    ParallelTransition ptOut = new ParallelTransition(ftOut, stOut);
                    ptOut.play();
                    
                    rr.setFill(Color.CYAN);
                    ScaleTransition pulse = new ScaleTransition(Duration.millis(300), rr);
                    pulse.setFromX(0.5); pulse.setFromY(0.5);
                    pulse.setToX(1.3); pulse.setToY(1.3);
                    pulse.setAutoReverse(true);
                    pulse.setCycleCount(2);
                    pulse.play();
                }
            });
        }
        
        void markComplete() {
            Platform.runLater(() -> {
                for (Rectangle rr : receptorRects) {
                    rr.setFill(Color.web("#00ff00"));
                }
                bar.setProgress(1.0);
                animateReconstruction();
            });
        }

        private void animateReconstruction() {
            label.setText("🏗️ Construyendo archivo...");
            label.setStyle("-fx-text-fill: #ffaa00; -fx-font-weight: bold;");
            
            ParallelTransition pt = new ParallelTransition();
            
            for (int i = 0; i < receptorRects.length; i++) {
                Rectangle r = receptorRects[i];
                TranslateTransition tt = new TranslateTransition(Duration.millis(800), r);
                tt.setToX(receptorBlocks.getWidth() / 2 - r.getLayoutX());
                tt.setToY(receptorBlocks.getHeight() / 2 - r.getLayoutY());
                
                RotateTransition rt = new RotateTransition(Duration.millis(800), r);
                rt.setToAngle(360);
                
                FadeTransition ft = new FadeTransition(Duration.millis(800), r);
                ft.setToValue(0);
                
                pt.getChildren().addAll(tt, rt, ft);
            }
            
            pt.setOnFinished(e -> {
                label.setText("🧹 Eliminando chunks temporales (UUID)...");
                label.setStyle("-fx-text-fill: #ff4444;");
                
                PauseTransition wait = new PauseTransition(Duration.seconds(1.2));
                wait.setOnFinished(ev -> {
                    label.setText("✅ Archivo Reconstruido");
                    label.setStyle("-fx-text-fill: #00ff00; -fx-font-weight: bold;");
                    if (getChildren().size() > 2) getChildren().remove(2);
                    
                    Glow glow = new Glow(0);
                    label.setEffect(glow);
                    Timeline pulse = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(glow.levelProperty(), 0)),
                        new KeyFrame(Duration.millis(500), new KeyValue(glow.levelProperty(), 1.0)),
                        new KeyFrame(Duration.seconds(1), new KeyValue(glow.levelProperty(), 0))
                    );
                    pulse.play();
                });
                wait.play();
            });
            pt.play();
        }
    }

    private void shutdown() {
        if (currentTransferTask != null && currentTransferTask.isRunning()) {
            currentTransferTask.cancel(true);
        }
        if (transferExecutor != null) {
            transferExecutor.shutdownNow();
        }
        virtualExecutor.shutdownNow();
        new Thread(() -> {
            try { 
                JettraMain.checkSingleInstanceAndCleanup();
                JettraMain.stopReceptor(); 
                Thread.sleep(100);
            } catch (Exception e) {}
            Platform.exit();
            System.exit(0);
        }).start();
        
        // Force exit after 1s if stuck
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            System.exit(0);
        }).start();
    }

    public static void main(String[] args) { launch(args); }

    static class TrackedThreadFactory implements java.util.concurrent.ThreadFactory {
        private final java.util.concurrent.ThreadFactory delegate;
        private final java.util.concurrent.atomic.AtomicInteger activeCount = new java.util.concurrent.atomic.AtomicInteger(0);

        public TrackedThreadFactory(java.util.concurrent.ThreadFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public Thread newThread(Runnable r) {
            return delegate.newThread(() -> {
                activeCount.incrementAndGet();
                try {
                    r.run();
                } finally {
                    activeCount.decrementAndGet();
                }
            });
        }

        public int getActiveCount() {
            return activeCount.get();
        }
    }
}
