package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.Lifeline;
import io.github.morichan.retuss.model.uml.Message;
import io.github.morichan.retuss.model.uml.OccurenceSpecification;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Optional;


public class MessageDialogController {
    @FXML private ComboBox<Class> classCombo;
    @FXML private ComboBox<Operation> operationCombo;
    @FXML private TextField argumentTextField;
    @FXML private Button createButton;
    private Model model = Model.getInstance();
    private Class targetClass;
    private Operation targetOperation;

    public void initialize(String fileName, String operationId) {
        // tagetFileの探索
        Optional<CodeFile> fileOptional = model.findCodeFile(fileName);
        if(fileOptional.isEmpty()) {
            Stage stage = (Stage) createButton.getScene().getWindow();
            stage.close();
            return;
        }

        // targetClassの探索
        if(fileOptional.get().getUmlClassList().size() == 0){
            Stage stage = (Stage) createButton.getScene().getWindow();
            stage.close();
            return;
        }
        this.targetClass = fileOptional.get().getUmlClassList().get(0);

        // targetOperationの探索
        Optional<Operation> operationOptional = targetClass.findOperation(operationId);
        if(operationOptional.isEmpty()) {
            Stage stage = (Stage) createButton.getScene().getWindow();
            stage.close();
            return;
        }
        this.targetOperation = operationOptional.get();

        // クラスをコンボボックスに追加
        for (Class umlClass : model.getUmlClassList()) {
            classCombo.getItems().add(umlClass);
        }
        classCombo.setValue(targetClass);

        // 操作をコンボボックスに追加
        setOperation();
    }

    @FXML private void setOperation() {
        // classComboで選択したクラスが持つpublicメソッドをmethodComboにセットする
        operationCombo.getItems().clear();
        Class selectedClass = classCombo.getValue();
        for (Operation operation : selectedClass.getOperationList()) {
            operationCombo.getItems().add(operation);
        }
        operationCombo.getSelectionModel().select(0);
    }

    @FXML private void createMessage() {
        // messageの作成
        String messageName = operationCombo.getValue().getName().getNameText();

        // 終点Lifelineの作成
        // 属性にない他クラスの操作を呼び出す場合は、クラス名のみのライフラインとする
        Lifeline endLifeline = new Lifeline("", classCombo.getValue().getName());
        if(targetClass.equals(classCombo.getValue())) {
            // 自分が持つ操作を呼び出す場合
            endLifeline = new Lifeline("", targetClass.getName());
        } else {
            // 属性に持つ他クラスの操作を呼び出す場合
            for(Attribute attribute : targetClass.getAttributeList()) {
                if(attribute.getType().getName().getNameText().equals(classCombo.getValue().getName())) {
                    endLifeline = new Lifeline(attribute.getName().getNameText(), attribute.getType().toString());
                    break;
                }
            }
        }

        OccurenceSpecification messageEnd = new OccurenceSpecification(endLifeline);
        Message message = new Message(messageName, messageEnd);

        // 引数を設定
        if(argumentTextField.getText().length() > 0) {
            ArrayList<Parameter> parameterList = new ArrayList<>();
            String[] argumentsText = argumentTextField.getText().split(",");
            for(String argument : argumentsText) {
                parameterList.add(new Parameter(new Name(argument)));
            }
            message.setParameterList(parameterList);
        }

        // messageの追加
        model.addMessage(targetClass.getName(), targetOperation, message);

        // ダイアログを閉じる
        Stage stage = (Stage) createButton.getScene().getWindow();
        stage.close();
    }

}
