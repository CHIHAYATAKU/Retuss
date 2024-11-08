package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.sculptor.AttributeSculptor;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class AttributeDialogController {
    @FXML
    private Label messageLabel;
    @FXML
    private ComboBox classNameComboBox;
    @FXML
    private TextField attributeTextField;
    @FXML
    private Button createBtn;
    private JavaModel model = JavaModel.getInstance();

    public void initialize() {
        // classNameコンボボックスにumlClass名を設定
        for (Class umlClass : model.getUmlClassList()) {
            classNameComboBox.getItems().add(umlClass.getName());
        }
        // classNameComboBoxの初期値を設定
        classNameComboBox.setValue(model.getUmlClassList().get(0).getName());
    }

    @FXML
    private void createAttribute() {
        Class selectedClass = model.findClass(classNameComboBox.getValue().toString()).get();
        System.out.println("attribute : class = " + model.findClass(classNameComboBox.getValue().toString()).get());
        AttributeSculptor sculptor = new AttributeSculptor();
        try {
            sculptor.parse(attributeTextField.getText());
            model.addAttribute(selectedClass.getName(), sculptor.carve());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            messageLabel.setText("Invalid syntax: Please check the attribute syntax.");
            return;
        }

        // ダイアログを閉じる
        Stage stage = (Stage) createBtn.getScene().getWindow();
        stage.close();
    }
}
