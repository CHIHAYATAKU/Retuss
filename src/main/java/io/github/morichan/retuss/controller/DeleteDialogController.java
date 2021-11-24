package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.Class;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class DeleteDialogController {
    @FXML TreeView cdTreeView;
    @FXML Button deleteBtn;
    private Model model = Model.getInstance();
    private ArrayList cdTreeItemList = new ArrayList();

    public void initialize() {
        TreeItem<String> root = new TreeItem<>("Class List");
        root.setExpanded(true);
        cdTreeItemList.add("Class List");

        List<Class> umlClassList = model.getUmlClassList();
        for(Class umlClass : umlClassList) {
            cdTreeItemList.add(umlClass);
            TreeItem<String> classTreeItem = new TreeItem<>(umlClass.getName());
            classTreeItem.setExpanded(false);
            for(Attribute attribute : umlClass.getAttributeList()) {
                cdTreeItemList.add(attribute);
                TreeItem<String> attributeTreeItem = new TreeItem<>(attribute.toString());
                classTreeItem.getChildren().add(attributeTreeItem);
            }
            for(Operation operation : umlClass.getOperationList()) {
                cdTreeItemList.add(operation);
                TreeItem<String> operationTreeItem = new TreeItem<>(operation.toString());
                classTreeItem.getChildren().add(operationTreeItem);
            }
            root.getChildren().add(classTreeItem);
        }

        cdTreeView.setRoot(root);
        cdTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    @FXML private void delete() {
        ObservableList<Integer> selectedIndices = cdTreeView.getSelectionModel().getSelectedIndices();
        // 未選択または"Class List"を選択している場合
        if (selectedIndices.size() == 0 || selectedIndices.get(0) == 0) {
            return;
        }

        // 削除対象クラス名の探索
        String className = "";
        if(cdTreeItemList.get(selectedIndices.get(0)) instanceof Class) {
            // クラスを削除する場合
            className = ((Class) cdTreeItemList.get(selectedIndices.get(0))).getName();
            model.delete(className);
        } else {
            // 属性・操作を削除する場合
            for(int i=selectedIndices.get(0) - 1; i>0; i--) {
                if(cdTreeItemList.get(i) instanceof Class) {
                    className = ((Class) cdTreeItemList.get(i)).getName();
                    break;
                }
            }
            if(cdTreeItemList.get(selectedIndices.get(0)) instanceof Attribute) {
                model.delete(className, (Attribute) cdTreeItemList.get(selectedIndices.get(0)));
            } else if (cdTreeItemList.get(selectedIndices.get(0)) instanceof Operation) {
                model.delete(className, (Operation) cdTreeItemList.get(selectedIndices.get(0)));
            }
        }

        // ダイアログを閉じる
        Stage stage = (Stage) cdTreeView.getScene().getWindow();
        stage.close();
    }
}
