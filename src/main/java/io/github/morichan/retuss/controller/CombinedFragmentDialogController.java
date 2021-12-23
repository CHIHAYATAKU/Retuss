package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.*;
import io.github.morichan.retuss.model.uml.Class;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;

public class CombinedFragmentDialogController {
    @FXML private TabPane kindTabPane;
    @FXML private TextField guardTextFieldOpt;
    @FXML private VBox guardVBoxAlt;
    @FXML private Button addGuardButton;
    @FXML private Button deleteGuardButton;
    @FXML private TextField guardTextFieldLoop;
    @FXML private TextField guardTextFieldBreak;
    @FXML private Button createButton;

    private Class targetClass;
    private Operation targetOperation;
    private Model model = Model.getInstance();

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
    }

    @FXML
    private void addGuardTextField() {
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        Label label = new Label("Guard " + (guardVBoxAlt.getChildren().size() + 1));
        label.setPrefWidth(100);
        TextField textField = new TextField();
        textField.setPrefWidth(200);
        hBox.getChildren().addAll(label, textField);
        guardVBoxAlt.getChildren().add(hBox);
        if (guardVBoxAlt.getChildren().size() > 2) {
            deleteGuardButton.setDisable(false);
        }
    }

    @FXML
    private void deleteGuardTextField() {
        guardVBoxAlt.getChildren().remove(guardVBoxAlt.getChildren().size() - 1);
        if (guardVBoxAlt.getChildren().size() <= 2) {
            deleteGuardButton.setDisable(true);
        }
    }

    @FXML
    private void createCombinedFragment() {
        Tab selectedTab = new Tab();
        Boolean isCreated = true;
        for (Tab tab : kindTabPane.getTabs()) {
            if (tab.isSelected()) {
                selectedTab = tab;
            }
        }

        if (selectedTab.getText().equals("opt")) {
            isCreated = createCFOpt();
        } else if (selectedTab.getText().equals("alt")) {
            isCreated = createCFAlt();
        } else if (selectedTab.getText().equals("loop")) {
            isCreated = createCFLoop();
        } else if (selectedTab.getText().equals("break")) {
            isCreated = createCFBreak();
        }

        if (isCreated) {
            Stage stage = (Stage) createButton.getScene().getWindow();
            stage.close();
        }
    }

    private boolean createCFOpt() {
        if (guardTextFieldOpt.getText().isEmpty()) {
            return false;
        }

        Lifeline lifeline = new Lifeline("", targetClass.getName());
        CombinedFragment combinedFragment = new CombinedFragment(lifeline, InteractionOperandKind.opt);
        InteractionOperand interactionOperand = new InteractionOperand(lifeline, guardTextFieldOpt.getText());
        combinedFragment.getInteractionOperandList().add(interactionOperand);

        model.addCombinedFragment(targetClass.getName(), targetOperation, combinedFragment);

        return true;
    }

    private boolean createCFAlt() {
        Lifeline lifeline = new Lifeline("", targetClass.getName());
        CombinedFragment combinedFragment = new CombinedFragment(lifeline, InteractionOperandKind.alt);
        for(int i = 0; i < guardVBoxAlt.getChildren().size(); i++) {
            HBox hBox = (HBox) guardVBoxAlt.getChildren().get(i);
            TextField guardTextField = (TextField) hBox.getChildren().get(1);
            if (guardTextField.getText().isEmpty()) {
                return false;
            }
            InteractionOperand interactionOperand = new InteractionOperand(lifeline, guardTextField.getText());
            combinedFragment.getInteractionOperandList().add(interactionOperand);
        }

        model.addCombinedFragment(targetClass.getName(), targetOperation, combinedFragment);

        return true;
    }

    private boolean createCFLoop() {
        if (guardTextFieldLoop.getText().isEmpty()) {
            return false;
        }

        Lifeline lifeline = new Lifeline("", targetClass.getName());
        CombinedFragment combinedFragment = new CombinedFragment(lifeline, InteractionOperandKind.loop);
        InteractionOperand interactionOperand = new InteractionOperand(lifeline, guardTextFieldLoop.getText());
        combinedFragment.getInteractionOperandList().add(interactionOperand);

        model.addCombinedFragment(targetClass.getName(), targetOperation, combinedFragment);

        return true;
    }

    private boolean createCFBreak() {
        if (guardTextFieldBreak.getText().isEmpty()) {
            return false;
        }

        Lifeline lifeline = new Lifeline("", targetClass.getName());
        CombinedFragment combinedFragment = new CombinedFragment(lifeline, InteractionOperandKind.BREAK);
        InteractionOperand interactionOperand = new InteractionOperand(lifeline, guardTextFieldBreak.getText());
        combinedFragment.getInteractionOperandList().add(interactionOperand);

        model.addCombinedFragment(targetClass.getName(), targetOperation, combinedFragment);

        return true;
    }

}
