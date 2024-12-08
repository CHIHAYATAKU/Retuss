package io.github.morichan.retuss.controller;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.fescue.sculptor.OperationSculptor;
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

public class OperationDialogController {
    @FXML
    private Label messageLabel;
    @FXML
    private ComboBox classNameComboBox;
    @FXML
    private TextField operationTextField;
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
    private void createOperation() {
        try {
            String operationText = operationTextField.getText();
            String className = classNameComboBox.getValue().toString();

            if (umlController.isJavaSelected()) {
                // Javaの場合は既存のsculptorを使用
                OperationSculptor sculptor = new OperationSculptor();
                sculptor.parse(operationText);
                Operation operation = sculptor.carve();
                javaModel.addOperation(className, operation);
            } else if (umlController.isCppSelected()) {
                // C++の場合は新しいパーサーを使用
                Operation operation = parseOperationString(operationText);
                Set<Modifier> modifiers = extractModifiers(operationText);
                CppHeaderClass cls = cppModel.findClass(className).get();
                modifiers.forEach(mod -> cls.addMemberModifier(operation.getName().getNameText(), mod));
                cppModel.addOperation(className, operation);
            }

            Stage stage = (Stage) createBtn.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            messageLabel.setText("Invalid syntax: " + e.getMessage());
            e.printStackTrace(); // デバッグ用
        }
    }

    private Operation parseOperationString(String input) {
        System.out.println("\n=== Start Operation Parsing ===");
        System.out.println("Input string: " + input);

        try {
            // 可視性を取得
            Visibility visibility = Visibility.Private;
            if (input.startsWith("+"))
                visibility = Visibility.Public;
            else if (input.startsWith("#"))
                visibility = Visibility.Protected;
            System.out.println("Visibility: " + visibility);

            // メソッド名とパラメータを分離
            int paramStart = input.indexOf("(");
            int paramEnd = input.lastIndexOf(")");
            System.out.println("Parameter brackets found at: " + paramStart + ", " + paramEnd);

            if (paramStart == -1 || paramEnd == -1) {
                throw new IllegalArgumentException("Missing parentheses in method declaration");
            }

            // メソッド名を取得（修飾子を除去）
            String namePart = input.substring(0, paramStart).replaceAll("[+#-]", "").trim();
            String pureName = namePart.replaceAll("\\{.*?\\}", "").trim();
            System.out.println("Method name: " + pureName);

            // 戻り値の型を取得
            String returnType = "void"; // デフォルト
            String afterParams = input.substring(paramEnd + 1).trim();
            if (afterParams.startsWith(":")) {
                returnType = afterParams.substring(1).trim();
            }
            System.out.println("Return type: " + returnType);

            // Operation オブジェクトを生成
            Name operationName = new Name(pureName);
            System.out.println("Created Name object: " + operationName);

            Operation operation = new Operation(operationName);
            System.out.println("Created Operation object");

            // パラメータリストを初期化
            operation.setParameters(new ArrayList<>());
            System.out.println("Initialized parameters list");

            operation.setReturnType(new Type(returnType));
            System.out.println("Set return type");

            operation.setVisibility(visibility);
            System.out.println("Set visibility");

            // パラメータを処理
            String paramString = input.substring(paramStart + 1, paramEnd).trim();
            System.out.println("Parameter string: [" + paramString + "]");

            if (!paramString.isEmpty()) {
                String[] params = paramString.split(",");
                for (String param : params) {
                    System.out.println("Processing parameter: " + param);
                    String[] paramParts = param.trim().split(":");
                    if (paramParts.length == 2) {
                        String paramName = paramParts[0].trim();
                        String paramType = paramParts[1].trim();
                        System.out.println("Parameter name: " + paramName + ", type: " + paramType);
                        Parameter parameter = new Parameter(new Name(paramName));
                        parameter.setType(new Type(paramType));
                        operation.addParameter(parameter);
                        System.out.println("Added parameter");
                    }
                }
            }

            System.out.println("=== Operation Parsing Completed ===");
            return operation;

        } catch (Exception e) {
            System.out.println("=== Error in Operation Parsing ===");
            System.out.println("Exception type: " + e.getClass().getName());
            System.out.println("Exception message: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalArgumentException("Failed to parse operation: " + e.getMessage());
        }
    }

    private Set<Modifier> extractModifiers(String opText) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        if (opText.contains("virtual"))
            modifiers.add(Modifier.VIRTUAL);
        if (opText.contains("static"))
            modifiers.add(Modifier.STATIC);
        if (opText.contains("const"))
            modifiers.add(Modifier.READONLY);
        if (opText.contains("override"))
            modifiers.add(Modifier.OVERRIDE);
        if (opText.contains("= 0")) {
            modifiers.add(Modifier.ABSTRACT);
            modifiers.add(Modifier.VIRTUAL);
        }
        return modifiers;
    }
}