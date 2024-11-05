package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class DeleteDialogControllerCD {
    @FXML
    TreeView cdTreeView;
    private JavaModel model = JavaModel.getInstance();
    private ArrayList cdTreeItemList = new ArrayList();

    public void initialize() {
        TreeItem<String> root = new TreeItem<>("Class List");
        root.setExpanded(true);
        cdTreeItemList.add("Class List");

        List<Class> umlClassList = model.getUmlClassList();
        for (Class umlClass : umlClassList) {
            // クラス
            cdTreeItemList.add(umlClass);
            TreeItem<String> classTreeItem = new TreeItem<>(umlClass.getName());
            classTreeItem.setExpanded(false);
            // 属性またはコンポジション関係
            for (Attribute attribute : umlClass.getAttributeList()) {
                cdTreeItemList.add(attribute);
                if (isComposition(attribute.getType())) {
                    // コンポジション関係
                    TreeItem<String> compositionTreeItem = new TreeItem<>(
                            String.format("Composition : %s", attribute.getType().getName()));
                    classTreeItem.getChildren().add(compositionTreeItem);
                } else {
                    // 属性
                    TreeItem<String> attributeTreeItem = new TreeItem<>(attribute.toString());
                    classTreeItem.getChildren().add(attributeTreeItem);
                }
            }
            // 操作
            for (Operation operation : umlClass.getOperationList()) {
                cdTreeItemList.add(operation);
                TreeItem<String> operationTreeItem = new TreeItem<>(operation.toString());
                classTreeItem.getChildren().add(operationTreeItem);
            }
            // 汎化関係
            if (umlClass.getSuperClass().isPresent()) {
                cdTreeItemList.add("Generalization");
                TreeItem<String> generalizationTreeItem = new TreeItem<>(
                        String.format("Generalization : %s", umlClass.getSuperClass().get().getName()));
                classTreeItem.getChildren().add(generalizationTreeItem);
            }

            root.getChildren().add(classTreeItem);
        }

        cdTreeView.setRoot(root);
        cdTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    @FXML
    private void delete() {
        ObservableList<Integer> selectedIndices = cdTreeView.getSelectionModel().getSelectedIndices();
        // 未選択または"Class List"を選択している場合
        if (selectedIndices.size() == 0 || selectedIndices.get(0) == 0) {
            return;
        }

        // 削除対象クラス名の探索
        String className = "";
        if (cdTreeItemList.get(selectedIndices.get(0)) instanceof Class) {
            // クラスを削除する場合
            className = ((Class) cdTreeItemList.get(selectedIndices.get(0))).getName();
            model.delete(className);
        } else {
            // 属性・操作を削除する場合
            // 対象のクラスを探索
            for (int i = selectedIndices.get(0) - 1; i > 0; i--) {
                if (cdTreeItemList.get(i) instanceof Class) {
                    className = ((Class) cdTreeItemList.get(i)).getName();
                    break;
                }
            }
            // 削除
            if (cdTreeItemList.get(selectedIndices.get(0)) instanceof Attribute) {
                // 属性またはコンポジション関係の削除
                model.delete(className, (Attribute) cdTreeItemList.get(selectedIndices.get(0)));
            } else if (cdTreeItemList.get(selectedIndices.get(0)) instanceof Operation) {
                // 操作の削除
                model.delete(className, (Operation) cdTreeItemList.get(selectedIndices.get(0)));
            } else if (cdTreeItemList.get(selectedIndices.get(0)).equals("Generalization")) {
                // 汎化関係の削除
                model.deleteSuperClass(className);
            }
        }

        // ダイアログを閉じる
        Stage stage = (Stage) cdTreeView.getScene().getWindow();
        stage.close();
    }

    private Boolean isComposition(Type type) {
        // typeがユーザ定義のクラス名と同一ならば、そのクラスとコンポジション関係とする
        // 同一名のクラスが存在することは考慮しない
        for (Class umlClass : model.getUmlClassList()) {
            if (type.getName().getNameText().equals(umlClass.getName())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }
}
