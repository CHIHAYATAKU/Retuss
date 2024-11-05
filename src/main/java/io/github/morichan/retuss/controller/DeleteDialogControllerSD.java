package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.*;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Optional;

public class DeleteDialogControllerSD {
    @FXML
    TreeView sdTreeView;
    private Class targetClass;
    private Operation targetOperation;
    private Interaction targetInteraction;
    private JavaModel model = JavaModel.getInstance();
    private ArrayList sdTreeItemList = new ArrayList();

    public void initialize(String fileName, String operationId) {
        // tagetFileの探索
        Optional<CodeFile> fileOptional = model.findCodeFile(fileName);
        if (fileOptional.isEmpty()) {
            Stage stage = (Stage) sdTreeView.getScene().getWindow();
            stage.close();
            return;
        }

        // targetClassの探索
        if (fileOptional.get().getUmlClassList().size() == 0) {
            Stage stage = (Stage) sdTreeView.getScene().getWindow();
            stage.close();
            return;
        }
        this.targetClass = fileOptional.get().getUmlClassList().get(0);

        // targetOperationの探索
        Optional<Operation> operationOptional = targetClass.findOperation(operationId);
        if (operationOptional.isEmpty()) {
            Stage stage = (Stage) sdTreeView.getScene().getWindow();
            stage.close();
            return;
        }
        this.targetOperation = operationOptional.get();

        // targetInteractionの探索
        Optional<Interaction> interactionOptional = targetClass.findInteraction(targetOperation);
        if (interactionOptional.isEmpty()) {
            Stage stage = (Stage) sdTreeView.getScene().getWindow();
            stage.close();
            return;
        }
        this.targetInteraction = interactionOptional.get();

        // sdTreeViewにtargetOperationのinteractionFragment一覧を表示する
        TreeItem<String> root = new TreeItem<>("sd " + targetOperation.toString());
        root.setExpanded(true);
        sdTreeItemList.add(targetInteraction);

        for (InteractionFragment interactionFragment : targetInteraction.getInteractionFragmentList()) {
            root.getChildren().add(interactionFragmentToTreeItem(interactionFragment));
        }

        this.sdTreeView.setRoot(root);
        sdTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @FXML
    private void delete() {
        ObservableList<Integer> selectedIndices = sdTreeView.getSelectionModel().getSelectedIndices();
        // 未選択または"Class List"を選択している場合
        if (selectedIndices.size() == 0 || selectedIndices.get(0) == 0) {
            return;
        }

        // OccurenceSpecificationまたはCombinedFragmentだけで削除できるようにする
        if (sdTreeItemList.get(selectedIndices.get(0)) instanceof InteractionOperand) {
            return;
        }

        model.delete(targetClass.getName(), targetOperation,
                (InteractionFragment) sdTreeItemList.get(selectedIndices.get(0)));

        // ダイアログを閉じる
        Stage stage = (Stage) sdTreeView.getScene().getWindow();
        stage.close();
    }

    private TreeItem<String> interactionFragmentToTreeItem(InteractionFragment interactionFragment) {
        TreeItem<String> treeItem;
        if (interactionFragment instanceof OccurenceSpecification) {
            sdTreeItemList.add(interactionFragment);
            treeItem = new TreeItem<>("Message : " + interactionFragment.toString());
        } else {
            treeItem = CombinedFragmentToTreeItem((CombinedFragment) interactionFragment);
        }
        return treeItem;
    }

    private TreeItem<String> CombinedFragmentToTreeItem(CombinedFragment combinedFragment) {
        TreeItem<String> cfRoot = new TreeItem<>("Combined Fragment : " + combinedFragment.getKind().toString());
        cfRoot.setExpanded(true);
        sdTreeItemList.add(combinedFragment);

        for (InteractionOperand interactionOperand : combinedFragment.getInteractionOperandList()) {
            TreeItem<String> ioItem = new TreeItem<>(interactionOperand.getGuard());
            ioItem.setExpanded(true);
            sdTreeItemList.add(interactionOperand);
            for (InteractionFragment interactionFragment : interactionOperand.getInteractionFragmentList()) {
                ioItem.getChildren().add(interactionFragmentToTreeItem(interactionFragment));
            }
            cfRoot.getChildren().add(ioItem);
        }

        return cfRoot;
    }
}
