package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.CppFile;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.common.FileChangeListener;
import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.CppModel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
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
import javafx.util.Duration;
import javafx.util.Pair;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Arrays;

public class CodeController {
    @FXML
    private TabPane codeTabPane;
    @FXML
    private TreeView<String> fileTreeView;
    private Map<Tab, Boolean> tabModificationStatus = new HashMap<>();
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
        System.out.println("UmlController set in CodeController"); // デバッグ用
    }

    @FXML
    private void importDirectory() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Import Project Directory");

        File selectedDir = dirChooser.showDialog(codeTabPane.getScene().getWindow());
        if (selectedDir != null) {
            importProjectDirectory(selectedDir);
        }
    }

    private void importProjectDirectory(File directory) {
        // ルートディレクトリのTreeItem作成
        TreeItem<String> projectRoot = new TreeItem<>(directory.getName());
        rootItem.getChildren().add(projectRoot);
        directoryNodes.put(directory.getPath(), projectRoot);

        // ディレクトリを再帰的に処理
        processDirectory(directory, projectRoot);
    }

    private void processDirectory(File directory, TreeItem<String> parentItem) {
        File[] files = directory.listFiles();
        if (files == null)
            return;

        // ディレクトリの処理
        Arrays.stream(files)
                .filter(File::isDirectory)
                .sorted()
                .forEach(dir -> {
                    // ディレクトリ名の重複チェック
                    if (parentItem.getChildren().stream()
                            .noneMatch(item -> item.getValue().equals(dir.getName()))) {
                        TreeItem<String> dirItem = new TreeItem<>(dir.getName());
                        Platform.runLater(() -> parentItem.getChildren().add(dirItem));
                        directoryNodes.put(dir.getPath(), dirItem);
                        processDirectory(dir, dirItem);
                    }
                });

        // ヘッダーファイルをリストアップ
        Map<String, File> headerFiles = Arrays.stream(files)
                .filter(File::isFile)
                .filter(f -> f.getName().endsWith(".h"))
                .collect(Collectors.toMap(
                        f -> f.getName().replace(".h", ""),
                        f -> f,
                        (existing, replacement) -> existing // 重複する場合は最初のものを保持
                ));

        // CPPファイルをリストアップ
        Map<String, File> cppFiles = Arrays.stream(files)
                .filter(File::isFile)
                .filter(f -> f.getName().endsWith(".cpp"))
                .collect(Collectors.toMap(
                        f -> f.getName().replace(".cpp", ""),
                        f -> f,
                        (existing, replacement) -> existing // 重複する場合は最初のものを保持
                ));

        // ファイルの処理
        Set<String> processedBaseNames = new HashSet<>();

        // ヘッダーファイルの処理
        headerFiles.forEach((baseName, headerFile) -> {
            try {
                // ツリーアイテムの重複チェック
                if (parentItem.getChildren().stream()
                        .noneMatch(item -> item.getValue().equals(headerFile.getName()))) {
                    String headerContent = new String(Files.readAllBytes(headerFile.toPath()));
                    TreeItem<String> headerItem = new TreeItem<>(headerFile.getName());
                    Platform.runLater(() -> parentItem.getChildren().add(headerItem));

                    // ヘッダーファイルの作成と内容の更新
                    CppFile headerCppFile = new CppFile(headerFile.getName(), true);
                    cppModel.addNewFile(headerFile.getName());

                    CppFile modelHeaderFile = cppModel.findHeaderFile(baseName);
                    if (modelHeaderFile != null) {
                        modelHeaderFile.updateCode(headerContent);
                        Tab headerTab = createCppCodeTab(modelHeaderFile);
                        tabPathMap.put(headerTab, headerFile.toPath());
                        Platform.runLater(() -> {
                            // codeTabPane.getTabs().add(headerTab);
                            updateTabStatus(headerTab, false);
                        });
                    }
                }

                // 対応するCPPファイルの処理
                File cppFile = cppFiles.get(baseName);
                if (cppFile != null && parentItem.getChildren().stream()
                        .noneMatch(item -> item.getValue().equals(cppFile.getName()))) {
                    String cppContent = new String(Files.readAllBytes(cppFile.toPath()));
                    TreeItem<String> cppItem = new TreeItem<>(cppFile.getName());
                    Platform.runLater(() -> parentItem.getChildren().add(cppItem));

                    CppFile modelImplFile = cppModel.findImplFile(baseName);
                    if (modelImplFile != null) {
                        modelImplFile.updateCode(cppContent);
                        Tab implTab = createCppCodeTab(modelImplFile);
                        tabPathMap.put(implTab, cppFile.toPath());
                        Platform.runLater(() -> {
                            // codeTabPane.getTabs().add(implTab);
                            updateTabStatus(implTab, false);
                        });
                    }
                    processedBaseNames.add(baseName);
                }
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> showError("Failed to import file: " + headerFile.getName()));
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

    private void updateFileContent(CppFile file, String content) {
        if (file.isHeader()) {
            file.updateCode(content);
        } else {
            // 実装ファイルの場合、必要に応じてインクルード文を追加
            String baseName = file.getFileName().replace(".cpp", "");
            String includeStatement = "#include \"" + baseName + ".h\"";
            if (!content.contains(includeStatement)) {
                content = includeStatement + "\n\n" + content;
            }
            file.updateCode(content);
        }
    }

    // ファイル内容の比較を行うヘルパーメソッド
    private boolean isContentModified(String currentContent, Path filePath) {
        try {
            if (Files.exists(filePath)) {
                String fileContent = Files.readString(filePath);
                return !currentContent.equals(fileContent);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true; // ファイルが存在しない場合は変更ありとみなす
    }

    // ファイル名からタブを検索するヘルパーメソッド
    private Optional<Tab> findTabByFileName(String fileName) {
        return codeTabPane.getTabs().stream()
                .filter(tab -> tab.getText().equals(fileName) || tab.getText().equals(fileName + "*"))
                .findFirst();
    }

    private void importHeaderFile(File file, String content) {
        cppModel.addNewFile(file.getName());
        CppFile headerFile = cppModel.findHeaderFile(file.getName().replace(".h", ""));
        if (headerFile != null) {
            headerFile.updateCode(content);

            // タブを作成してパスを保存
            Tab tab = createCppCodeTab(headerFile);
            if (tab != null) {
                tabPathMap.put(tab, file.toPath());
                cppFileTabList.add(new Pair<>(headerFile, tab));
                Platform.runLater(() -> {
                    if (!codeTabPane.getTabs().contains(tab)) {
                        codeTabPane.getTabs().add(tab);
                    }
                });
            }
        }
    }

    private void importImplFile(File file, String content) {
        String baseName = file.getName().replace(".cpp", "");
        CppFile implFile = cppModel.findImplFile(baseName);
        if (implFile == null) {
            cppModel.addNewFile(baseName + ".h");
            implFile = cppModel.findImplFile(baseName);
        }

        if (implFile != null) {
            implFile.updateCode(content);

            // タブを作成してパスを保存
            Tab tab = createCppCodeTab(implFile);
            if (tab != null) {
                tabPathMap.put(tab, file.toPath());
                cppFileTabList.add(new Pair<>(implFile, tab));
                Platform.runLater(() -> {
                    if (!codeTabPane.getTabs().contains(tab)) {
                        codeTabPane.getTabs().add(tab);
                    }
                });
            }
        }
    }

    @FXML
    private ScrollBar tabScrollBar;

    private void initializeTabScroll() {
        ScrollPane tabHeaderScrollPane = (ScrollPane) codeTabPane.lookup(".tab-header-area");
        if (tabHeaderScrollPane != null) {
            tabScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
                tabHeaderScrollPane.setHvalue(newVal.doubleValue() / 100.0);
            });

            tabHeaderScrollPane.hvalueProperty().addListener((obs, oldVal, newVal) -> {
                tabScrollBar.setValue(newVal.doubleValue() * 100);
            });
        }
    }

    @FXML
    private void initialize() {
        // ファイルツリーの初期化
        rootItem = new TreeItem<>("Project Files");
        rootItem.setExpanded(true);
        fileTreeView.setRoot(rootItem);

        // ツリーの選択イベント
        fileTreeView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        handleFileSelection(newValue);
                    }
                });
        // タブペインの設定を追加
        Platform.runLater(this::configureTabPane);

        // スクロール用のイベントハンドラを設定
        codeTabPane.addEventFilter(ScrollEvent.ANY, event -> {
            if (event.isShortcutDown()) {
                Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
                if (selectedTab != null) {
                    int currentIndex = codeTabPane.getTabs().indexOf(selectedTab);
                    if (event.getDeltaY() > 0 && currentIndex > 0) {
                        codeTabPane.getSelectionModel().select(currentIndex - 1);
                    } else if (event.getDeltaY() < 0 && currentIndex < codeTabPane.getTabs().size() - 1) {
                        codeTabPane.getSelectionModel().select(currentIndex + 1);
                    }
                    event.consume();
                }
            }
        });

        // タブの選択時に自動スクロール
        codeTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                Platform.runLater(() -> {
                    ScrollPane scrollPane = (ScrollPane) codeTabPane.lookup(".tab-header-area");
                    if (scrollPane != null) {
                        Node tabNode = newTab.getGraphic();
                        if (tabNode != null) {
                            Bounds bounds = tabNode.localToScene(tabNode.getBoundsInLocal());
                            double scrollValue = (bounds.getMinX() - 50) /
                                    scrollPane.getContent().getBoundsInLocal().getWidth();
                            scrollPane.setHvalue(Math.min(Math.max(scrollValue, 0), 1));
                        }
                    }
                });
            }
        });

        // Platform.runLater を使用してシーンが確実に存在する状態でキーボードショートカットを設定
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

        // モデルの初期化
        javaModel.setCodeController(this);
        cppModel.setCodeController(this);
    }

    @FXML
    public void saveCurrentFile() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null)
            return;

        try {
            Optional<CppFile> fileOpt = findFileByTab(selectedTab);
            if (fileOpt.isPresent()) {
                CppFile cppFile = fileOpt.get();

                // パスを取得
                Path filePath = tabPathMap.get(selectedTab);
                if (filePath == null) {
                    // パスが未設定の場合は名前を付けて保存を実行
                    saveFileAs();
                    return;
                }

                // 現在のコードを取得
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                String content = codeArea.getText();

                // ファイルを保存
                Files.writeString(filePath, content);

                // モデルを更新
                cppModel.updateCodeFile(cppFile, content);

                // タブの状態を更新
                updateTabStatus(selectedTab, false);

                showSaveStatus("File saved: " + filePath.getFileName());
            }
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to save file: " + e.getMessage());
        }
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
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("C++ Header Files", "*.h"),
                new FileChooser.ExtensionFilter("C++ Source Files", "*.cpp"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fileChooser.showSaveDialog(codeTabPane.getScene().getWindow());
        if (file != null) {
            try {
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                Files.write(file.toPath(), codeArea.getText().getBytes());

                // タブの情報を更新
                selectedTab.setText(file.getName());
                tabPathMap.put(selectedTab, file.toPath());
                updateTabStatus(selectedTab, false);

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

    private boolean hasUnsavedChanges() {
        return tabModificationStatus.values().stream().anyMatch(modified -> modified);
    }

    private void saveFile(CppFile file, String content) throws IOException {
        // ファイルパスを取得または作成
        String basePath = System.getProperty("user.dir");
        String filePath = basePath + File.separator + file.getFileName();

        // ディレクトリが存在しない場合は作成
        new File(filePath).getParentFile().mkdirs();

        // ファイルに書き込み
        Files.write(Paths.get(filePath), content.getBytes());

        // モデルを更新
        if (file.isHeader()) {
            cppModel.updateCodeFile(file, content);
        } else {
            cppModel.updateCodeFile(file, content);
        }
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

    private void updateTabStatus(Tab tab, boolean modified) {
        Platform.runLater(() -> {
            String currentTitle = tab.getText();
            String baseTitle = currentTitle.endsWith("*") ? currentTitle.substring(0, currentTitle.length() - 1)
                    : currentTitle;

            if (modified) {
                tab.setText(baseTitle + "*");
            } else {
                tab.setText(baseTitle);
            }
        });
    }

    // コードエリアの変更を監視するメソッド
    private void setupCodeAreaChangeListener(CodeArea codeArea, Tab tab) {
        // 初期テキストを保存
        String initialText = codeArea.getText();
        Path filePath = tabPathMap.get(tab);

        codeArea.textProperty().addListener((observable, oldValue, newValue) -> {
            // 初期テキストとの比較は行わない（初期ロード時に*が付くのを防ぐ）
            if (!Objects.equals(oldValue, newValue) && !Objects.equals(initialText, newValue)) {
                try {
                    if (filePath != null && Files.exists(filePath)) {
                        String fileContent = Files.readString(filePath);
                        boolean isModified = !newValue.equals(fileContent);
                        updateTabStatus(tab, isModified);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    updateTabStatus(tab, true);
                }
            }
        });
    }

    @FXML
    private void importFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import C++ Files");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("C++ Files", "*.cpp", "*.h"));

        List<File> files = fileChooser.showOpenMultipleDialog(codeTabPane.getScene().getWindow());

        if (files != null) {
            for (File file : files) {
                importFile(file);
            }
        }
    }

    private void importFile(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            String fileName = file.getName();

            // ファイルツリーに追加
            TreeItem<String> fileItem = new TreeItem<>(fileName);
            rootItem.getChildren().add(fileItem);

            // .hファイルの場合
            if (fileName.endsWith(".h")) {
                CppFile headerFile = new CppFile(fileName, true);
                headerFile.updateCode(content);
                cppModel.addNewFile(fileName);

                // タブを作成してパスを保存
                Tab tab = createCppCodeTab(headerFile);
                tabPathMap.put(tab, file.toPath());
            }
            // .cppファイルの場合
            else if (fileName.endsWith(".cpp")) {
                String baseName = fileName.replace(".cpp", "");
                CppFile implFile = new CppFile(fileName, false);
                implFile.updateCode(content);

                // タブを作成してパスを保存
                Tab tab = createCppCodeTab(implFile);
                tabPathMap.put(tab, file.toPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to import file: " + file.getName());
        }
    }

    private void handleFileSelection(TreeItem<String> item) {
        String fileName = item.getValue();
        // タブが既に存在するかチェック
        for (Tab tab : codeTabPane.getTabs()) {
            if (tab.getText().equals(fileName)) {
                codeTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        // 新しいタブを作成
        if (fileName.endsWith(".h") || fileName.endsWith(".cpp")) {
            CppFile file = fileName.endsWith(".h") ? cppModel.findHeaderFile(fileName.replace(".h", ""))
                    : cppModel.findImplFile(fileName.replace(".cpp", ""));

            if (file != null) {
                updateCodeTab(file);
            }
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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

                AnchorPane anchorPane = (AnchorPane) targetTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                // コードが実際に変更された場合のみ更新
                if (!codeArea.getText().equals(file.getCode())) {
                    // エディタの状態を保存
                    int caretPosition = codeArea.getCaretPosition();
                    IndexRange selection = codeArea.getSelection();
                    double scrollY = codeArea.estimatedScrollYProperty().getValue();

                    Platform.runLater(() -> {
                        try {
                            codeArea.replaceText(file.getCode());

                            // カーソル位置を復元
                            int newPosition = Math.min(caretPosition, codeArea.getLength());
                            codeArea.moveTo(newPosition);

                            // 選択範囲を復元
                            if (selection.getLength() > 0) {
                                int newStart = Math.min(selection.getStart(), codeArea.getLength());
                                int newEnd = Math.min(selection.getEnd(), codeArea.getLength());
                                codeArea.selectRange(newStart, newEnd);
                            }

                            // スクロール位置を復元
                            codeArea.estimatedScrollYProperty().setValue(scrollY);
                            codeArea.requestFollowCaret();
                        } catch (Exception e) {
                            System.err.println("Error restoring editor state: " + e.getMessage());
                        }
                    });
                }
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

    private void configureTabPane() {
        // タブの最小幅と最大幅を設定
        codeTabPane.setTabMinWidth(80);
        codeTabPane.setTabMaxWidth(150);

        // スクロールボタンを表示するしきい値を設定
        codeTabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);

        // タブヘッダー領域のサイズを制限
        StackPane headerArea = (StackPane) codeTabPane.lookup(".tab-header-area");
        if (headerArea != null) {
            headerArea.setMaxWidth(Control.USE_PREF_SIZE);
            headerArea.setPrefWidth(488.0); // 現在のヘッダー幅に合わせる
        }

        // コントロールボタンを強制的に表示
        Node controlButtons = codeTabPane.lookup(".control-buttons-tab");
        if (controlButtons != null) {
            controlButtons.setVisible(true);
            controlButtons.setManaged(true);
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
        String extension = file.isHeader() ? ".h" : ".cpp";
        String baseName = fileName.replace(extension, "");
        tab.setText(fileName);

        // ツールチップにフルパスやファイルタイプの情報を表示
        tab.setTooltip(new Tooltip("Class: " + baseName + "\nType: " +
                (file.isHeader() ? "Header File" : "Implementation File")));
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

    // C++用の新しいタブ作成メソッド
    private Tab createCppCodeTab(CppFile cppFile) {
        // 既存のタブをチェック
        for (Pair<CppFile, Tab> pair : cppFileTabList) {
            if (pair.getKey().getID().equals(cppFile.getID())) {
                return pair.getValue();
            }
        }

        CodeArea codeArea = new CodeArea() {
            @Override
            public void replaceText(int start, int end, String text) {
                super.replaceText(start, end, text);
            }

            @Override
            public void replaceSelection(String text) {
                super.replaceSelection(text);
            }
        };
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        codeArea.setOnKeyTyped(event -> {
            int caretPosition = codeArea.getCaretPosition();
            IndexRange selection = codeArea.getSelection();
            double scrollY = codeArea.estimatedScrollYProperty().getValue();

            updateCppCodeFile();

            Platform.runLater(() -> {
                try {
                    // カーソル位置を復元
                    int newPosition = Math.min(caretPosition, codeArea.getLength());
                    codeArea.moveTo(newPosition);

                    // 選択範囲を復元
                    if (selection.getLength() > 0) {
                        int newStart = Math.min(selection.getStart(), codeArea.getLength());
                        int newEnd = Math.min(selection.getEnd(), codeArea.getLength());
                        codeArea.selectRange(newStart, newEnd);
                    }

                    // スクロール位置を復元
                    codeArea.estimatedScrollYProperty().setValue(scrollY);
                    codeArea.requestFollowCaret();
                } catch (Exception e) {
                    System.err.println("Error restoring editor state: " + e.getMessage());
                }
            });
        });

        codeArea.replaceText(cppFile.getCode());

        AnchorPane codeAnchor = new AnchorPane(codeArea);
        AnchorPane.setBottomAnchor(codeArea, 0.0);
        AnchorPane.setTopAnchor(codeArea, 0.0);
        AnchorPane.setLeftAnchor(codeArea, 0.0);
        AnchorPane.setRightAnchor(codeArea, 0.0);

        Tab codeTab = new Tab();
        codeTab.setText(cppFile.getFileName());
        codeTab.setContent(codeAnchor);
        updateTabTitle(codeTab, cppFile);
        codeTab.setClosable(false);

        cppFile.addChangeListener(new CppFile.FileChangeListener() {
            @Override
            public void onFileChanged(CppFile file) {
                // カーソル位置等の保持は updateCodeTab メソッドで行う
                Platform.runLater(() -> updateCodeTab(file));
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                Platform.runLater(() -> {
                    codeTab.setText(newName);
                    updateTabTitle(codeTab, cppFile);
                });
            }
        });

        codeTab.setStyle("-fx-max-width: 200px;");

        // タブが長い場合にツールチップを表示
        Tooltip tooltip = new Tooltip(cppFile.getFileName());
        codeTab.setTooltip(tooltip);

        cppFileTabList.add(new Pair<>(cppFile, codeTab));
        setupCodeAreaChangeListener(codeArea, codeTab);
        return codeTab;
    }

    // ステータス表示用のメソッド
    private void showSaveStatus(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Save Status");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();

            // 2秒後に自動で閉じる
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), evt -> alert.close()));
            timeline.play();
        });
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

        for (Pair<CppFile, Tab> fileTabPair : cppFileTabList) {
            if (fileTabPair.getValue().equals(selectedTab)) {
                CppFile targetCodeFile = fileTabPair.getKey();
                System.out.println(" target C++" + fileTabPair.getKey().getCode());
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);

                // カーソル位置、選択範囲、スクロール位置を保存
                int caretPosition = codeArea.getCaretPosition();
                IndexRange selection = codeArea.getSelection();
                double scrollY = codeArea.estimatedScrollYProperty().getValue();

                // コード更新
                String code = codeArea.getText();
                System.out.println("DEBUG: Updating code for " + targetCodeFile.getFileName());

                // UMLコントローラーに直接通知して関係抽出を行う
                // if (umlController != null) {
                // // umlController.handleCodeUpdate(targetCodeFile);
                // }

                cppModel.updateCodeFile(targetCodeFile, code);

                Platform.runLater(() -> {
                    try {
                        // カーソル位置を復元（範囲チェック付き）
                        int newPosition = Math.min(caretPosition, codeArea.getLength());
                        codeArea.moveTo(newPosition);

                        // 選択範囲を復元
                        if (selection.getLength() > 0) {
                            int newStart = Math.min(selection.getStart(), codeArea.getLength());
                            int newEnd = Math.min(selection.getEnd(), codeArea.getLength());
                            codeArea.selectRange(newStart, newEnd);
                        }

                        // スクロール位置を復元
                        codeArea.estimatedScrollYProperty().setValue(scrollY);
                        codeArea.requestFollowCaret();
                    } catch (Exception e) {
                        System.err.println("Error updating caret position: " + e.getMessage());
                    }
                });
                return;
            }
        }
    }
}