package io.github.morichan.retuss.controller;

import java.util.EnumSet;
import java.util.Set;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.sculptor.AttributeSculptor;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.model.uml.cpp.utils.Modifier;
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
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private UmlController umlController;

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        initializeClassList();
    }

    private void initializeClassList() {
        classNameComboBox.getItems().clear();
        if (umlController.isJavaSelected()) {
            for (Class cls : javaModel.getUmlClassList()) {
                classNameComboBox.getItems().add(cls.getName());
            }
        } else if (umlController.isCppSelected()) {
            for (CppHeaderClass cls : cppModel.getHeaderClasses()) {
                classNameComboBox.getItems().add(cls.getName());
            }
        }
        if (!classNameComboBox.getItems().isEmpty()) {
            classNameComboBox.setValue(classNameComboBox.getItems().get(0));
        }
    }

    @FXML
    private void createAttribute() {
        AttributeSculptor sculptor = new AttributeSculptor();
        try {
            sculptor.parse(attributeTextField.getText());
            String className = classNameComboBox.getValue().toString();
            Attribute attribute = sculptor.carve();

            if (umlController.isCppSelected()) {
                // 修飾子の抽出と設定
                Set<Modifier> modifiers = extractModifiers(attributeTextField.getText());
                CppHeaderClass cls = cppModel.findClass(className).get();
                modifiers.forEach(mod -> cls.addMemberModifier(attribute.getName().getNameText(), mod));
            }

            if (umlController.isJavaSelected()) {
                javaModel.addAttribute(className, attribute);
            } else if (umlController.isCppSelected()) {
                cppModel.addAttribute(className, attribute);
            }

            Stage stage = (Stage) createBtn.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            messageLabel.setText("Invalid syntax: " + e.getMessage());
        }
    }

    private Set<Modifier> extractModifiers(String attrText) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        if (attrText.contains("static"))
            modifiers.add(Modifier.STATIC);
        if (attrText.contains("const"))
            modifiers.add(Modifier.CONST);
        if (attrText.contains("mutable"))
            modifiers.add(Modifier.MUTABLE);
        return modifiers;
    }
}
