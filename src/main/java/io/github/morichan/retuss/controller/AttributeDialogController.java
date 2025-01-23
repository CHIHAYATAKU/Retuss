package io.github.morichan.retuss.controller;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;
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
        try {
            String attributeText = attributeTextField.getText();
            String className = classNameComboBox.getValue().toString();

            if (umlController.isJavaSelected()) {
                // Javaの場合は既存のsculptorを使用
                AttributeSculptor sculptor = new AttributeSculptor();
                sculptor.parse(attributeText);
                Attribute attribute = sculptor.carve();
                javaModel.addAttribute(className, attribute);
            } else if (umlController.isCppSelected()) {
                // C++の場合は新しいパーサーを使用
                AttributeSculptor sculptor = new AttributeSculptor();
                sculptor.parse(attributeText);
                Attribute attribute = sculptor.carve();
                // Attribute attribute = parseAttributeString(attributeText);
                // Set<Modifier> modifiers = extractModifiers(attributeText);
                // CppHeaderClass cls = cppModel.findClass(className).get();
                // modifiers.forEach(mod -> cls.addMemberModifier(attribute, mod));
                cppModel.addAttribute(className, attribute);
            }

            initializeClassList();
        } catch (Exception e) {
            messageLabel.setText("Invalid syntax: " + e.getMessage());
        }
    }

    private Attribute parseAttributeString(String input) {
        System.out.println("Parsing attribute: " + input);

        try {
            // 可視性を取得
            Visibility visibility = Visibility.Private;
            if (input.startsWith("+"))
                visibility = Visibility.Public;
            else if (input.startsWith("#"))
                visibility = Visibility.Protected;

            // 名前と型を分離
            String[] parts = input.split(":");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid attribute format");
            }

            // 名前部分を処理（修飾子を除去）
            String namePart = parts[0].replaceAll("[+#-]", "").trim();
            namePart = namePart.replaceAll("\\{.*?\\}", "").trim();

            // 型部分と初期値を分離
            String typePart = parts[1].trim();
            String defaultValue = null;
            if (typePart.contains("=")) {
                String[] typeAndDefault = typePart.split("=");
                typePart = typeAndDefault[0].trim();
                defaultValue = typeAndDefault[1].trim();
            }

            // Attribute オブジェクトを生成
            Name name = new Name(namePart);
            Attribute attr = new Attribute(name);
            attr.setType(new Type(typePart));
            attr.setVisibility(visibility);
            if (defaultValue != null) {
                // DefaultValueオブジェクトを作成して設定
                attr.setDefaultValue(new DefaultValue(new OneIdentifier(defaultValue)));
            }

            System.out.println("Parsed attribute - Name: " + namePart +
                    ", Type: " + typePart +
                    ", Default: " + defaultValue +
                    ", Visibility: " + visibility);
            return attr;

        } catch (Exception e) {
            System.err.println("Error parsing attribute: " + e.getMessage());
            throw new IllegalArgumentException("Failed to parse attribute: " + e.getMessage());
        }
    }

    private Set<Modifier> extractModifiers(String text) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String modifierText = matcher.group(1).toLowerCase();
            // C++固有の修飾子を処理
            switch (modifierText) {
                case "static":
                    modifiers.add(Modifier.STATIC);
                    break;
                case "const":
                    modifiers.add(Modifier.READONLY);
                    break;
                case "volatile":
                    modifiers.add(Modifier.VOLATILE);
                    break;
                case "mutable":
                    modifiers.add(Modifier.MUTABLE);
                    break;
                case "virtual":
                    modifiers.add(Modifier.VIRTUAL);
                    break;
                case "override":
                    modifiers.add(Modifier.OVERRIDE);
                    break;
                case "abstract":
                    modifiers.add(Modifier.ABSTRACT);
                    break;
            }
        }
        System.out.println("Extracted modifiers: " + modifiers);
        return modifiers;
    }
}
