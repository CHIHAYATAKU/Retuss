package io.github.morichan.retuss.controller;

import com.google.common.base.Strings;
import io.github.morichan.retuss.model.Model;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class NewFileDialogController {
    @FXML
    private TextField fileNameTextField;
    @FXML private Button createBtn;
    @FXML private Label messageLabel;
    private Model model = Model.getInstance();

    @FXML private void createFile() {
        if(validateClassName()) {
            model.addNewCodeFile(fileNameTextField.getText() + ".java");
            // ダイアログを閉じる
            Stage stage = (Stage) createBtn.getScene().getWindow();
            stage.close();
        }
    }

    private Boolean validateClassName() {
        if(Strings.isNullOrEmpty(fileNameTextField.getText())) {
            messageLabel.setText("Please set a file name.");
            return Boolean.FALSE;
        }
        if(model.findClass(fileNameTextField.getText()).isPresent()) {
            messageLabel.setText(String.format("The \"%s.java\" file already exists. Please set a different file name.", fileNameTextField.getText()));
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }
}
