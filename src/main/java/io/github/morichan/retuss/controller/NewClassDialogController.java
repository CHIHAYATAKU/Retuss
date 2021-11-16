package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.Class;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class NewClassDialogController {
    @FXML private TextField classNameTextField;
    @FXML private Button createBtn;
    @FXML private Label messageLabel;
    private Model model = Model.getInstance();

    @FXML private void createClass() {
        if(validateClassName()) {
            Class umlClass = new Class(classNameTextField.getText());
            model.addUmlClass(umlClass);
            // ダイアログを閉じる
            Stage stage = (Stage) createBtn.getScene().getWindow();
            stage.close();
        }
    }

    private Boolean validateClassName() {
        if(model.findClass(classNameTextField.getText()).isPresent()) {
            messageLabel.setText(String.format("The \"%s\" class already exists. Please set a different class name.", classNameTextField.getText()));
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }
}
