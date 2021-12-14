package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.drawer.ClassDiagramDrawer;
import io.github.morichan.retuss.drawer.SequenceDiagramDrawer;
import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.Interaction;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UmlController {
    @FXML private Tab classDiagramTab;
    @FXML private Button classBtn;
    @FXML private Button attributeBtn;
    @FXML private Button operationBtn;
    @FXML private Button relationshipBtn;
    @FXML private Button deleteBtn;
    @FXML private WebView classDiagramWebView;

    @FXML private Tab sequenceDiagramTab;
    @FXML private TabPane tabPaneInSequenceTab;

    private Model model = Model.getInstance();
    private ClassDiagramDrawer classDiagramDrawer;
    private SequenceDiagramDrawer sequenceDiagramDrawer;
    private List<Pair<CodeFile, Tab>> fileSdTabList = new ArrayList<>();


    /**
     * <p> JavaFXにおけるデフォルトコンストラクタ </p>
     *
     * <p>
     * Javaにおける通常のコンストラクタ（ {@code Controller()} メソッド）は使えないため、
     * {@link RetussWindow} クラス内でのFXMLファイル読込み時に {@link #initialize()} メソッドを呼び出す仕様になっています。
     * </p>
     */
    @FXML
    private void initialize() {
        model.setUmlController(this);
        classDiagramDrawer = new ClassDiagramDrawer(classDiagramWebView);
        sequenceDiagramDrawer = new SequenceDiagramDrawer(tabPaneInSequenceTab);
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
     * <p> シーケンス図タブのノーマルボタン選択時のシグナルハンドラ</>
     */
    @FXML
    private void normalMessageInSD() {

    }

    /**
     * <p> シーケンス図タブのキャンバスを右クリックメニューにある「メッセージの追加」をクリック時のシグナルハンドラ </p>
     * <p> シーケンス図タブのキャンバスはSequenceDiagramDrawerクラスで動的に生成するため、シグナルハンドラも動的に割り当てている </p>
     * <p> メッセージ作成ダイアログを表示する </p>
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

    @FXML private void showClassDialog() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/classDialog.fxml"));
            Parent parent = fxmlLoader.load();
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New Class Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @FXML private void showAttributeDialog() {
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

    @FXML private void showOperationDialog() {
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

    @FXML private void showRelationshipDialog() {
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

    @FXML private void showDeleteDialogCD() {
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

    public void updateDiagram(CodeFile codeFile) {
        classDiagramDrawer.draw();
        updateSequenceDiagram(codeFile);
    }

    /**
     * codeFile従ってSDタブを更新する
     * @param codeFile
     */
    private void updateSequenceDiagram(CodeFile codeFile) {
        // ファイルタブの探索
        Optional<Tab> fileTabOptional = findFileTab(codeFile);
        Tab fileTab;
        if(fileTabOptional.isPresent()) {
            fileTab = fileTabOptional.get();
        } else {
            fileTab = new Tab(codeFile.getFileName());
            fileTab.setContent(new TabPane());
            fileSdTabList.add(new Pair<>(codeFile, fileTab));
            tabPaneInSequenceTab.getTabs().add(fileTab);
        }

        // codeFileにクラス宣言がなければ終了
        if(codeFile.getUmlClassList().size() == 0) return;

        // SDタブの更新
        TabPane tabPane = (TabPane)fileTab.getContent();
        ObservableList<Tab> tabList = tabPane.getTabs();
        Class umlClass = codeFile.getUmlClassList().get(0);
        List<Operation> operationList = umlClass.getOperationList();
        List<Interaction> interactionList = umlClass.getInteractionList();
        // 既存のタブのタイトル、コンテンツを全て更新する
        for(int i=0; i<operationList.size(); i++) {
            if(i >= tabList.size()) {
                // タブが足りない場合は追加する
                Tab newTab = new Tab();
                newTab.setContent(new WebView());
                tabList.add(newTab);
            }
            Tab sdTab = tabList.get(i);
            Interaction interaction = interactionList.get(i);
            sdTab.setText(operationList.get(i).toString());
            sequenceDiagramDrawer.draw(codeFile, interaction, (WebView) sdTab.getContent());
        }
    }

    private Optional<Tab> findFileTab(CodeFile codeFile) {
        for(Pair<CodeFile, Tab> fileTab : fileSdTabList) {
            if(fileTab.getKey().equals(codeFile)) {
                return Optional.of(fileTab.getValue());
            }
        }
        return Optional.empty();
    }

}
