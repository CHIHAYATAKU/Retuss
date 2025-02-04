package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.CppFile;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.common.FileChangeListener;
import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.CppModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import javafx.scene.control.ProgressIndicator;
import javafx.geometry.Pos;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

public class CodeController implements CppModel.ModelChangeListener {
    @FXML
    private TabPane codeTabPane;
    @FXML
    private TreeView<String> fileTreeView;
    private Map<String, TreeItem<String>> directoryNodes = new HashMap<>();
    private TreeItem<String> rootItem;
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private List<Pair<CodeFile, Tab>> javaFileTabList = new ArrayList<>();
    private UmlController umlController;
    private Map<Tab, CppFile> tabToCppFileMap = new HashMap<>();
    private Map<String, Tab> cppFileNameToTabMap = new HashMap<>();
    private Map<Tab, Path> tabPathMap = new HashMap<>();

    // ファイル処理の抽象化
    private enum FileType {
        HEADER(".h"),
        IMPLEMENTATION(".cpp"),
        JAVA(".java");

        private final String extension;

        FileType(String ext) {
            this.extension = ext;
        }

        public static FileType fromFileName(String fileName) {
            if (fileName.endsWith(".h"))
                return HEADER;
            if (fileName.endsWith(".cpp"))
                return IMPLEMENTATION;
            if (fileName.endsWith(".java"))
                return JAVA;
            return null;
        }

        public static FileChooser.ExtensionFilter createFilter() {
            return new FileChooser.ExtensionFilter(
                    "Source Files", "*.java", "*.cpp", "*.h");
        }
    }

    @FXML
    private void importDirectory() {

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Import Project Directory");

        File selectedDir = dirChooser.showDialog(codeTabPane.getScene().getWindow());
        long startTime = System.nanoTime();
        if (selectedDir != null) {
            LoadingIndicator loadingIndicator = new LoadingIndicator();
            loadingIndicator.show();
            processDirectoryAsync(selectedDir)
                    .exceptionally(e -> {
                        handleError("Failed to import directory", e);
                        return null;
                    })
                    .thenRun(() -> {
                        loadingIndicator.hide();
                        long endTime = System.nanoTime();
                        long duration = (endTime - startTime) / 1_000_000; // ミリ秒に変換
                        System.out.println("importDirectory execution time: " + duration + " ms");
                    });
        }
    }

    @FXML
    private void importFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Files");
        fileChooser.getExtensionFilters().setAll(FileType.createFilter());

        List<File> files = fileChooser.showOpenMultipleDialog(codeTabPane.getScene().getWindow());
        long startTime = System.nanoTime();
        if (files != null) {
            LoadingIndicator loadingIndicator = new LoadingIndicator();
            loadingIndicator.show();
            CompletableFuture.runAsync(() -> {
                try {
                    for (File file : files) {
                        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                        Platform.runLater(() -> processFile(file, content));
                    }
                } catch (Exception e) {
                    handleError("Failed to import files", e);
                } finally {
                    Platform.runLater(() -> {
                        loadingIndicator.hide();
                        sortTabs();
                        umlController.handleRefreshAll();

                        long endTime = System.nanoTime();
                        long duration = (endTime - startTime) / 1_000_000; // ミリ秒に変換
                        System.out.println("importFiles execution time: " + duration + " ms");
                    });
                }
            });
        }
    }

    private CompletableFuture<Void> processDirectoryAsync(File directory) {
        return CompletableFuture.runAsync(() -> {
            Queue<File> directories = new LinkedList<>();
            directories.add(directory);

            while (!directories.isEmpty()) {
                File currentDir = directories.poll();
                File[] files = currentDir.listFiles();
                if (files == null)
                    continue;

                CompletableFuture<TreeItem<String>> rootFuture = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try {
                        TreeItem<String> root = new TreeItem<>(currentDir.getName());
                        if (currentDir == directory) {
                            rootItem.getChildren().add(root);
                        } else {
                            TreeItem<String> parentRoot = directoryNodes.get(currentDir.getParentFile().getPath());
                            if (parentRoot != null) {
                                parentRoot.getChildren().add(root);
                            }
                        }
                        directoryNodes.put(currentDir.getPath(), root);
                        rootFuture.complete(root);
                    } catch (Exception e) {
                        rootFuture.completeExceptionally(e);
                    }
                });

                // Future.getの結果を待つ
                TreeItem<String> currentRoot;
                try {
                    currentRoot = rootFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }

                for (File file : files) {
                    if (file.isDirectory()) {
                        directories.add(file);
                        continue;
                    }

                    try {
                        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                        TreeItem<String> finalCurrentRoot = currentRoot;
                        Platform.runLater(() -> {
                            processFile(file, content);
                            addFileToTree(file, finalCurrentRoot);
                        });
                    } catch (IOException e) {
                        handleError("Error reading file: " + file.getName(), e);
                    }
                }
            }

            Platform.runLater(() -> {
                sortTreeItems(rootItem);
                sortTabs();
                umlController.handleRefreshAll();
            });
        });
    }

    private void processFile(File file, String content) {
        try {
            FileType type = FileType.fromFileName(file.getName());
            if (type == null)
                return;

            Tab tab = null;
            switch (type) {
                case JAVA:
                    CodeFile javaFile = new CodeFile(file.getName());
                    javaFile.updateCode(content);
                    javaModel.addNewFile(javaFile);
                    tab = createCodeTab(javaFile);
                    break;
                case HEADER:
                case IMPLEMENTATION:
                    String baseName = file.getName().replaceAll("\\.(h|cpp)$", "");
                    cppModel.addNewFile(file.getName());
                    CppFile cppFile = type == FileType.HEADER ? cppModel.findHeaderFile(baseName)
                            : cppModel.findImplFile(baseName);
                    if (cppFile != null) {
                        cppFile.updateCode(content);
                        tab = createCppCodeTab(cppFile);
                    }
                    break;
            }

            // タブが作成された場合のみパスをマップに追加
            if (tab != null) {
                tabPathMap.put(tab, file.toPath());
                if (!codeTabPane.getTabs().contains(tab)) {
                    codeTabPane.getTabs().add(tab);
                }
            }
        } catch (Exception e) {
            handleError("Error processing file: " + file.getName(), e);
        }
    }

    private void processJavaFile(String fileName, String content) {
        if (fileName.endsWith(".java")) {
            CodeFile javaFile = new CodeFile(fileName);
            javaFile.updateCode(content);
            javaModel.addNewFile(javaFile);
            // createCodeTab(javaFile);
        }
        return;
    }

    private void processCppFile(String fileName, String content) {
        String baseName = fileName.replaceAll("\\.(h|cpp)$", "");

        if (fileName.endsWith(".h")) {
            cppModel.addNewFile(fileName);
            CppFile headerFile = cppModel.findHeaderFile(baseName);
            if (headerFile != null) {
                headerFile.updateCode(content);
                // createCppCodeTab(headerFile);
            }
        } else {
            cppModel.addNewFile(fileName);
            CppFile implFile = cppModel.findImplFile(baseName);
            if (implFile != null) {
                implFile.updateCode(content);
                // createCppCodeTab(implFile);
            }
        }
        return;
    }

    private void addFileToTree(File file, TreeItem<String> parent) {
        TreeItem<String> fileItem = new TreeItem<>(file.getName());
        parent.getChildren().add(fileItem);
    }

    private class LoadingIndicator {
        private final ProgressIndicator indicator;
        private final StackPane overlay;

        LoadingIndicator() {
            indicator = new ProgressIndicator();
            indicator.setMaxSize(50, 50);
            overlay = new StackPane(indicator);
            overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3);");
            overlay.setAlignment(Pos.CENTER);

            AnchorPane.setTopAnchor(overlay, 0.0);
            AnchorPane.setBottomAnchor(overlay, 0.0);
            AnchorPane.setLeftAnchor(overlay, 0.0);
            AnchorPane.setRightAnchor(overlay, 0.0);
        }

        void show() {
            Platform.runLater(() -> {
                AnchorPane root = (AnchorPane) codeTabPane.getScene().getRoot();
                root.getChildren().add(overlay);
            });
        }

        void hide() {
            Platform.runLater(() -> {
                AnchorPane root = (AnchorPane) codeTabPane.getScene().getRoot();
                root.getChildren().remove(overlay);
            });
        }
    }

    private void sortTreeItems(TreeItem<String> item) {
        if (!item.isLeaf()) {
            // 子アイテムをソート
            List<TreeItem<String>> children = new ArrayList<>(item.getChildren());
            children.sort((item1, item2) -> {
                // ディレクトリを先に
                boolean isDir1 = !item1.isLeaf();
                boolean isDir2 = !item2.isLeaf();
                if (isDir1 != isDir2) {
                    return Boolean.compare(isDir2, isDir1);
                }

                String name1 = item1.getValue();
                String name2 = item2.getValue();

                // ファイルの場合は.hと.cppをグループ化
                if (!isDir1) {
                    String baseName1 = name1.replaceAll("\\.(h|cpp)$", "");
                    String baseName2 = name2.replaceAll("\\.(h|cpp)$", "");

                    // まずベース名で比較
                    int baseCompare = baseName1.compareTo(baseName2);
                    if (baseCompare != 0)
                        return baseCompare;

                    // 同じベース名の場合、.hを先に
                    boolean isHeader1 = name1.endsWith(".h");
                    boolean isHeader2 = name2.endsWith(".h");
                    return Boolean.compare(isHeader2, isHeader1);
                }

                // ディレクトリの場合は単純な名前比較
                return name1.compareTo(name2);
            });

            item.getChildren().setAll(children);

            // 再帰的に子ディレクトリもソート
            for (TreeItem<String> child : item.getChildren()) {
                if (!child.isLeaf()) {
                    sortTreeItems(child);
                }
            }
        }
    }

    private void sortTabs() {
        List<Tab> tabs = new ArrayList<>(codeTabPane.getTabs());
        tabs.sort((tab1, tab2) -> {
            String name1 = tab1.getText().replace("*", "");
            String name2 = tab2.getText().replace("*", "");

            // ベース名を取得
            String baseName1 = name1.replaceAll("\\.(h|cpp)$", "");
            String baseName2 = name2.replaceAll("\\.(h|cpp)$", "");

            // まずベース名で比較
            int baseCompare = baseName1.compareTo(baseName2);
            if (baseCompare != 0)
                return baseCompare;

            // 同じベース名の場合、.hを先に
            boolean isHeader1 = name1.endsWith(".h");
            boolean isHeader2 = name2.endsWith(".h");
            return Boolean.compare(isHeader2, isHeader1);
        });

        codeTabPane.getTabs().setAll(tabs);
    }

    // private void markTabAsModified(Tab tab, boolean modified) {
    // Platform.runLater(() -> {
    // try {
    // String currentText = tab.getText();
    // String baseText = getTabBaseText(tab);

    // if (modified && !currentText.endsWith("*")) {
    // tab.setText(baseText + "*");
    // tab.setStyle("-fx-text-fill: #2196F3;"); // Material Designの青色
    // } else if (!modified) {
    // tab.setText(baseText);
    // tab.setStyle(""); // スタイルをリセット
    // }
    // } catch (Exception e) {
    // System.err.println("Error marking tab as modified: " + e.getMessage());
    // }
    // });
    // }

    private String getTabBaseText(Tab tab) {
        return tab.getText().replace("*", "");
    }

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        // 双方向の参照を設定
        controller.setCodeController(this);
    }

    @Override
    public void onModelChanged() {
        // 全体更新が必要な場合用
    }

    @Override
    public void onFileAdded(CppFile file) {
        Platform.runLater(() -> {
            try {
                Tab tab = createCppCodeTab(file);
                if (!codeTabPane.getTabs().contains(tab)) {
                    codeTabPane.getTabs().add(tab);
                }
            } catch (Exception e) {
                handleError("Error adding file: " + file.getFileName(), e);
            }
        });
    }

    @Override
    public void onClassAdded(CppFile file) {
        Platform.runLater(() -> {
            try {
                Tab tab = createCppCodeTab(file);
                if (!codeTabPane.getTabs().contains(tab)) {
                    codeTabPane.getTabs().add(tab);
                }
            } catch (Exception e) {
                handleError("Error adding file: " + file.getFileName(), e);
            }
        });
    }

    private void handleError(String message, Throwable e) {
        Platform.runLater(() -> {
            System.err.println(message);
            // e.printStackTrace();
            // showError(message + "\n" + e.getMessage());
        });
    }

    @Override
    public void onFileUpdated(CppFile file) {
        Platform.runLater(() -> {
            try {
                // Find the corresponding tab for the updated file
                Tab tab = cppFileNameToTabMap.get(file.getFileName());

                if (tab != null) {
                    // Select the tab
                    codeTabPane.getSelectionModel().select(tab);

                    // Get the CodeArea from the tab's content
                    AnchorPane anchorPane = (AnchorPane) tab.getContent();
                    CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);

                    // Store the current caret position
                    int caretPosition = codeArea.getCaretPosition();

                    // Update the code content
                    codeArea.replaceText(file.getCode());

                    // Restore caret position if it's within bounds
                    if (caretPosition <= codeArea.getLength()) {
                        codeArea.moveTo(caretPosition);
                    }
                } else {
                    // If no existing tab is found, create a new one
                    Tab newTab = createCppCodeTab(file);
                    codeTabPane.getTabs().add(newTab);
                    codeTabPane.getSelectionModel().select(newTab);
                }
            } catch (Exception e) {
                System.err.println("Error updating code tab: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onFileDeleted(String className) {
        Platform.runLater(() -> {
            try {
                Stream.of(".h", ".cpp")
                        .map(ext -> className + ext)
                        .forEach(fileName -> {
                            Tab tab = cppFileNameToTabMap.get(fileName);
                            if (tab != null) {
                                removeMappingByTab(tab);
                                codeTabPane.getTabs().remove(tab);
                            }
                        });
            } catch (Exception e) {
                handleError("Error deleting class: " + className, e);
            }
        });
    }

    @Override
    public void onFileRenamed(String oldName, String newName) {
        Platform.runLater(() -> {
            try {
                Tab tab = cppFileNameToTabMap.get(oldName);
                if (tab != null) {
                    CppFile file = tabToCppFileMap.get(tab);
                    updateMapping(oldName, newName, tab, file);
                    updateTabTitle(tab, file);
                }
            } catch (Exception e) {
                handleError("Error renaming file: " + oldName + " to " + newName, e);
            }
        });
    }

    private void debugTabPaneState() {
        System.out.println("\nTab Pane Debug Info:");
        System.out.println("Total tabs: " + codeTabPane.getTabs().size());
        // System.out.println("Visible tabs: " +
        // codeTabPane.getTabs().filtered(Tab::isVisible).size());

        // タブヘッダー領域の情報
        Node header = codeTabPane.lookup(".tab-header-area");
        if (header != null) {
            System.out.println("Header width: " + header.getBoundsInLocal().getWidth());
            System.out.println("Header visible: " + header.isVisible());
        }

        // コントロールボタン（ドロップダウン）の情報
        Node controlButtons = codeTabPane.lookup(".control-buttons-tab");
        if (controlButtons != null) {
            System.out.println("Control buttons visible: " + controlButtons.isVisible());
        }
    }

    @FXML
    private ScrollBar tabScrollBar;

    @FXML
    private void initialize() {
        javaModel.setCodeController(this);
        initializeTreeView();
        configureTabPane();
        setupKeyboardShortcuts();
    }

    private void initializeTreeView() {
        rootItem = new TreeItem<>("Project Files");
        rootItem.setExpanded(true);
        fileTreeView.setRoot(rootItem);
        fileTreeView.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        handleFileSelection(newValue);
                    }
                });
    }

    private void configureTabPane() {
        codeTabPane.setTabMinWidth(80);
        codeTabPane.setTabMaxWidth(150);
        codeTabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        setupTabPaneScrolling();
    }

    private void setupTabPaneScrolling() {
        codeTabPane.addEventFilter(ScrollEvent.ANY, event -> {
            if (event.isShortcutDown()) {
                Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
                if (selectedTab != null) {
                    int currentIndex = codeTabPane.getTabs().indexOf(selectedTab);

                    if (event.getDeltaY() > 0 && currentIndex > 0) {
                        // 上スクロールで前のタブへ
                        codeTabPane.getSelectionModel().select(currentIndex - 1);
                    } else if (event.getDeltaY() < 0 && currentIndex < codeTabPane.getTabs().size() - 1) {
                        // 下スクロールで次のタブへ
                        codeTabPane.getSelectionModel().select(currentIndex + 1);
                    }
                    event.consume();
                }
            }
        });
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (codeTabPane.getScene() != null) {
                codeTabPane.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
                        saveCurrentFile();
                        event.consume();
                    }
                });
            }
        });
    }

    @FXML
    private void saveCurrentFile() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null)
            return;

        try {
            String content = getTabContent(selectedTab);
            saveFile(selectedTab, content);
            // エディタの初期テキストを更新
            // AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
            // CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
            // codeArea.replaceText(content);

            // markTabAsModified(selectedTab, false);
        } catch (IOException e) {
            showError("Failed to save file: " + e.getMessage());
        }
    }

    private String getTabContent(Tab tab) {
        AnchorPane anchorPane = (AnchorPane) tab.getContent();
        CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
        return codeArea.getText();
    }

    private void saveFile(Tab tab, String content) throws IOException {
        Path path = tabPathMap.get(tab);
        if (path == null) {
            saveFileAs();
            return;
        }

        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private Map<Tab, CompletableFuture<Void>> updateFutures = new HashMap<>();

    private void setupCodeAreaChangeListener(CodeArea codeArea, Tab tab) {
        // フォーカスの監視
        codeArea.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                // フォーカスを失った時に最後の状態を保存
                // updateCppCodeFile(tab, codeArea.getText());
            }
        });

        // テキスト変更の監視（フォーカスがある時のみ）
        codeArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue) && codeArea.isFocused()) {
                updateCppCodeFile(tab, newValue);
            }
        });
    }

    public void postInitialize() {
        // スクリーンに表示された後の初期化処理
        if (codeTabPane.getScene() == null) {
            return;
        }

        // キーボードショートカットの設定
        codeTabPane.getScene().getWindow().setOnShowing(event -> {
            codeTabPane.getScene().addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
                if (keyEvent.isShortcutDown() && keyEvent.getCode() == KeyCode.S) {
                    saveCurrentFile();
                    keyEvent.consume();
                }
            });
        });
    }

    @FXML
    private void saveFileAs() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null)
            return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save As");

        if (umlController.isJavaSelected()) {
            fileChooser.getExtensionFilters().setAll(
                    new FileChooser.ExtensionFilter("Java Files", "*.java"));
        } else {
            fileChooser.getExtensionFilters().setAll(
                    new FileChooser.ExtensionFilter("C++ Header Files", "*.h"),
                    new FileChooser.ExtensionFilter("C++ Source Files", "*.cpp"));
        }

        File file = fileChooser.showSaveDialog(codeTabPane.getScene().getWindow());
        if (file != null) {
            try {
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                Files.write(file.toPath(), codeArea.getText().getBytes());

                // タブの情報を更新
                selectedTab.setText(file.getName());
                tabPathMap.put(selectedTab, file.toPath());
                // markTabAsModified(selectedTab, false);

                System.err.println("File saved: " + file.getName());
            } catch (IOException e) {
                showError("Failed to save file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void exitApplication() {
        // 未保存の変更がある場合は確認ダイアログを表示
        if (hasUnsavedChanges()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("There are unsaved changes.");
            alert.setContentText("Do you want to save changes before exiting?");

            ButtonType buttonTypeSave = new ButtonType("Save");
            ButtonType buttonTypeDontSave = new ButtonType("Don't Save");
            ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(buttonTypeSave, buttonTypeDontSave, buttonTypeCancel);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == buttonTypeSave) {
                saveCurrentFile();
            } else if (result.get() == buttonTypeCancel) {
                return;
            }
        }

        Platform.exit();
    }

    private void handleFileSelection(TreeItem<String> item) {
        String fileName = item.getValue();
        Optional<Tab> existingTab = findTabByName(fileName);

        if (existingTab.isPresent()) {
            // タブが見つかった場合は選択
            codeTabPane.getSelectionModel().select(existingTab.get());
        } else if (fileName.endsWith(".h") || fileName.endsWith(".cpp")) {
            // C++ファイルの場合
            openCppFile(fileName);
        } else if (fileName.endsWith(".java")) {
            // Javaファイルの場合を追加
            openJavaFile(fileName);
        }
    }

    private void openCppFile(String fileName) {
        CppFile file = fileName.endsWith(".h")
                ? cppModel.findHeaderFile(fileName.replace(".h", ""))
                : cppModel.findImplFile(fileName.replace(".cpp", ""));

        if (file != null) {
            Tab tab = createCppCodeTab(file);
            codeTabPane.getTabs().add(tab);
            codeTabPane.getSelectionModel().select(tab);
        }
    }

    private void openJavaFile(String fileName) {
        CodeFile javaFile = new CodeFile(fileName);
        // 既存のファイルを探す
        for (Pair<CodeFile, Tab> pair : javaFileTabList) {
            if (pair.getKey().getFileName().equals(fileName)) {
                codeTabPane.getSelectionModel().select(pair.getValue());
                return;
            }
        }
        // 見つからない場合は新しいタブを作成
        Tab tab = createCodeTab(javaFile);
        if (!codeTabPane.getTabs().contains(tab)) {
            codeTabPane.getTabs().add(tab);
        }
        codeTabPane.getSelectionModel().select(tab);
    }

    private Optional<Tab> findTabByName(String fileName) {
        // C++ファイルのタブを確認
        Tab cppTab = cppFileNameToTabMap.get(fileName);
        if (cppTab != null) {
            return Optional.of(cppTab);
        }

        // Javaファイルのタブを確認
        return javaFileTabList.stream()
                .filter(pair -> pair.getKey().getFileName().equals(fileName))
                .map(Pair::getValue)
                .findFirst();
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @FXML
    private void showNewFileDialog() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/newFileDialog.fxml"));
            Parent parent = fxmlLoader.load();
            NewFileDialogController controller = fxmlLoader.getController();

            if (umlController == null) {
                System.err.println("Warning: UmlController is not set!");
                return;
            }

            controller.setUmlController(umlController);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New File Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateCodeTab(ICodeFile file) {
        if (file instanceof CodeFile) {
            // 既存のJavaファイル処理
            updateCodeTab((CodeFile) file);
        } else if (file instanceof CppFile) {
            // C++ファイルの処理
            updateCodeTab((CppFile) file);
        }
    }

    // 既存のJava用メソッド
    public void updateCodeTab(CodeFile changedCodeFile) {
        UUID changedFileId = changedCodeFile.getID();

        changedCodeFile.addChangeListener(new FileChangeListener() {
            @Override
            public void onFileChanged(ICodeFile file) {
                // ファイル内容の変更時の処理は必要に応じて実装
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                Platform.runLater(() -> {
                    for (Pair<CodeFile, Tab> fileTabPair : javaFileTabList) {
                        if (fileTabPair.getKey().getID().equals(changedFileId)) {
                            fileTabPair.getValue().setText(newName);
                            break;
                        }
                    }
                });
            }
        });

        for (Pair<CodeFile, Tab> fileTabPair : javaFileTabList) {
            if (changedFileId.equals(fileTabPair.getKey().getID())) {
                Tab targetTab = fileTabPair.getValue();
                AnchorPane anchorPane = (AnchorPane) targetTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                codeArea.replaceText(changedCodeFile.getCode());
                codeTabPane.getSelectionModel().select(targetTab);
                return;
            }
        }

        Tab targetTab = createCodeTab(changedCodeFile);
        codeTabPane.getSelectionModel().select(targetTab);
    }

    // C++用の新しいメソッド
    public void updateCodeTab(CppFile file) {
        try {
            Tab tab = cppFileNameToTabMap.get(file.getFileName());

            if (tab != null) {
                // 既存のタブを更新
                updateTabTitle(tab, file);
                // コンテンツも更新
                AnchorPane anchorPane = (AnchorPane) tab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                codeArea.replaceText(file.getCode());
            } else {
                // 新しいタブを作成
                Tab newTab = createCppCodeTab(file);
                codeTabPane.getTabs().add(newTab);
                codeTabPane.getSelectionModel().select(newTab);
                debugTabPaneState();
            }
        } catch (Exception e) {
            System.err.println("Failed to update code tab: " + e.getMessage());
        }
    }

    public void onClassDeleted(String className) {
        Platform.runLater(() -> onFileDeleted(className));
    }

    private void updateTabTitle(Tab tab, CppFile file) {
        String fileName = file.getFileName();
        boolean wasModified = tab.getText().endsWith("*");
        tab.setText(fileName); // 基本名を設定
        if (wasModified) {
            // markTabAsModified(tab, true); // 変更状態を維持
        }

        // ツールチップの設定
        String baseName = fileName.replaceAll("\\.(h|cpp)$", "");
        tab.setTooltip(new Tooltip(String.format("Class: %s%nType: %s",
                baseName,
                file.isHeader() ? "Header File" : "Implementation File")));
    }

    private boolean hasUnsavedChanges() {
        return codeTabPane.getTabs().stream()
                .anyMatch(tab -> tab.getText().endsWith("*")); // マークの有無でチェック
    }

    // 既存のJava用タブ作成メソッド
    private Tab createCodeTab(CodeFile codeFile) {
        // 既存のタブをチェック
        Optional<Tab> existingTab = javaFileTabList.stream()
                .filter(pair -> pair.getKey().getFileName().equals(codeFile.getFileName()))
                .map(Pair::getValue)
                .findFirst();

        if (existingTab.isPresent()) {
            return existingTab.get();
        }
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                updateCodeFile();
            }
        });
        codeArea.replaceText(codeFile.getCode());

        AnchorPane codeAnchor = new AnchorPane(codeArea);
        AnchorPane.setBottomAnchor(codeArea, 0.0);
        AnchorPane.setTopAnchor(codeArea, 0.0);
        AnchorPane.setLeftAnchor(codeArea, 0.0);
        AnchorPane.setRightAnchor(codeArea, 0.0);

        Tab codeTab = new Tab();
        codeTab.setContent(codeAnchor);
        codeTab.setText(codeFile.getFileName());
        codeTab.setClosable(false);

        // 新しい共通インターフェースを使用
        codeFile.addChangeListener(new FileChangeListener() {
            @Override
            public void onFileChanged(ICodeFile file) {
                Platform.runLater(() -> {
                    CodeArea area = (CodeArea) ((AnchorPane) codeTab.getContent()).getChildren().get(0);
                    area.replaceText(file.getCode());
                });
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                Platform.runLater(() -> {
                    System.out.println("ファイル名が変更されました: " + oldName + " -> " + newName);
                    codeTab.setText(newName);
                });
            }
        });

        codeTabPane.getTabs().add(codeTab);
        javaFileTabList.add(new Pair<>(codeFile, codeTab));

        return codeTab;
    }

    private Tab createCppCodeTab(CppFile file) {
        try {
            String fileName = file.getFileName();
            Tab existingTab = cppFileNameToTabMap.get(fileName);
            if (existingTab != null) {
                return existingTab;
            }

            Tab tab = new Tab(fileName);
            CodeArea codeArea = createCodeArea(file);
            tab.setContent(createAnchorPane(codeArea));
            tab.setClosable(false);

            setupCodeAreaChangeListener(codeArea, tab);
            setupTabContent(tab, file);
            addMapping(tab, file);

            return tab;
        } catch (Exception e) {
            handleError("Error creating tab for: " + file.getFileName(), e);
            throw new RuntimeException(e);
        }
    }

    private void setupTabContent(Tab tab, CppFile file) {
        updateTabTitle(tab, file);
        tab.setStyle("-fx-max-width: 200px;");
        tab.setTooltip(new Tooltip(String.format("%s\nType: %s",
                file.getFileName(),
                file.isHeader() ? "Header File" : "Implementation File")));
    }

    private CodeArea createCodeArea(CppFile file) {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.replaceText(file.getCode());
        return codeArea;
    }

    private AnchorPane createAnchorPane(Node content) {
        AnchorPane anchorPane = new AnchorPane(content);
        AnchorPane.setTopAnchor(content, 0.0);
        AnchorPane.setBottomAnchor(content, 0.0);
        AnchorPane.setLeftAnchor(content, 0.0);
        AnchorPane.setRightAnchor(content, 0.0);
        return anchorPane;
    }

    // 既存のJava用コード更新メソッド
    private void updateCodeFile() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null)
            return;

        for (Pair<CodeFile, Tab> fileTabPair : javaFileTabList) {
            if (fileTabPair.getValue().equals(selectedTab)) {
                CodeFile targetCodeFile = fileTabPair.getKey();
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                String code = codeArea.getText();
                javaModel.updateCodeFile(targetCodeFile, code);
                return;
            }
        }
    }

    public String getSelectedClassName() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        String baseText = getTabBaseText(selectedTab);
        return baseText.replaceAll("\\.(h|cpp|java)$", "");
    }

    // C++用のコード更新メソッド
    private void updateCppCodeFile(Tab tab, String content) {
        CppFile file = tabToCppFileMap.get(tab);
        if (file == null)
            return;

        CompletableFuture.runAsync(() -> cppModel.updateCodeFile(file, content))
                .exceptionally(e -> {
                    handleError("Error updating file: " + file.getFileName(), e);
                    return null;
                });
    }

    private void addMapping(Tab tab, CppFile file) {
        if (tab == null || file == null) {
            throw new IllegalArgumentException("Tab and File cannot be null");
        }
        String fileName = file.getFileName();
        tabToCppFileMap.put(tab, file);
        cppFileNameToTabMap.put(fileName, tab);
    }

    private void removeMappingByTab(Tab tab) {
        if (tab == null)
            return;
        CppFile file = tabToCppFileMap.remove(tab);
        if (file != null) {
            cppFileNameToTabMap.remove(file.getFileName());
        }
        tabPathMap.remove(tab);
    }

    private void removeMappingByFileName(String fileName) {
        if (fileName == null)
            return;
        Tab tab = cppFileNameToTabMap.remove(fileName);
        if (tab != null) {
            tabToCppFileMap.remove(tab);
            tabPathMap.remove(tab);
        }
    }

    private void updateMapping(String oldFileName, String newFileName, Tab tab, CppFile file) {
        if (tab == null || oldFileName == null || newFileName == null || file == null)
            return;

        cppFileNameToTabMap.remove(oldFileName);
        cppFileNameToTabMap.put(newFileName, tab);
        tabToCppFileMap.put(tab, file);
    }
}