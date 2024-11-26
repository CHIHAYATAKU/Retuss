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

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        System.out.println("UmlController set in ClassDialogController"); // デバッグ用
    }

    @FXML
    private void createClass() {
        if (umlController == null) {
            messageLabel.setText("Error: UmlController is not set");
            return;
        }

        System.out.println("Java selected: " + umlController.isJavaSelected());
        System.out.println("C++ selected: " + umlController.isCppSelected());

        if (validateClassName()) {
            try {
                Class umlClass = new Class(classNameTextField.getText());

                if (umlController.isJavaSelected()) {
                    System.out.println("Creating Java class: " + classNameTextField.getText());
                    javaModel.addNewUmlClass(umlClass);
                }

                if (umlController.isCppSelected()) {
                    CppHeaderClass headerClass = new CppHeaderClass(classNameTextField.getText());
                    System.out.println("Creating C++ class: " + classNameTextField.getText());
                    cppModel.addNewFileFromUml(headerClass);
                }

                // ダイアログを閉じる
                Stage stage = (Stage) createBtn.getScene().getWindow();
                stage.close();
            } catch (Exception e) {
                messageLabel.setText("Failed to create class: " + e.getMessage());
                e.printStackTrace(); // デバッグ用にスタックトレースを出力
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