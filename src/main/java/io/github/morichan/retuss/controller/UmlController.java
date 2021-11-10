package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.Model;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Arrays;

public class UmlController {
    @FXML
    private TreeView<String> classTree;
    @FXML
    private Tab classDiagramTab;
    @FXML
    private Button normalButtonInCD;
    @FXML
    private Button classButtonInCD;
    @FXML
    private Button noteButtonInCD;
    @FXML
    private Button compositionButtonInCD;
    @FXML
    private Button generalizationButtonInCD;
    @FXML
    private ScrollPane classDiagramScrollPane;
    @FXML
    private Canvas classDiagramCanvas;
    @FXML
    private Tab sequenceDiagramTab;
    @FXML
    private TabPane tabPaneInSequenceTab;
    @FXML
    private Button normalButtonInSD;
    @FXML
    private Button messageButtonInSD;
    private Model model = Model.getInstance();

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
     * <p> Normalボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のNormalボタンで参照 </p>
     */
    @FXML
    private void selectNormalInCD() {

    }

    /**
     * <p> Classボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のClassボタンで参照 </p>
     */
    @FXML
    private void selectClassInCD() {

    }

    /**
     * <p> Noteボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のNoteボタンで参照 </p>
     */
    @FXML
    private void selectNoteInCD() {

    }

    /**
     * <p> Compositionボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のCompositionボタンで参照 </p>
     */
    @FXML
    private void selectCompositionInCD() {
    }

    /**
     * <p> Generalizationボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のGeneralizationボタンで参照 </p>
     */
    @FXML
    private void selectGeneralizationInCD() {
    }

    /**
     * <p> Canvasクリック時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるScrollPane内のCanvas（ {@link #classDiagramCanvas} ）で参照 </p>
     */
    @FXML
    private void clickedCanvasInCD(MouseEvent event) {

    }

    /**
     * <p> Canvasドラッグ時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるScrollPane内のCanvas（ {@link #classDiagramCanvas} ）で参照 </p>
     */
    @FXML
    private void draggedCanvasInCD(MouseEvent event) {

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
}
