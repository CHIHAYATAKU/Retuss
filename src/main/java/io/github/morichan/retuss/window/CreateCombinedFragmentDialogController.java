package io.github.morichan.retuss.window;

import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.window.diagram.AttributeGraphic;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import io.github.morichan.retuss.window.diagram.sequence.InteractionOperand;
import io.github.morichan.retuss.window.diagram.sequence.InteractionOperandKind;
import io.github.morichan.retuss.window.diagram.sequence.Lifeline;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;

/**
 * <p> 複合フラグメントの作成ダイアログの管理クラス</p>
 */

public class CreateCombinedFragmentDialogController {
    @FXML
    private TabPane kindTabPane;

    @FXML
    private TextField guardTextFieldOpt;

    @FXML
    private VBox guardVBoxAlt;

    @FXML
    private Button addGuardButton;

    @FXML
    private Button deleteGuardButton;

    @FXML
    private Spinner numLoopSpinner;

    @FXML
    private CheckBox numLoopNoneCheckBox;

    @FXML
    private TextField guardTextFieldLoop;

    @FXML
    private Button createButton;

    private MainController mainController;
    private SequenceDiagramDrawer sequenceDiagramDrawer;
    private Class targetClass;
    private OperationGraphic targetOg;

    public void initialize(MainController mainController, String classId, String operationId) {
        this.mainController = mainController;
        this.sequenceDiagramDrawer = mainController.getSequenceDiagramDrawer();
        // messageを追加するクラス
        this.targetClass = sequenceDiagramDrawer.getUmlPackage().searchClass(classId);
        // messageを追加するOperationGraphic
        this.targetOg = sequenceDiagramDrawer.getUmlPackage().searchOperatingGraphics(operationId);

    }

    @FXML
    private void addGuardTextField() {
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        Label label = new Label("ガード条件" + (guardVBoxAlt.getChildren().size() + 1));
        label.setPrefWidth(100);
        TextField textField = new TextField();
        textField.setPrefWidth(200);
        hBox.getChildren().addAll(label, textField);
        guardVBoxAlt.getChildren().add(hBox);
        if (guardVBoxAlt.getChildren().size() > 2) {
            deleteGuardButton.setDisable(false);
        }
    }

    @FXML
    private void deleteGuardTextField() {
        guardVBoxAlt.getChildren().remove(guardVBoxAlt.getChildren().size() - 1);
        if (guardVBoxAlt.getChildren().size() <= 2) {
            deleteGuardButton.setDisable(true);
        }
    }

    @FXML
    private void createCombinedFragment() {
        Tab selectedTab = new Tab();
        Boolean isCreated = true;
        for (Tab tab : kindTabPane.getTabs()) {
            if (tab.isSelected()) {
                selectedTab = tab;
            }
        }

        if (selectedTab.getText().equals("opt")) {
            isCreated = createCFOpt();
        } else if (selectedTab.getText().equals("alt")) {
            isCreated = createCFAlt();
        } else if (selectedTab.getText().equals("loop")) {
            isCreated = createCFLoop();
        }

        if (isCreated) {
            Stage stage = (Stage) createButton.getScene().getWindow();
            stage.close();
            sequenceDiagramDrawer.draw();
            mainController.getCodeController().createCodeTabs(sequenceDiagramDrawer.getUmlPackage());
        }

    }

    private Boolean createCFOpt() {
        if (guardTextFieldOpt.getText().isEmpty()) {
            return false;
        }
        InteractionOperandKind interactionOperandKind = InteractionOperandKind.opt;
        ArrayList<InteractionOperand> interactionOperandList = new ArrayList<InteractionOperand>();
        interactionOperandList.add(new InteractionOperand(guardTextFieldOpt.getText()));
        targetOg.getInteraction().addCombinedFragment(interactionOperandKind, interactionOperandList);

        return true;
    }

    private Boolean createCFAlt() {
        InteractionOperandKind interactionOperandKind = InteractionOperandKind.alt;
        ArrayList<InteractionOperand> interactionOperandList = new ArrayList<InteractionOperand>();
        for (int i = 0; i < guardVBoxAlt.getChildren().size(); i++) {
            HBox hBox = (HBox) guardVBoxAlt.getChildren().get(i);
            TextField guardTextField = (TextField) hBox.getChildren().get(1);
            if (guardTextField.getText().isEmpty()) {
                return false;
            }
            interactionOperandList.add(new InteractionOperand(guardTextField.getText()));
        }
        targetOg.getInteraction().addCombinedFragment(interactionOperandKind, interactionOperandList);

        return true;
    }

    private Boolean createCFLoop() {
        // 反復回数またはガード条件のどちらか一方しか複合フラグメントに持たせない。
        // 反復回数とガード条件の両方を持つ複合フラグメントloopをJavaに変換する場合、for文とif文の入れ子になる。
        // 現在、入れ子には対応していない。

        InteractionOperandKind interactionOperandKind = InteractionOperandKind.loop;
        ArrayList<InteractionOperand> interactionOperandList = new ArrayList<InteractionOperand>();
        if (numLoopNoneCheckBox.isSelected()) {
            interactionOperandList.add(new InteractionOperand(guardTextFieldLoop.getText()));
            targetOg.getInteraction().addCombinedFragment(interactionOperandKind, interactionOperandList);
        } else {
            String numLoop = numLoopSpinner.getValue().toString();
            targetOg.getInteraction().addCombinedFragment(interactionOperandKind, interactionOperandList, numLoop);
        }

        return true;
    }




}
