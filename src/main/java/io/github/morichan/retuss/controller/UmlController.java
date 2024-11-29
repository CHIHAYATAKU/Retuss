package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.RetussWindow;
import io.github.morichan.retuss.drawer.*;
import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.CppFile;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.Interaction;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.parser.cpp.CPP14Lexer;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class UmlController {
    private CodeController codeController;

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
    private Tab sequenceDiagramTab;
    @FXML
    private TabPane tabPaneInSequenceTab;
    @FXML
    private CheckBox javaCheckBox;

    @FXML
    private CheckBox cppCheckBox;

    @FXML
    private void toggleJavaSelection() {
        if (javaCheckBox.isSelected()) {
            cppCheckBox.setSelected(false); // Javaを選択時にC++のチェックを外す
        }
    }

    @FXML
    private void toggleCppSelection() {
        if (cppCheckBox.isSelected()) {
            javaCheckBox.setSelected(false); // C++を選択時にJavaのチェックを外す
        }
    }

    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 100;
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private JavaClassDiagramDrawer javaClassDiagramDrawer;
    private CppClassDiagramDrawer cppClassDiagramDrawer;
    private SequenceDiagramDrawer sequenceDiagramDrawer;
    // private CppSequenceDiagramDrawer cppSequenceDiagramDrawer;
    private List<Pair<CodeFile, Tab>> fileSdTabList = new ArrayList<>();

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
        cppModel.setUmlController(this);
        javaClassDiagramDrawer = new JavaClassDiagramDrawer(classDiagramWebView);
        cppClassDiagramDrawer = new CppClassDiagramDrawer(classDiagramWebView);
        sequenceDiagramDrawer = new SequenceDiagramDrawer(tabPaneInSequenceTab);
        // cppSequenceDiagramDrawer = new
        // CppSequenceDiagramDrawer(tabPaneInSequenceTab);

        // チェックボックスにカスタムスタイルを適用
        javaCheckBox.getStyleClass().add("custom-radio-check-box");
        cppCheckBox.getStyleClass().add("custom-radio-check-box");
    }

    public void setCodeController(CodeController controller) {
        this.codeController = controller;
        System.out.println("CodeController set in UmlController");
    }

    // getterメソッドの追加
    public CheckBox getJavaCheckBox() {
        return javaCheckBox;
    }

    public CheckBox getCppCheckBox() {
        return cppCheckBox;
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

    // チェックボックスの状態を取得するメソッド
    public boolean isJavaSelected() {
        return javaCheckBox.isSelected();
    }

    public boolean isCppSelected() {
        return cppCheckBox.isSelected();
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
            if (cppCheckBox.isSelected()) {
                cppClassDiagramDrawer.clearCache();
                cppClassDiagramDrawer.draw();
            }

            // シーケンス図のタブを削除
            if (tabPaneInSequenceTab != null) {
                tabPaneInSequenceTab.getTabs().removeIf(tab -> tab.getText().equals(className + ".h") ||
                        tab.getText().equals(className + ".cpp"));
            }
        });
    }

    public void updateDiagram(CppFile cppFile) {
        // ヘッダーファイルの場合、またはヘッダーファイルが存在する実装ファイルの場合に更新
        if (cppFile.isHeader() || findCorrespondingHeaderFile(cppFile) != null) {
            System.out.println("DEBUG: Processing C++ file: " + cppFile.getFileName());
            Platform.runLater(() -> {
                try {
                    // クラス図の更新（関係抽出を含む）
                    cppClassDiagramDrawer.clearCache(); // キャッシュをクリアして強制的に再描画
                    cppClassDiagramDrawer.draw();
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
                // クラス図の更新
                javaClassDiagramDrawer.draw();

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
                        cppClassDiagramDrawer.draw();
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

    // public void handleCodeUpdate(CppFile file) {
    // if (file.isHeader()) {
    // // ヘッダーファイルが更新された場合
    // String baseName = file.getFileName().replace(".h", "");
    // CppFile implFile = CppModel.getInstance().findImplFile(baseName);
    // updateRelationships(file, implFile);
    // } else {
    // // 実装ファイルが更新された場合
    // String baseName = file.getFileName().replace(".cpp", "");
    // CppFile headerFile = CppModel.getInstance().findHeaderFile(baseName);
    // if (headerFile != null) {
    // updateRelationships(headerFile, file);
    // }
    // }
    // updateDiagram(file);
    // }

    // private void updateRelationships(CppFile headerFile, CppFile implFile) {
    // if (headerFile != null && !headerFile.getHeaderClasses().isEmpty()) {
    // CppHeaderClass headerClass = headerFile.getHeaderClasses().get(0);
    // try {
    // // 実装ファイルからの関係も解析
    // if (implFile != null) {
    // CppMethodAnalyzer analyzer = new CppMethodAnalyzer(headerClass);
    // CharStream input = CharStreams.fromString(implFile.getCode());
    // CPP14Lexer lexer = new CPP14Lexer(input);
    // CommonTokenStream tokens = new CommonTokenStream(lexer);
    // CPP14Parser parser = new CPP14Parser(tokens);
    // ParseTreeWalker.DEFAULT.walk(analyzer, parser.translationUnit());
    // }
    // } catch (Exception e) {
    // System.err.println("Error analyzing relationships: " + e.getMessage());
    // }
    // }
    // }

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

    // /**
    // * codeFile従ってSDタブを更新する
    // *
    // * @param codeFile
    // */
    // private void updateCppSequenceDiagram(CppFile headerFile) {
    // System.out.println("Starting sequence diagram update for: " +
    // headerFile.getFileName());

    // String baseName = headerFile.getFileName().replace(".h", "");
    // CppFile implFile = cppModel.findImplFile(baseName);

    // if (implFile == null) {
    // System.err.println("Implementation file not found for " + baseName);
    // return;
    // }

    // // 既存のタブを探す
    // Tab fileTab = null;
    // for (Tab tab : tabPaneInSequenceTab.getTabs()) {
    // if (tab.getText().equals(baseName + ".cpp")) {
    // fileTab = tab;
    // break;
    // }
    // }

    // if (fileTab == null) {
    // // 新しいタブを作成
    // fileTab = new Tab(baseName + ".cpp");
    // tabPaneInSequenceTab.getTabs().add(fileTab);
    // }

    // // メソッドタブを作成/更新
    // TabPane methodTabPane = new TabPane();
    // fileTab.setContent(methodTabPane);

    // // クラスとメソッドの情報を取得
    // if (!headerFile.getUmlClassList().isEmpty()) {
    // Class umlClass = headerFile.getUmlClassList().get(0);
    // System.out.println("Processing methods for class: " + umlClass.getName());

    // for (Operation operation : umlClass.getOperationList()) {
    // String methodName = operation.getName().getNameText();
    // System.out.println("Processing methods for class: " + methodName);
    // System.out.println("Creating tab for method: " + methodName);

    // Tab methodTab = new Tab(methodName);
    // WebView webView = new WebView();
    // methodTab.setContent(webView);

    // // シーケンス図の生成と表示
    // cppSequenceDiagramDrawer.draw(headerFile, implFile, methodName, webView);

    // methodTabPane.getTabs().add(methodTab);
    // }
    // }
    // }

    private Optional<Tab> findFileTab(CodeFile codeFile) {
        for (Pair<CodeFile, Tab> fileTab : fileSdTabList) {
            if (fileTab.getKey().equals(codeFile)) {
                return Optional.of(fileTab.getValue());
            }
        }
        return Optional.empty();
    }
}
