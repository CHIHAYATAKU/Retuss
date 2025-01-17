package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.RetussWindow;
import io.github.morichan.retuss.drawer.*;
import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.CppFile;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.UmlModel;
import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.Interaction;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class UmlController implements CppModel.ModelChangeListener {
    private CodeController codeController;
    @FXML
    private TabPane codeTabPane;
    @FXML
    private Tab classDiagramTab;
    @FXML
    private Button classBtn;
    @FXML
    private Button attributeBtn;
    @FXML
    private Button operationBtn;
    @FXML
    private Button relationshipBtn;
    @FXML
    private Button deleteBtn;
    @FXML
    private WebView classDiagramWebView;
    @FXML
    private TextArea plantUmlCodeArea;
    @FXML
    private StackPane diagramContainer;
    @FXML
    private Button toggleViewBtn;

    private boolean showingDiagram = true;
    @FXML
    private Tab sequenceDiagramTab;
    @FXML
    private TabPane tabPaneInSequenceTab;
    @FXML
    private ComboBox<String> languageSelector;
    @FXML
    private Slider scaleSlider;
    @FXML
    private Slider scaleSlider2;

    private String currentLanguage = "Java";

    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 100;
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private JavaClassDiagramDrawer javaClassDiagramDrawer;
    private CppClassDiagramDrawer cppClassDiagramDrawer;
    private SequenceDiagramDrawer sequenceDiagramDrawer;
    private UmlModel umlModel;
    // private CppSequenceDiagramDrawer cppSequenceDiagramDrawer;
    private List<Pair<CodeFile, Tab>> fileSdTabList = new ArrayList<>();

    private class DiagramState {
        private double scrollX;
        private double scrollY;

        public void saveState(WebView webView) {
            // JavaScriptを使用してスクロール位置を取得
            Object x = webView.getEngine().executeScript("document.documentElement.scrollLeft");
            Object y = webView.getEngine().executeScript("document.documentElement.scrollTop");
            this.scrollX = x instanceof Number ? ((Number) x).doubleValue() : 0;
            this.scrollY = y instanceof Number ? ((Number) y).doubleValue() : 0;
        }

        public void restoreState(WebView webView) {
            // 描画完了後にスクロール位置を復元
            webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    webView.getEngine().executeScript(
                            String.format("window.scrollTo(%f, %f);", scrollX, scrollY));
                }
            });
        }
    }

    // マウスの初期位置
    private double initialMouseX = 0;
    private double initialMouseY = 0;

    // 初期スクロール位置
    private double currentScrollX = 0;
    private double currentScrollY = 0;

    private final double scrollSpeedFactor = 2.0; // 倍率を変えることで速さを調整

    public void enableHoverSlide(WebView webView) {
        // マウスが押されたときの位置を記録
        webView.setOnMousePressed(event -> {
            initialMouseX = event.getSceneX();
            initialMouseY = event.getSceneY();

            // 現在のスクロール位置を取得
            currentScrollX = (double) webView.getEngine().executeScript(
                    "document.documentElement.scrollLeft || document.body.scrollLeft");
            currentScrollY = (double) webView.getEngine().executeScript(
                    "document.documentElement.scrollTop || document.body.scrollTop");

            event.consume();
        });

        // マウスをドラッグしたときにスクロールを移動
        webView.setOnMouseDragged(event -> {
            double deltaX = (initialMouseX - event.getSceneX()) * scrollSpeedFactor; // スクロール速度を調整
            double deltaY = (initialMouseY - event.getSceneY()) * scrollSpeedFactor; // スクロール速度を調整

            // スクロール位置を更新
            currentScrollX += deltaX;
            currentScrollY += deltaY;

            try {
                webView.getEngine().executeScript(
                        String.format("window.scrollTo(%f, %f);", currentScrollX, currentScrollY));
            } catch (Exception e) {
                System.err.println("Error during hover slide: " + e.getMessage());
            }

            // 次のドラッグ操作に備えて位置を更新
            initialMouseX = event.getSceneX();
            initialMouseY = event.getSceneY();

            event.consume();
        });
    }

    @FXML
    public void setZoom(double zoomFactor) {
        if (classDiagramWebView != null) {
            classDiagramWebView.setZoom(zoomFactor); // WebViewのズーム機能を利用
            System.out.println("Zoom set to: " + zoomFactor);
        }
    }

    private DiagramState classDiagramState = new DiagramState();

    @Override
    public void onModelChanged() {
        // 全体更新が必要な場合用
    }

    @Override
    public void onFileAdded(CppFile file) {
        if (file.isHeader()) {
            Platform.runLater(() -> {
                updateDiagram(file);
            });
        }
    }

    @Override
    public void onFileUpdated(CppFile file) {
        if (file.isHeader()) {
            Platform.runLater(() -> {
                updateDiagram(file);
            });
        }
    }

    @Override
    public void onFileDeleted(String className) {
        Platform.runLater(() -> {
            onClassDeleted(className);
        });
    }

    @Override
    public void onFileRenamed(String oldName, String newName) {
        Platform.runLater(() -> {
            if (isCppSelected()) {
                cppClassDiagramDrawer.clearCache();
                cppClassDiagramDrawer.draw(codeController.getSelectedClassName());
            }
        });
    }

    /**
     * <p>
     * JavaFXにおけるデフォルトコンストラクタ
     * </p>
     *
     * <p>
     * Javaにおける通常のコンストラクタ（ {@code Controller()} メソッド）は使えないため、
     * {@link RetussWindow} クラス内でのFXMLファイル読込み時に {@link #initialize()}
     * メソッドを呼び出す仕様になっています。
     * </p>
     */
    @FXML
    private void initialize() {
        javaModel.setUmlController(this);
        languageSelector.setValue("Java");
        currentLanguage = "Java";
        javaClassDiagramDrawer = new JavaClassDiagramDrawer(classDiagramWebView);
        cppClassDiagramDrawer = new CppClassDiagramDrawer(classDiagramWebView);
        sequenceDiagramDrawer = new SequenceDiagramDrawer(tabPaneInSequenceTab);
        umlModel = UmlModel.getInstance();
        umlModel.addChangeListener(newPlantUml -> {
            Platform.runLater(() -> {
                plantUmlCodeArea.setText(newPlantUml);
            });
        });
        scaleSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            setZoom(newVal.doubleValue());
        });
        scaleSlider2.valueProperty().addListener((obs, oldVal, newVal) -> {
            setZoom(newVal.doubleValue());
        });

        enableHoverSlide(classDiagramWebView);

        classDiagramWebView.setOnMouseEntered(event -> classDiagramWebView.setCursor(Cursor.HAND));
        classDiagramWebView.setOnMouseExited(event -> classDiagramWebView.setCursor(Cursor.DEFAULT));

        // TextAreaの設定
        plantUmlCodeArea.setEditable(true);
        plantUmlCodeArea.setWrapText(false);

        // Toggle ボタンのテキスト設定
        toggleViewBtn.setText("Show Code");
        // cppSequenceDiagramDrawer = new
        // CppSequenceDiagramDrawer(tabPaneInSequenceTab);
    }

    @FXML
    private void handleLanguageSelection(ActionEvent event) {
        String selectedLanguage = languageSelector.getValue();
        if (selectedLanguage != null) {
            currentLanguage = selectedLanguage;
            System.out.println("Selected language: " + selectedLanguage);

            // 図の更新
            if ("Java".equals(currentLanguage)) {
                javaClassDiagramDrawer.draw();
                javaClassDiagramDrawer.draw();
            } else if ("C++".equals(currentLanguage)) {
                cppClassDiagramDrawer.draw(codeController.getSelectedClassName());
            }
        }
    }

    @FXML
    private void toggleDiagramView() {
        showingDiagram = !showingDiagram;
        classDiagramWebView.setVisible(showingDiagram);
        plantUmlCodeArea.setVisible(!showingDiagram);
        toggleViewBtn.setText(showingDiagram ? "Show Code" : "Show Diagram");
    }

    public void setCodeController(CodeController controller) {
        this.codeController = controller;
        System.out.println("CodeController set in UmlController");
    }

    @FXML
    private void importJavaFile() {

    }

    @FXML
    private void importCppFile() {

    }

    @FXML
    private void selectClassDiagramTab() {

    }

    @FXML
    private void selectSequenceDiagramTab() {

    }

    /**
     * <p>
     * シーケンス図タブのノーマルボタン選択時のシグナルハンドラ</>
     */
    @FXML
    private void normalMessageInSD() {

    }

    /**
     * <p>
     * シーケンス図タブのキャンバスを右クリックメニューにある「メッセージの追加」をクリック時のシグナルハンドラ
     * </p>
     * <p>
     * シーケンス図タブのキャンバスはSequenceDiagramDrawerクラスで動的に生成するため、シグナルハンドラも動的に割り当てている
     * </p>
     * <p>
     * メッセージ作成ダイアログを表示する
     * </p>
     */
    @FXML
    public void showCreateMessageDialog() {
        Tab selectedFileTab = tabPaneInSequenceTab.getSelectionModel().getSelectedItem();
        Tab selectedOperationTab = ((TabPane) selectedFileTab.getContent()).getSelectionModel().getSelectedItem();
        String selectedFileName = selectedFileTab.getText();
        String selectedOperationId = selectedOperationTab.getText();

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/messageDialog.fxml"));
            Parent parent = fxmlLoader.load();
            MessageDialogController messageDialogController = fxmlLoader.getController();
            messageDialogController.initialize(selectedFileName, selectedOperationId);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New Message Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @FXML
    public void showCreateCombinedFragmentDialog() {
        Tab selectedFileTab = tabPaneInSequenceTab.getSelectionModel().getSelectedItem();
        Tab selectedOperationTab = ((TabPane) selectedFileTab.getContent()).getSelectionModel().getSelectedItem();
        String selectedFileName = selectedFileTab.getText();
        String selectedOperationId = selectedOperationTab.getText();

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/createCombinedFragmentDialog.fxml"));
            Parent parent = fxmlLoader.load();
            CombinedFragmentDialogController combinedFragmentController = fxmlLoader.getController();
            combinedFragmentController.initialize(selectedFileName, selectedOperationId);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New Combined Fragment Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @FXML
    private void showDeleteDialogSD() {
        Tab selectedFileTab = tabPaneInSequenceTab.getSelectionModel().getSelectedItem();
        Tab selectedOperationTab = ((TabPane) selectedFileTab.getContent()).getSelectionModel().getSelectedItem();
        String selectedFileName = selectedFileTab.getText();
        String selectedOperationId = selectedOperationTab.getText();

        System.out.println("Selected File Tab: " + selectedFileName);
        System.out.println("Selected Operation Tab: " + selectedOperationId);

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/deleteDialogSD.fxml"));
            Parent parent = fxmlLoader.load();
            DeleteDialogControllerSD deleteDialogControllerSD = fxmlLoader.getController();
            deleteDialogControllerSD.initialize(selectedFileName, selectedOperationId);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("Delete Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @FXML
    public void handleRefreshAll() {
        System.out.println("DEBUG: Starting Reload");

        try {
            Collection<CppFile> headers = cppModel.getHeaderFiles().values();
            System.out.println("DEBUG: Found " + headers.size() + " header files");

            for (CppFile headerFile : headers) {
                System.out.println("DEBUG: Processing " + headerFile.getFileName());
                String currentCode = headerFile.getCode();
                cppModel.updateCode(headerFile, currentCode);
            }

            System.out.println("DEBUG: Updating diagram");
            System.out.println("DEBUG: Refresh completed");

        } catch (Exception e) {
            System.err.println("Refresh failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void showClassDialog() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/classDialog.fxml"));
            Parent parent = fxmlLoader.load();
            ClassDialogController dialogController = fxmlLoader.getController();

            // UmlControllerの参照を設定
            dialogController.setUmlController(this);

            // デバッグ出力
            System.out.println("Opening Class Dialog");
            System.out.println("Java checkbox state: " + isJavaSelected());
            System.out.println("C++ checkbox state: " + isCppSelected());

            Scene scene = new Scene(parent);
            scene.getStylesheets().add(getClass().getResource("/custom-radio.css").toExternalForm());

            Stage stage = new Stage();
            stage.setTitle("New Class Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.err.println("Error showing class dialog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isJavaSelected() {
        return "Java".equals(currentLanguage);
    }

    public boolean isCppSelected() {
        return "C++".equals(currentLanguage);
    }

    @FXML
    private void showAttributeDialog() {
        try {
            System.out.println("Opening Attribute Dialog");
            System.out.println("Java checkbox state: " + isJavaSelected());
            System.out.println("C++ checkbox state: " + isCppSelected());
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/attributeDialog.fxml"));
            Parent parent = fxmlLoader.load();
            AttributeDialogController attrDialogController = fxmlLoader.getController();
            attrDialogController.setUmlController(this);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New Attribute Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @FXML
    private void showOperationDialog() {
        try {
            System.out.println("Opening Operation Dialog");
            System.out.println("Java checkbox state: " + isJavaSelected());
            System.out.println("C++ checkbox state: " + isCppSelected());
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/operationDialog.fxml"));
            Parent parent = fxmlLoader.load();
            OperationDialogController operDialogController = fxmlLoader.getController();
            operDialogController.setUmlController(this);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New Operation Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @FXML
    private void showRelationshipDialog() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("relationshipDialog.fxml"));
            Parent parent = fxmlLoader.load();
            System.out.println("relationshipDialog.fxml loaded!");
            RelationshipDialogController relationShipDialogController = fxmlLoader.getController();
            relationShipDialogController.setUmlController(this);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New Relationship Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.err.println("Error loading FXML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void showDeleteDialogCD() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/deleteDialogCD.fxml"));
            Parent parent = fxmlLoader.load();
            DeleteDialogControllerCD dialogController = fxmlLoader.getController();
            // UmlControllerの参照を設定
            dialogController.setUmlController(this);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("Delete Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void onClassDeleted(String className) {
        Platform.runLater(() -> {
            // クラス図の更新
            if (isCppSelected()) {
                cppClassDiagramDrawer.clearCache();
                cppClassDiagramDrawer.draw(codeController.getSelectedClassName());
            }

            // // シーケンス図のタブを削除
            // if (tabPaneInSequenceTab != null) {
            // tabPaneInSequenceTab.getTabs().removeIf(tab -> tab.getText().equals(className
            // + ".h") ||
            // tab.getText().equals(className + ".cpp"));
            // }
        });
    }

    public void createCppClass(String className) {
        CppHeaderClass headerClass = new CppHeaderClass(className);
        cppModel.addNewFileFromUml(headerClass);
    }

    public void updateDiagram(CppFile cppFile) {
        // ヘッダーファイルの場合、またはヘッダーファイルが存在する実装ファイルの場合に更新
        if (cppFile.isHeader() || findCorrespondingHeaderFile(cppFile) != null) {
            System.out.println("DEBUG: Processing C++ file: " + cppFile.getFileName());
            Platform.runLater(() -> {
                try {
                    // 現在のスクロール位置を保存
                    classDiagramState.saveState(classDiagramWebView);
                    // クラス図の更新（関係抽出を含む）
                    cppClassDiagramDrawer.clearCache(); // キャッシュをクリアして強制的に再描画
                    cppClassDiagramDrawer.draw(codeController.getSelectedClassName());
                    // シーケンス図の更新
                    // updateCppSequenceDiagram(cppFile);
                } catch (Exception e) {
                    System.err.println("Error updating diagrams: " + e.getMessage());
                }
            });
        }
    }

    public void updateDiagram(ICodeFile codeFile) {
        System.out.println("DEBUG: Updating diagram for file: " + codeFile.getFileName());
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < MIN_UPDATE_INTERVAL) {
            return;
        }
        lastUpdateTime = currentTime;

        if (codeFile instanceof CodeFile) {
            // Javaファイルの場合
            System.out.println("DEBUG: Updating Java diagrams");
            CodeFile javaFile = (CodeFile) codeFile;

            Platform.runLater(() -> {
                // 現在のスクロール位置を保存
                classDiagramState.saveState(classDiagramWebView);
                // クラス図の更新
                javaClassDiagramDrawer.draw();

                // スクロール位置を復元
                classDiagramState.restoreState(classDiagramWebView);

                // シーケンス図の更新
                updateJavaSequenceDiagram(javaFile);
            });

            System.out.println("DEBUG: Java diagrams update completed");
        } else if (codeFile instanceof CppFile) {
            // C++ファイルの場合
            CppFile cppFile = (CppFile) codeFile;
            // ヘッダーファイルの場合、またはヘッダーファイルが存在する実装ファイルの場合に更新
            if (cppFile.isHeader() || findCorrespondingHeaderFile(cppFile) != null) {
                System.out.println("DEBUG: Processing C++ file: " + cppFile.getFileName());
                Platform.runLater(() -> {
                    try {
                        // クラス図の更新（関係抽出を含む）
                        cppClassDiagramDrawer.clearCache(); // キャッシュをクリアして強制的に再描画
                        cppClassDiagramDrawer.draw(codeController.getSelectedClassName());
                        // シーケンス図の更新
                        // updateCppSequenceDiagram(cppFile);
                    } catch (Exception e) {
                        System.err.println("Error updating diagrams: " + e.getMessage());
                    }
                });
            }
        }
    }

    private CppFile findCorrespondingHeaderFile(CppFile implFile) {
        if (!implFile.isHeader()) {
            String baseName = implFile.getFileName().replace(".cpp", "");
            return CppModel.getInstance().findHeaderFile(baseName);
        }
        return null;
    }

    private void updateJavaSequenceDiagram(CodeFile codeFile) {
        System.out.println("DEBUG: Updating Java sequence diagram for " + codeFile.getFileName());

        // ファイルタブの探索
        Optional<Tab> fileTabOptional = findFileTab(codeFile);
        Tab fileTab;

        if (fileTabOptional.isPresent()) {
            fileTab = fileTabOptional.get();
            System.out.println("DEBUG: Found existing tab for " + codeFile.getFileName());
        } else {
            fileTab = new Tab(codeFile.getFileName());
            fileTab.setContent(new TabPane());
            fileSdTabList.add(new Pair<>(codeFile, fileTab));
            tabPaneInSequenceTab.getTabs().add(fileTab);
            System.out.println("DEBUG: Created new tab for " + codeFile.getFileName());
        }

        // codeFileにクラス宣言がなければ終了
        if (codeFile.getUmlClassList().isEmpty()) {
            System.out.println("DEBUG: No UML classes found in " + codeFile.getFileName());
            return;
        }

        Class umlClass = codeFile.getUmlClassList().get(0);
        System.out.println("DEBUG: Processing class: " + umlClass.getName());

        // タブのタイトルを更新
        fileTab.setText(umlClass.getName() + ".java");

        // シーケンス図タブの更新
        TabPane tabPane = (TabPane) fileTab.getContent();
        ObservableList<Tab> tabList = tabPane.getTabs();
        List<Operation> operationList = umlClass.getOperationList();
        List<Interaction> interactionList = umlClass.getInteractionList();

        System.out.println("DEBUG: Found " + operationList.size() + " operations");

        // 既存のタブの更新と新規タブの追加
        for (int i = 0; i < operationList.size(); i++) {
            if (i >= tabList.size()) {
                Tab newTab = new Tab();
                newTab.setContent(new WebView());
                tabList.add(newTab);
                System.out.println("DEBUG: Added new method tab");
            }

            Tab sdTab = tabList.get(i);
            Operation operation = operationList.get(i);

            if (i < interactionList.size()) {
                Interaction interaction = interactionList.get(i);
                sdTab.setText(operation.toString());
                System.out.println("DEBUG: Drawing sequence diagram for method: " + operation.toString());
                sequenceDiagramDrawer.draw(codeFile, interaction, (WebView) sdTab.getContent());
            }
        }

        System.out.println("DEBUG: Java sequence diagram update completed");
    }

    private void removeOldSequenceTab(String oldClassName) {
        tabPaneInSequenceTab.getTabs().removeIf(tab -> tab.getText().equals(oldClassName + ".cpp") ||
                tab.getText().equals(oldClassName + ".h"));
    }

    public void updateFileName(String oldName, String newName) {
        // クラス名が変更された場合の処理
        if (oldName != null && newName != null && !oldName.equals(newName)) {
            removeOldSequenceTab(oldName.replace(".h", "").replace(".cpp", ""));
        }
    }

    private Optional<Tab> findFileTab(CodeFile codeFile) {
        for (Pair<CodeFile, Tab> fileTab : fileSdTabList) {
            if (fileTab.getKey().equals(codeFile)) {
                return Optional.of(fileTab.getValue());
            }
        }
        return Optional.empty();
    }
}
