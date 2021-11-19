package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.drawer.ClassDiagramDrawer;
import io.github.morichan.retuss.model.Model;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class UmlController {
    @FXML private Tab classDiagramTab;
    @FXML private Button classBtn;
    @FXML private Button attributeBtn;
    @FXML private Button operationBtn;
    @FXML private Button relationshipBtn;
    @FXML private Button deleteBtn;
    @FXML private WebView classDiagramWebView;

    private Model model = Model.getInstance();
    private ClassDiagramDrawer classDiagramDrawer;

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

    }

    @FXML
    public void showCreateCombinedFragmentDialog() {

    }

    /**
     * <p> シーケンス図タブのメッセージボタン選択時のシグナルハンドラ</>
     */
    @FXML
    private void showDeleteDialog() {

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

//    @FXML private void showDleteDialog() {
//
//    }

    public void updateDiagram() {
        if(classDiagramTab.isSelected()) {
            classDiagramDrawer.draw();
        }
    }
}
