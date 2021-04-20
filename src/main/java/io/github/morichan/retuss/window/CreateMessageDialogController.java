package io.github.morichan.retuss.window;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.retuss.language.java.Field;
import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import io.github.morichan.retuss.window.diagram.AttributeGraphic;
import io.github.morichan.retuss.window.diagram.sequence.Lifeline;
import io.github.morichan.retuss.window.diagram.sequence.MessageOccurrenceSpecification;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.ArrayList;

/**
 * <p> メッセージの作成ダイアログの管理クラス</p>
 */

public class CreateMessageDialogController {
    @FXML
    private ComboBox<String> classCombo;

    @FXML
    private ComboBox<String> methodCombo;

    @FXML
    private TextField parameterTextField;

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

        // 追加対象クラス名をclassComboに追加
        classCombo.getItems().add(targetClass.getName());
        classCombo.setValue(targetClass.getName());
        // 追加対象クラスが持つメソッド名をmethodComboに追加する
        for (OperationGraphic og : targetClass.getOperationGraphics()) {
            methodCombo.getItems().add(og.getOperation().toString());
        }
        methodCombo.setValue(targetOg.getOperation().toString());

        // 追加対象クラスの属性のクラス名を追加
        for (AttributeGraphic ag : targetClass.getAttributeGraphics()) {
            classCombo.getItems().add(ag.getAttribute().getType().getName().getNameText());
        }
    }

    @FXML
    private void setMethod() {
        // classComboで選択したクラスが持つpublicメソッドをmethodComboにセットする
        methodCombo.getItems().clear();
        String selectedClassName = classCombo.getValue();
        Class selectedUmlClass = sequenceDiagramDrawer.getUmlPackage().searchClass(selectedClassName);
        for (OperationGraphic og : selectedUmlClass.getOperationGraphics()) {
            if (og.getOperation().getVisibility().toString().equals("+")) {
                methodCombo.getItems().add(og.getOperation().toString());
            }
        }
        methodCombo.getSelectionModel().select(0);
    }

    @FXML
    private void createMessage() {
        Class callClass = sequenceDiagramDrawer.getUmlPackage().searchClass(classCombo.getValue());
        OperationGraphic callOg = sequenceDiagramDrawer.getUmlPackage().searchOperatingGraphics(methodCombo.getValue());

        String methodName = callOg.getOperation().getName().getNameText();
        String parameter = parameterTextField.getText();
        Class umlClass = callClass;
        if (!callClass.getName().equals(targetClass)) {
            // 呼び出すメソッドを持つクラスのインスタンスをフィールドから探す
            for (AttributeGraphic ag : targetClass.getAttributeGraphics()) {
                if (ag.getAttribute().getType().getName().getNameText().equals(callClass.getName())) {
                    umlClass = new Class(ag.getAttribute().getName().getNameText());
                    break;
                }
            }
        }
        Lifeline lifeline = callOg.getInteraction().getMessage().getLifeline();
        sequenceDiagramDrawer.addCallMethodMessage(targetOg, methodName, parameter, umlClass, lifeline);

        Stage stage = (Stage) createButton.getScene().getWindow();
        stage.close();
        sequenceDiagramDrawer.draw();
        mainController.getCodeController().updateCode();
    }


}
