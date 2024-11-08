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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private JavaClassDiagramDrawer javaClassDiagramDrawer;
    private CppClassDiagramDrawer cppClassDiagramDrawer;
    private SequenceDiagramDrawer sequenceDiagramDrawer;
    private CppSequenceDiagramDrawer cppSequenceDiagramDrawer;
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
        cppSequenceDiagramDrawer = new CppSequenceDiagramDrawer(tabPaneInSequenceTab);

        // チェックボックスにカスタムスタイルを適用
        javaCheckBox.getStyleClass().add("custom-radio-check-box");
        cppCheckBox.getStyleClass().add("custom-radio-check-box");
    }

    public void setCodeController(CodeController controller) {
        this.codeController = controller;
        System.out.println("CodeController set in UmlController"); // デバッグ用
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
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/attributeDialog.fxml"));
            Parent parent = fxmlLoader.load();
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
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/operationDialog.fxml"));
            Parent parent = fxmlLoader.load();
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
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/relationshipDialog.fxml"));
            Parent parent = fxmlLoader.load();
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New Relationship Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @FXML
    private void showDeleteDialogCD() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/deleteDialogCD.fxml"));
            Parent parent = fxmlLoader.load();
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

    public void updateDiagram(ICodeFile codeFile) {
        System.err.println("codeFile : " + codeFile.getCode());
        if (codeFile instanceof CodeFile) {
            // Javaファイルの場合
            System.out.println("Updating diagram for Java file: " + codeFile.getFileName());
            javaClassDiagramDrawer.draw();
            System.err.println("codeFile : " + codeFile.getCode());
            updateSequenceDiagram((CodeFile) codeFile);
        } else if (codeFile instanceof CppFile) {
            // C++ファイルの場合
            CppFile cppFile = (CppFile) codeFile;
            if (cppFile.isHeader()) { // ヘッダーファイルの場合のみUML図を更新
                System.out.println("Updating diagram for C++ header file: " + cppFile.getFileName());
                System.out.println("UML classes: " + cppFile.getUmlClassList().size());
                cppClassDiagramDrawer.draw();
                updateCppSequenceDiagram(cppFile);
            }
        }
    }

    private void updateCppSequenceDiagram(CppFile headerFile) {
        String baseName = headerFile.getFileName().replace(".hpp", "");

        // ペアになる実装ファイルを探す
        CppFile implFile = cppModel.findImplFile(baseName);

        if (implFile == null) {
            System.err.println("No implementation file found for " + headerFile.getFileName());
            return;
        }

        // ファイルタブの探索または作成
        Tab fileTab = findOrCreateCppFileTab(headerFile);
        TabPane methodTabPane = new TabPane();
        fileTab.setContent(methodTabPane);

        // クラス情報の取得
        if (headerFile.getUmlClassList().isEmpty()) {
            return;
        }

        Class umlClass = headerFile.getUmlClassList().get(0);
        fileTab.setText(umlClass.getName() + ".cpp");

        // メソッドごとにタブを作成
        for (Operation operation : umlClass.getOperationList()) {
            Tab methodTab = new Tab(operation.getName().getNameText());
            WebView webView = new WebView();
            methodTab.setContent(webView);
            methodTabPane.getTabs().add(methodTab);

            // シーケンス図の生成と表示
            cppSequenceDiagramDrawer.draw(
                    headerFile,
                    implFile,
                    operation.getName().getNameText(),
                    webView);
        }
    }

    private Tab findOrCreateCppFileTab(CppFile headerFile) {
        String fileName = headerFile.getFileName();
        for (Tab tab : tabPaneInSequenceTab.getTabs()) {
            if (tab.getText().equals(fileName)) {
                return tab;
            }
        }

        Tab newTab = new Tab(fileName);
        tabPaneInSequenceTab.getTabs().add(newTab);
        return newTab;
    }

    /**
     * codeFile従ってSDタブを更新する
     *
     * @param codeFile
     */
    private void updateSequenceDiagram(CodeFile codeFile) {
        // ファイルタブの探索
        Optional<Tab> fileTabOptional = findFileTab(codeFile);
        Tab fileTab;
        if (fileTabOptional.isPresent()) {
            fileTab = fileTabOptional.get();
        } else {
            fileTab = new Tab(codeFile.getFileName());
            fileTab.setContent(new TabPane());
            fileSdTabList.add(new Pair<>(codeFile, fileTab));
            tabPaneInSequenceTab.getTabs().add(fileTab);
        }

        // codeFileにクラス宣言がなければ終了
        if (codeFile.getUmlClassList().size() == 0)
            return;

        Class umlClass = codeFile.getUmlClassList().get(0);

        fileTab.setText(umlClass.getName() + ".java");

        // SDタブの更新
        TabPane tabPane = (TabPane) fileTab.getContent();
        ObservableList<Tab> tabList = tabPane.getTabs();
        List<Operation> operationList = umlClass.getOperationList();
        List<Interaction> interactionList = umlClass.getInteractionList();
        // 既存のタブのタイトル、コンテンツを全て更新する
        for (int i = 0; i < operationList.size(); i++) {
            if (i >= tabList.size()) {
                // タブが足りない場合は追加する
                Tab newTab = new Tab();
                newTab.setContent(new WebView());
                tabList.add(newTab);
            }
            Tab sdTab = tabList.get(i);
            Interaction interaction = interactionList.get(i);

            // タブのタイトルを操作名に設定し、必要に応じてファイル名を使う
            sdTab.setText(operationList.get(i).toString());
            sequenceDiagramDrawer.draw(codeFile, interaction, (WebView) sdTab.getContent());
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
