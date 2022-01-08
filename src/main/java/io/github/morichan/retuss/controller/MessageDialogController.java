package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.*;
import io.github.morichan.retuss.model.uml.Class;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;


public class MessageDialogController {
    // SynchCallタブ
    @FXML private ComboBox<MessageSort> sortCombo;
    @FXML private ComboBox<Class> classCombo;
    @FXML private ComboBox<Operation> operationCombo;
    @FXML private TextField lifelineNameTextField;
    @FXML private TextField argumentTextField;
    @FXML private Button createButton;
    @FXML private HBox classHBox;
    @FXML private HBox operationHBox;
    @FXML private HBox lifelineNameHBox;
    @FXML private HBox argumentsHBox;
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

        // メッセージソートをコンボボックスに追加
        for (MessageSort messageSort : MessageSort.values()) {
            sortCombo.getItems().add(messageSort);
        }
        sortCombo.setValue(MessageSort.synchCall);

        // クラスをコンボボックスに追加
        for (Class umlClass : model.getUmlClassList()) {
            classCombo.getItems().add(umlClass);
        }
        classCombo.setValue(targetClass);

        // 操作をコンボボックスに追加
        setOperation();

        // 入力項目の表示・非表示を初期化
        changeForm();
        // 非表示のHBoxの領域を詰めて他のHBoxを表示する
        classHBox.managedProperty().bind(classHBox.visibleProperty());
        operationHBox.managedProperty().bind(operationHBox.visibleProperty());
        lifelineNameHBox.managedProperty().bind(lifelineNameHBox.visibleProperty());
        argumentsHBox.managedProperty().bind(argumentsHBox.visibleProperty());
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

    /**
     * 選択されたMessageSortに応じて、入力する項目を変更する
     */
    @FXML private void changeForm() {
        // 全て非表示にする
        classHBox.setVisible(false);
        operationHBox.setVisible(false);
        lifelineNameHBox.setVisible(false);
        argumentsHBox.setVisible(false);

        // 必要なHBoxを表示する
        MessageSort selectedMessageSort = sortCombo.getValue();
        if (selectedMessageSort == MessageSort.synchCall) {
            classHBox.setVisible(true);
            operationHBox.setVisible(true);
            argumentsHBox.setVisible(true);
        } else if (selectedMessageSort == MessageSort.createMessage) {
            classHBox.setVisible(true);
            lifelineNameHBox.setVisible(true);
            argumentsHBox.setVisible(true);
        } else {

        }
    }

    @FXML private void createMessage() {

        MessageSort messageSort = sortCombo.getValue();
        Message message = null;
        if (messageSort == MessageSort.synchCall) {
            message = createSynchCallMessage();
        } else if (messageSort == MessageSort.createMessage) {
            message = createCreateMessage();
        }

        if (Objects.isNull(message)) {
            // TODO: エラー処理
            return;
        }

        // messageの追加
        model.addMessage(targetClass.getName(), targetOperation, message);

        // ダイアログを閉じる
        Stage stage = (Stage) createButton.getScene().getWindow();
        stage.close();
    }

    private Message createSynchCallMessage() {
        // messageの作成
        String messageName = operationCombo.getValue().getName().getNameText();

        // 終点Lifelineの作成
        // 属性にない他クラスの操作を呼び出す場合は、クラス名のみのライフラインとする
        Lifeline endLifeline = new Lifeline("", classCombo.getValue().getName());
        InteractionUse interactionUse = new InteractionUse(endLifeline, messageName);
        if(targetClass.equals(classCombo.getValue())) {
            // 自分が持つ操作を呼び出す場合
            endLifeline = new Lifeline("", targetClass.getName());
        } else {
            // 属性に持つ他クラスの操作を呼び出す場合
            for(Attribute attribute : targetClass.getAttributeList()) {
                if(attribute.getType().getName().getNameText().equals(classCombo.getValue().getName())) {
                    endLifeline = new Lifeline(attribute.getName().getNameText(), attribute.getType().toString());
                    interactionUse.setCollaborationUse(attribute.getName().getNameText());
                    break;
                }
            }
        }

        OccurenceSpecification messageEnd = new OccurenceSpecification(endLifeline);
        messageEnd.getInteractionFragmentList().add(interactionUse);
        Message message = new Message(messageName, messageEnd);

        // 引数を設定
        if(!argumentTextField.getText().isEmpty()) {
            ArrayList<Parameter> parameterList = new ArrayList<>();
            String[] argumentsText = argumentTextField.getText().split(",");
            for(String argument : argumentsText) {
                parameterList.add(new Parameter(new Name(argument)));
            }
            message.getParameterList().addAll(parameterList);
            interactionUse.getParameterList().addAll(parameterList);
        }

        return message;
    }

    private Message createCreateMessage() {
        Class selectedClass = classCombo.getValue();

        // messageEndの生成
        String lifelineName = lifelineNameTextField.getText();
        if(lifelineName.isEmpty()) {
            lifelineName = selectedClass.getName().toLowerCase();
        }
        Lifeline endLifeline = new Lifeline(lifelineName, selectedClass.getName());
        OccurenceSpecification messageEnd = new OccurenceSpecification(endLifeline);

        // メッセージ名の生成
        String messageName = "create";
        Message message = new Message(messageName, messageEnd);
        message.setMessageSort(MessageSort.createMessage);
        // 引数を設定
        if(!argumentTextField.getText().isEmpty()) {
            ArrayList<Parameter> parameterList = new ArrayList<>();
            String[] argumentsText = argumentTextField.getText().split(",");
            for(String argument : argumentsText) {
                parameterList.add(new Parameter(new Name(argument)));
            }
            message.setParameterList(parameterList);
        }

        return message;
    }
}
