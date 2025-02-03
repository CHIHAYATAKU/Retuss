package io.github.morichan.retuss.controller;

import com.google.common.base.Strings;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
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

    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private UmlController umlController;
    private CodeController codeController;

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        System.out.println("UmlController set in ClassDialogController"); // デバッグ用
    }

    public void setCodeController(CodeController controller) {
        this.codeController = controller;
        System.out.println("UmlController set in ClassDialogController"); // デバッグ用
    }

    @FXML
    private void createClass() {
        if (umlController == null) {
            messageLabel.setText("Error: UmlController is not set");
            return;
        }

        if (validateClassName()) {
            try {
                String className = classNameTextField.getText().trim();

                if (umlController.isJavaSelected()) {
                    Class umlClass = new Class(className);
                    javaModel.addNewUmlClass(umlClass);
                }

                if (umlController.isCppSelected()) {
                    CppHeaderClass umlClass = new CppHeaderClass(className);
                    cppModel.addNewClass(className);
                }

            } catch (Exception e) {
                messageLabel.setText("Failed to create class: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private Boolean validateClassName() {
        if (Strings.isNullOrEmpty(classNameTextField.getText())) {
            messageLabel.setText("Please set a class name.");
            return Boolean.FALSE;
        }

        String className = classNameTextField.getText();

        // 言語に応じた存在チェック
        if (umlController.isJavaSelected()) {
            if (javaModel.findClass(className).isPresent()) {
                messageLabel.setText(
                        String.format("The \"%s\" class already exists in Java. Please set a different class name.",
                                className));
                return Boolean.FALSE;
            }
        } else if (umlController.isCppSelected()) {
            if (cppModel.findClass(className).isPresent()) {
                messageLabel.setText(
                        String.format("The \"%s\" class already exists in C++. Please set a different class name.",
                                className));
                return Boolean.FALSE;
            }
        } else {
            // どちらの言語も選択されていない場合
            if (!umlController.isJavaSelected() && !umlController.isCppSelected()) {
                messageLabel.setText("Please select at least one language.");
                return Boolean.FALSE;
            }
        }

        return Boolean.TRUE;
    }
}