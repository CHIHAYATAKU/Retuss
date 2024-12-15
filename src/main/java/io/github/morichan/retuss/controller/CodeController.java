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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CodeController implements CppModel.ModelChangeListener {
    @FXML
    private TabPane codeTabPane;
    @FXML
    private TreeView<String> fileTreeView;
    private ProgressIndicator loadingIndicator;
    private Map<String, TreeItem<String>> directoryNodes = new HashMap<>();
    private TreeItem<String> rootItem;
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private List<Pair<CodeFile, Tab>> javaFileTabList = new ArrayList<>();
    private List<Pair<CppFile, Tab>> cppFileTabList = new ArrayList<>();
    private UmlController umlController;
    private Map<Tab, Path> tabPathMap = new HashMap<>();

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
            Tab tab = createCppCodeTab(file);
            if (!codeTabPane.getTabs().contains(tab)) {
                codeTabPane.getTabs().add(tab);
            }
        });
    }

    @Override
    public void onFileUpdated(CppFile file) {
        // Platform.runLater(() -> {
        // Optional<Tab> tab = findTab(file.getID());
        // if (tab.isPresent()) {
        // updateCodeTab(file);
        // }
        // });
    }

    @Override
    public void onFileDeleted(String className) {
        Platform.runLater(() -> {
            cppFileTabList.removeIf(pair -> {
                if (pair.getKey().getFileName().equals(className + ".h") ||
                        pair.getKey().getFileName().equals(className + ".cpp")) {
                    codeTabPane.getTabs().remove(pair.getValue());
                    return true;
                }
                return false;
            });
        });
    }

    @Override
    public void onFileRenamed(String oldName, String newName) {
        Platform.runLater(() -> {
            for (Pair<CppFile, Tab> pair : cppFileTabList) {
                if (pair.getKey().getFileName().equals(newName)) { // oldNameから変更
                    updateCodeTab(pair.getKey()); // ツールチップなどの更新
                }
            }
        });
    }

    @FXML
    private void importDirectory() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Import Project Directory");

        File selectedDir = dirChooser.showDialog(codeTabPane.getScene().getWindow());
        if (selectedDir != null) {

            // 非同期処理のみを使用
            try {
                processDirectoryAsync(selectedDir);
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showError("Failed to import directory: " + e.getMessage()));
            }
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

    private void markTabAsModified(Tab tab, boolean modified) {
        Platform.runLater(() -> {
            try {
                String currentText = tab.getText();
                String baseText = getTabBaseText(tab);

                if (modified && !currentText.endsWith("*")) {
                    tab.setText(baseText + "*");
                    tab.setStyle("-fx-text-fill: #2196F3;"); // Material Designの青色
                } else if (!modified) {
                    tab.setText(baseText);
                    tab.setStyle(""); // スタイルをリセット
                }
            } catch (Exception e) {
                System.err.println("Error marking tab as modified: " + e.getMessage());
            }
        });
    }

    private String getTabBaseText(Tab tab) {
        return tab.getText().replace("*", "");
    }

    private void processDirectoryAsync(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            Platform.runLater(this::hideLoadingIndicator);
            return;
        }

        showLoadingIndicator();

        CompletableFuture.runAsync(() -> {
            try {
                Map<File, String> fileContents = new HashMap<>();
                Map<File, TreeItem<String>> fileItems = new HashMap<>();

                for (File file : files) {
                    if (file.isDirectory()) {
                        processDirectoryAsync(file);
                        continue;
                    }

                    try {
                        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        fileContents.put(file, content);
                        fileItems.put(file, new TreeItem<>(file.getName()));
                    } catch (IOException e) {
                        System.err.println("Error reading file: " + file.getName());
                    }
                }

                Platform.runLater(() -> {
                    TreeItem<String> projectRoot = new TreeItem<>(directory.getName());
                    rootItem.getChildren().add(projectRoot);
                    directoryNodes.put(directory.getPath(), projectRoot);

                    fileContents.forEach((file, content) -> {
                        try {
                            TreeItem<String> fileItem = fileItems.get(file);
                            TreeItem<String> parent = directoryNodes.get(directory.getPath());
                            if (parent != null) {
                                parent.getChildren().add(fileItem);
                            }

                            Tab tab = null;
                            if (file.getName().endsWith(".java")) {
                                CodeFile javaFile = new CodeFile(file.getName());
                                javaFile.updateCode(content);
                                javaModel.addNewFile(javaFile);
                                tab = createCodeTab(javaFile);
                            } else if (file.getName().endsWith(".h")) {
                                String baseName = file.getName().replace(".h", "");
                                cppModel.addNewFile(file.getName());
                                CppFile headerFile = cppModel.findHeaderFile(baseName);
                                if (headerFile != null) {
                                    headerFile.updateCode(content);
                                    tab = createCppCodeTab(headerFile);
                                }
                            } else if (file.getName().endsWith(".cpp")) {
                                String baseName = file.getName().replace(".cpp", "");
                                CppFile implFile = cppModel.findImplFile(baseName);
                                if (implFile == null) {
                                    cppModel.addNewFile(baseName + ".h");
                                    implFile = cppModel.findImplFile(baseName);
                                }
                                if (implFile != null) {
                                    implFile.updateCode(content);
                                    tab = createCppCodeTab(implFile);
                                }
                            }

                            if (tab != null) {
                                if (!codeTabPane.getTabs().contains(tab)) {
                                    codeTabPane.getTabs().add(tab);
                                }
                                tabPathMap.put(tab, file.toPath());
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing file: " + file.getName());
                        }
                    });
                    // ツリーアイテムのソート
                    sortTreeItems(projectRoot);

                    // タブのソート
                    sortTabs();
                });
            } finally {
                // 処理が完了したらインジケーターを非表示にする
                if (!hasChildDirectories(directory)) {
                    Platform.runLater(this::hideLoadingIndicator);
                }
            }
        });
    }

    private boolean hasChildDirectories(File directory) {
        File[] files = directory.listFiles();
        if (files == null)
            return false;
        return Arrays.stream(files).anyMatch(File::isDirectory);
    }

    private void showLoadingIndicator() {
        if (loadingIndicator == null) {
            loadingIndicator = new ProgressIndicator();
            loadingIndicator.setMaxSize(50, 50);
            StackPane overlay = new StackPane(loadingIndicator);
            overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3);");
            overlay.setAlignment(Pos.CENTER);

            // メインペインの上にオーバーレイを表示
            AnchorPane.setTopAnchor(overlay, 0.0);
            AnchorPane.setBottomAnchor(overlay, 0.0);
            AnchorPane.setLeftAnchor(overlay, 0.0);
            AnchorPane.setRightAnchor(overlay, 0.0);

            // ルートペインにオーバーレイを追加
            Platform.runLater(() -> {
                AnchorPane root = (AnchorPane) codeTabPane.getScene().getRoot();
                root.getChildren().add(overlay);
            });
        }
    }

    private void hideLoadingIndicator() {
        Platform.runLater(() -> {
            if (loadingIndicator != null) {
                AnchorPane root = (AnchorPane) codeTabPane.getScene().getRoot();
                root.getChildren().removeIf(node -> node instanceof StackPane &&
                        ((StackPane) node).getChildren().contains(loadingIndicator));
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
            AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
            CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
            codeArea.replaceText(content);

            markTabAsModified(selectedTab, false);
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

    private void updateModel(Tab tab, String content) {
        if (umlController.isJavaSelected()) {
            Optional<CodeFile> javaFile = findJavaFileByTab(tab);
            javaFile.ifPresent(file -> javaModel.updateCodeFile(file, content));
        } else {
            Optional<CppFile> cppFile = findFileByTab(tab);
            cppFile.ifPresent(file -> cppModel.updateCode(file, content));
        }
    }

    private Optional<CodeFile> findJavaFileByTab(Tab tab) {
        return javaFileTabList.stream()
                .filter(pair -> pair.getValue().equals(tab))
                .map(Pair::getKey)
                .findFirst();
    }

    private void setupCodeAreaChangeListener(CodeArea codeArea, Tab tab) {
        // テキスト変更時のリスナー
        codeArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                markTabAsModified(tab, true);
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
                markTabAsModified(selectedTab, false);

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

    private Optional<CppFile> findFileByTab(Tab tab) {
        // C++ファイルを探す
        for (Pair<CppFile, Tab> pair : cppFileTabList) {
            if (pair.getValue().equals(tab)) {
                return Optional.of(pair.getKey());
            }
        }
        return Optional.empty();
    }

    @FXML
    private void importFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Files");

        // 言語選択に基づいてフィルターを設定
        if (umlController.isJavaSelected()) {
            fileChooser.getExtensionFilters().setAll(
                    new FileChooser.ExtensionFilter("Java Files", "*.java"));
        } else if (umlController.isCppSelected()) {
            fileChooser.getExtensionFilters().setAll(
                    new FileChooser.ExtensionFilter("C++ Files", "*.cpp", "*.h"));
        }

        List<File> files = fileChooser.showOpenMultipleDialog(codeTabPane.getScene().getWindow());
        if (files != null) {
            for (File file : files) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    Platform.runLater(() -> importSingleFile(file, content));
                } catch (IOException e) {
                    Platform.runLater(() -> showError("Failed to import: " + file.getName()));
                }
            }
        }
    }

    private void importSingleFile(File file, String content) {
        String fileName = file.getName();
        TreeItem<String> fileItem = new TreeItem<>(fileName);
        rootItem.getChildren().add(fileItem);

        if (fileName.endsWith(".java")) {
            try {
                CodeFile javaFile = new CodeFile(fileName);
                javaFile.updateCode(content);
                javaModel.addNewFile(javaFile);

                Tab tab = createCodeTab(javaFile);
                if (tab != null) {
                    tabPathMap.put(tab, file.toPath());
                    if (!codeTabPane.getTabs().contains(tab)) {
                        codeTabPane.getTabs().add(tab);
                    }
                }
            } catch (Exception e) {
                showError("Error importing Java file: " + fileName);
            }
        } else if (fileName.endsWith(".h") || fileName.endsWith(".cpp")) {
            importCppFile(file, content);
        }
    }

    private void importCppFile(File file, String content) {
        if (file.getName().endsWith(".h")) {
            String baseName = file.getName().replace(".h", "");
            cppModel.addNewFile(file.getName());
            CppFile headerFile = cppModel.findHeaderFile(baseName);
            if (headerFile != null) {
                headerFile.updateCode(content);
                Tab tab = createCppCodeTab(headerFile);
                if (tab != null) {
                    tabPathMap.put(tab, file.toPath());
                    if (!codeTabPane.getTabs().contains(tab)) {
                        codeTabPane.getTabs().add(tab);
                    }
                }
            }
        } else if (file.getName().endsWith(".cpp")) {
            String baseName = file.getName().replace(".cpp", "");
            CppFile implFile = cppModel.findImplFile(baseName);
            if (implFile == null) {
                cppModel.addNewFile(baseName + ".h");
                implFile = cppModel.findImplFile(baseName);
            }
            if (implFile != null) {
                implFile.updateCode(content);
                Tab tab = createCppCodeTab(implFile);
                if (tab != null) {
                    tabPathMap.put(tab, file.toPath());
                    if (!codeTabPane.getTabs().contains(tab)) {
                        codeTabPane.getTabs().add(tab);
                    }
                }
            }
        }
    }

    private void handleFileSelection(TreeItem<String> item) {
        String fileName = item.getValue();
        Optional<Tab> existingTab = findTabByName(fileName);

        if (existingTab.isPresent()) {
            codeTabPane.getSelectionModel().select(existingTab.get());
        } else if (fileName.endsWith(".h") || fileName.endsWith(".cpp")) {
            openCppFile(fileName);
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

    private Optional<Tab> findTabByName(String fileName) {
        return codeTabPane.getTabs().stream()
                .filter(tab -> getTabBaseText(tab).equals(fileName)) // タブ名から*を除いて比較
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
            Optional<Tab> existingTab = findTab(file.getID());

            if (existingTab.isPresent()) {
                Tab targetTab = existingTab.get();
                updateTabTitle(targetTab, file);
            } else {
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
        Platform.runLater(() -> {
            // C++のファイルタブを削除
            cppFileTabList.removeIf(pair -> {
                CppFile file = pair.getKey();
                Tab tab = pair.getValue();
                boolean shouldRemove = file.getFileName().equals(className + ".h") ||
                        file.getFileName().equals(className + ".cpp");
                if (shouldRemove) {
                    codeTabPane.getTabs().remove(tab);
                }
                return shouldRemove;
            });
        });
    }

    private void updateTabTitle(Tab tab, CppFile file) {
        String fileName = file.getFileName();
        boolean wasModified = tab.getText().endsWith("*");
        tab.setText(fileName); // 基本名を設定
        if (wasModified) {
            markTabAsModified(tab, true); // 変更状態を維持
        }

        // ツールチップの設定
        String extension = file.isHeader() ? ".h" : ".cpp";
        String baseName = fileName.replace(extension, "");
        tab.setTooltip(new Tooltip("Class: " + baseName + "\nType: " +
                (file.isHeader() ? "Header File" : "Implementation File")));
    }

    private boolean hasUnsavedChanges() {
        return codeTabPane.getTabs().stream()
                .anyMatch(tab -> tab.getText().endsWith("*")); // マークの有無でチェック
    }

    private Optional<Tab> findTab(UUID fileId) {
        // C++ファイル用のタブを探す
        Optional<Pair<CppFile, Tab>> cppPair = cppFileTabList.stream()
                .filter(pair -> pair.getKey().getID().equals(fileId))
                .findFirst();
        if (cppPair.isPresent()) {
            return Optional.of(cppPair.get().getValue());
        }

        // Javaファイル用のタブを探す
        return javaFileTabList.stream()
                .filter(pair -> pair.getKey().getID().equals(fileId))
                .map(Pair::getValue)
                .findFirst();
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
        codeArea.setOnKeyTyped(event -> updateCodeFile());
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
        Optional<Tab> existingTab = findTab(file.getID());
        if (existingTab.isPresent()) {
            return existingTab.get();
        }

        CodeArea codeArea = createCodeArea(file);
        Tab tab = new Tab(file.getFileName());
        tab.setContent(createAnchorPane(codeArea));
        tab.setClosable(false);

        setupCodeAreaChangeListener(codeArea, tab);

        setupTabContent(tab, file);
        cppFileTabList.add(new Pair<>(file, tab));
        return tab;
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

        // キー入力イベントを使用してコード更新を行う
        codeArea.setOnKeyTyped(event -> updateCppCodeFile());

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

    // C++用のコード更新メソッド
    private void updateCppCodeFile() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            Optional<Pair<CppFile, Tab>> fileTabPair = cppFileTabList.stream()
                    .filter(pair -> pair.getValue().equals(selectedTab))
                    .findFirst();

            if (fileTabPair.isPresent()) {
                CppFile targetCodeFile = fileTabPair.get().getKey();
                System.out.println(" target C++" + fileTabPair.get().getKey().getCode());
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);

                // コード更新
                String code = codeArea.getText();
                System.out.println("DEBUG: Updating code for " + targetCodeFile.getFileName());

                cppModel.updateCodeFile(targetCodeFile, code);
            }
        });
    }
}