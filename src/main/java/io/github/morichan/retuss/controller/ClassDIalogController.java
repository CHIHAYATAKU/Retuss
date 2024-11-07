package io.github.morichan.retuss.controller;

import com.google.common.base.Strings;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.Class;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

public class ClassDialogController {
    @FXML
    private TextField classNameTextField;
    @FXML
    private Button createBtn;
    @FXML
    private Label messageLabel;
    @FXML
    private ComboBox<String> languageComboBox;

    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();

    @FXML
    private void initialize() {
        // デフォルトでJavaを選択
        languageComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void createClass() {
        if (validateClassName()) {
            Class umlClass = new Class(classNameTextField.getText());
            String language = languageComboBox.getValue();

            try {
                if (language.equals("Java")) {
                    javaModel.addNewUmlClass(umlClass);
                } else if (language.equals("C++")) {
                    cppModel.addNewUmlClass(umlClass);
                }

                // ダイアログを閉じる
                Stage stage = (Stage) createBtn.getScene().getWindow();
                stage.close();
            } catch (Exception e) {
                messageLabel.setText("Failed to create class: " + e.getMessage());
            }
        }
    }

    private Boolean validateClassName() {
        if (Strings.isNullOrEmpty(classNameTextField.getText())) {
            messageLabel.setText("Please set a class name.");
            return Boolean.FALSE;
        }

        String className = classNameTextField.getText();
        String language = languageComboBox.getValue();

        // 言語に応じた存在チェック
        if (language.equals("Java")) {
            if (javaModel.findClass(className).isPresent()) {
                messageLabel.setText(
                        String.format("The \"%s\" class already exists in Java. Please set a different class name.",
                                className));
                return Boolean.FALSE;
            }
        } else if (language.equals("C++")) {
            if (cppModel.findClass(className).isPresent()) {
                messageLabel.setText(
                        String.format("The \"%s\" class already exists in C++. Please set a different class name.",
                                className));
                return Boolean.FALSE;
            }
        }

        return Boolean.TRUE;
    }
}