package io.github.morichan.retuss.window;

import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import io.github.morichan.retuss.window.diagram.sequence.MessageOccurrenceSpecification;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.ArrayList;

/**
 * <p> メッセージの作成ダイアログの管理クラス</p>
 */

public class CreateMessageDialogController {
    @FXML
    private ComboBox objectCombo;

    @FXML
    private ComboBox methodCombo;

    @FXML
    private TextField parameterTextField;

    @FXML
    private Button createButton;

    private SequenceDiagramDrawer sequenceDiagramDrawer;
    private Class targetClass;
    private OperationGraphic targetOg;

    @FXML
    private void createMessage() {
        OperationGraphic callOg = sequenceDiagramDrawer.getUmlPackage().searchOperatingGraphics((String) methodCombo.getValue());
        String methodName = callOg.getOperation().getName().getNameText();
        String parameter = parameterTextField.getText();

        sequenceDiagramDrawer.addMessage(targetOg, methodName, parameter);
        Stage stage = (Stage) createButton.getScene().getWindow();
        stage.close();
        sequenceDiagramDrawer.draw();
    }

    public void initialize(SequenceDiagramDrawer sequenceDiagramDrawer, String classId, String operationId) {
        this.sequenceDiagramDrawer = sequenceDiagramDrawer;
        this.targetClass = sequenceDiagramDrawer.getUmlPackage().searchClass(classId);
        this.targetOg = sequenceDiagramDrawer.getUmlPackage().searchOperatingGraphics(operationId);

        objectCombo.getItems().add(targetClass.getName());
        objectCombo.setValue(targetClass.getName());
        for (OperationGraphic og : targetClass.getOperationGraphics()) {
            methodCombo.getItems().add(og.getOperation().toString());
        }
        methodCombo.setValue(targetOg.getOperation().toString());
    }
}
