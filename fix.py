import sys

with open('jettra-sender/src/main/java/io/jettra/fs/fx/JettraFileManagerFX.java', 'r') as f:
    lines = f.readlines()

# We want to replace everything from `private void startTransfer(String srcAbs, TreeItem<FileNode> targetItem) {`
# (around line 421) down to the end of `t.start(); }` that precedes `private void showTransferSummary() {` (around line 828).

start_idx = -1
end_idx = -1

for i, line in enumerate(lines):
    if "private void startTransfer(String srcAbs, TreeItem<FileNode> targetItem) {" in line:
        start_idx = i
        break

for i in range(start_idx, len(lines)):
    if "private void showTransferSummary() {" in lines[i]:
        end_idx = i - 1
        # walk backwards to skip empty lines
        while end_idx > 0 and lines[end_idx].strip() == "":
            end_idx -= 1
        break

print(f"Start: {start_idx}, End: {end_idx}")

new_method = """    private void startTransfer(String srcAbs, TreeItem<FileNode> targetItem) {
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
                File[] fs = dir.listFiles();
                if (fs != null) for (File f : fs) if (f.isDirectory()) collectFiles(f, list); else list.add(f);
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

                File tempDestDir = new File(System.getProperty("user.home"), ".jettra_receptor_temp/" + fileId);
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

                ChunkManager.mergeFiles(tempDestDir, destFile, n);
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
"""

lines = lines[:start_idx] + [new_method, '\n'] + lines[end_idx+1:]

with open('jettra-sender/src/main/java/io/jettra/fs/fx/JettraFileManagerFX.java', 'w') as f:
    f.writelines(lines)
