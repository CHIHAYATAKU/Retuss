package io.github.morichan.retuss.controller;

import com.google.common.base.Strings;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.CppModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

public class NewFileDialogController {
    @FXML
    private TextField fileNameTextField;
    @FXML
    private Button createBtn;
    @FXML
    private Label messageLabel;
    @FXML
    private Label extensionLabel;
    @FXML
    private ComboBox<String> languageComboBox;

    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private UmlController umlController;

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        updateExtensionLabel();
    }

    private void updateExtensionLabel() {
        if (umlController.isJavaSelected() && !umlController.isCppSelected()) {
            extensionLabel.setText(".java");
        } else if (!umlController.isJavaSelected() && umlController.isCppSelected()) {
            extensionLabel.setText(".hpp");
        } else {
            extensionLabel.setText("");
        }
    }

    @FXML
    private void createFile() {
        if (validateClassName()) {
            String fileName = fileNameTextField.getText();

            try {
                if (umlController.isJavaSelected()) {
                    javaModel.addNewCodeFile(fileName + ".java");
                }
                if (umlController.isCppSelected()) {
                    cppModel.addNewFile(fileName + ".hpp");
                }

                // ダイアログを閉じる
                Stage stage = (Stage) createBtn.getScene().getWindow();
                stage.close();
            } catch (Exception e) {
                messageLabel.setText("Failed to create file: " + e.getMessage());
            }
        }
    }

    private Boolean validateClassName() {
        if (Strings.isNullOrEmpty(fileNameTextField.getText())) {
            messageLabel.setText("Please set a file name.");
            return Boolean.FALSE;
        }

        String fileName = fileNameTextField.getText();

        // 言語に応じたバリデーション
        if (umlController.isJavaSelected()) {
            if (javaModel.findClass(fileName).isPresent()) {
                messageLabel.setText(
                        String.format("The \"%s.java\" file already exists. Please set a different file name.",
                                fileName));
                return Boolean.FALSE;
            }
        }

        if (umlController.isCppSelected()) {
            if (cppModel.findClass(fileName).isPresent()) {
                messageLabel.setText(
                        String.format("The \"%s.hpp\" file already exists. Please set a different file name.",
                                fileName));
                return Boolean.FALSE;
            }
        }

        // どちらの言語も選択されていない場合
        if (!umlController.isJavaSelected() && !umlController.isCppSelected()) {
            messageLabel.setText("Please select at least one language.");
            return Boolean.FALSE;
        }

        return Boolean.TRUE;
    }
}