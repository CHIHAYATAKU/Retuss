package io.github.morichan.retuss.controller;

import com.google.common.base.Strings;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ClassDIalogController {
    @FXML
    private TextField classNameTextField;
    @FXML
    private Button createBtn;
    @FXML
    private Label messageLabel;
    private JavaModel model = JavaModel.getInstance();

    @FXML
    private void createClass() {
        if (validateClassName()) {
            Class umlClass = new Class(classNameTextField.getText());
            model.addNewUmlClass(umlClass);
            // ダイアログを閉じる
            Stage stage = (Stage) createBtn.getScene().getWindow();
            stage.close();
        }
    }

    private Boolean validateClassName() {
        if (Strings.isNullOrEmpty(classNameTextField.getText())) {
            messageLabel.setText("Please set a class name.");
            return Boolean.FALSE;
        }
        if (model.findClass(classNameTextField.getText()).isPresent()) {
            messageLabel.setText(String.format("The \"%s\" class already exists. Please set a different class name.",
                    classNameTextField.getText()));
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }
}
