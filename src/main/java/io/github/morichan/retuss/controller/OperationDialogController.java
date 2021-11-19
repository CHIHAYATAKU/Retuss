package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.sculptor.OperationSculptor;
import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.Class;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class OperationDialogController {
    @FXML
    private Label messageLabel;
    @FXML private ComboBox classNameComboBox;
    @FXML private TextField operationTextField;
    @FXML private Button createBtn;
    private Model model = Model.getInstance();

    public void initialize() {
        // classNameコンボボックスにumlClass名を設定
        for(Class umlClass : model.getUmlClassList()) {
            classNameComboBox.getItems().add(umlClass.getName());
        }
        // classNameComboBoxの初期値を設定
        classNameComboBox.setValue(model.getUmlClassList().get(0).getName());
    }

    @FXML private void createOperation() {
        Class selectedClass = model.findClass(classNameComboBox.getValue().toString()).get();
        OperationSculptor sculptor = new OperationSculptor();
        try {
            sculptor.parse(operationTextField.getText());
            model.addOperation(selectedClass.getName(), sculptor.carve());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            messageLabel.setText("Invalid syntax: Please check the operation syntax.");
            return;
        }

        // ダイアログを閉じる
        Stage stage = (Stage) createBtn.getScene().getWindow();
        stage.close();
    }
}
