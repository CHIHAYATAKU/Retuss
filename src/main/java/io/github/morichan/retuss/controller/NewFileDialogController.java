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

    @FXML
    private void initialize() {
        // 言語選択時の拡張子更新
        languageComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue.equals("Java")) {
                        extensionLabel.setText(".java");
                    } else if (newValue.equals("C++")) {
                        extensionLabel.setText(".hpp");
                    }
                });

        // デフォルトでJavaを選択
        languageComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void createFile() {
        if (validateClassName()) {
            String fileName = fileNameTextField.getText();
            String language = languageComboBox.getValue();

            try {
                if (language.equals("Java")) {
                    javaModel.addNewCodeFile(fileName + ".java");
                } else if (language.equals("C++")) {
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
        String language = languageComboBox.getValue();

        // 言語に応じたバリデーション
        if (language.equals("Java")) {
            if (javaModel.findClass(fileName).isPresent()) {
                messageLabel
                        .setText(String.format("The \"%s.java\" file already exists. Please set a different file name.",
                                fileName));
                return Boolean.FALSE;
            }
        } else if (language.equals("C++")) {
            if (cppModel.findClass(fileName).isPresent()) {
                messageLabel
                        .setText(String.format("The \"%s.hpp\" file already exists. Please set a different file name.",
                                fileName));
                return Boolean.FALSE;
            }
        }

        return Boolean.TRUE;
    }
}