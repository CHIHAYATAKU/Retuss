package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.model.uml.cpp.utils.Modifier;
import io.github.morichan.retuss.model.uml.cpp.utils.RelationshipElement;
import io.github.morichan.retuss.model.uml.cpp.utils.RelationshipInfo;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DeleteDialogControllerCD {
    @FXML
    TreeView cdTreeView;
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private UmlController umlController;
    private ArrayList cdTreeItemList = new ArrayList();

    public void initialize() {
        TreeItem<String> root = new TreeItem<>("Class List");
        root.setExpanded(true);
        cdTreeItemList.add("Class List");

        if (umlController.isJavaSelected()) {
            // 既存のJava用の処理
            initializeJavaTree(root);
        } else if (umlController.isCppSelected()) {
            // C++用の処理を追加
            initializeCppTree(root);
        }

        cdTreeView.setRoot(root);
        cdTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private void initializeJavaTree(TreeItem<String> root) {
        // 既存のJavaの処理をそのまま移動
        List<Class> umlClassList = javaModel.getUmlClassList();
        for (Class umlClass : umlClassList) {
            cdTreeItemList.add(umlClass);
            TreeItem<String> classTreeItem = new TreeItem<>(umlClass.getName());
            classTreeItem.setExpanded(false);

            for (Attribute attribute : umlClass.getAttributeList()) {
                cdTreeItemList.add(attribute);
                if (isComposition(attribute.getType())) {
                    TreeItem<String> compositionTreeItem = new TreeItem<>(
                            String.format("Composition : %s", attribute.getType().getName()));
                    classTreeItem.getChildren().add(compositionTreeItem);
                } else {
                    TreeItem<String> attributeTreeItem = new TreeItem<>(attribute.toString());
                    classTreeItem.getChildren().add(attributeTreeItem);
                }
            }

            for (Operation operation : umlClass.getOperationList()) {
                cdTreeItemList.add(operation);
                TreeItem<String> operationTreeItem = new TreeItem<>(operation.toString());
                classTreeItem.getChildren().add(operationTreeItem);
            }

            if (umlClass.getSuperClass().isPresent()) {
                cdTreeItemList.add("Generalization");
                TreeItem<String> generalizationTreeItem = new TreeItem<>(
                        String.format("Generalization : %s", umlClass.getSuperClass().get().getName()));
                classTreeItem.getChildren().add(generalizationTreeItem);
            }

            root.getChildren().add(classTreeItem);
        }
    }

    private void initializeCppTree(TreeItem<String> root) {
        List<CppHeaderClass> cppClasses = cppModel.getHeaderClasses();
        for (CppHeaderClass cppClass : cppClasses) {
            cdTreeItemList.add(cppClass);
            TreeItem<String> classTreeItem = new TreeItem<>(cppClass.getName());
            classTreeItem.setExpanded(false);

            // 属性
            for (Attribute attribute : cppClass.getAttributeList()) {
                cdTreeItemList.add(attribute);
                TreeItem<String> attributeTreeItem = new TreeItem<>(
                        formatAttribute(attribute, cppClass));
                classTreeItem.getChildren().add(attributeTreeItem);
            }

            // 操作
            for (Operation operation : cppClass.getOperationList()) {
                cdTreeItemList.add(operation);
                TreeItem<String> operationTreeItem = new TreeItem<>(
                        formatOperation(operation, cppClass));
                classTreeItem.getChildren().add(operationTreeItem);
            }

            // 関係
            for (RelationshipInfo relation : cppClass.getRelationshipManager().getAllRelationships()) {
                cdTreeItemList.add(relation);
                TreeItem<String> relationItem = new TreeItem<>(formatRelationship(relation));
                classTreeItem.getChildren().add(relationItem);
            }

            root.getChildren().add(classTreeItem);
        }
    }

    @FXML
    private void delete() {
        ObservableList<Integer> selectedIndices = cdTreeView.getSelectionModel().getSelectedIndices();
        if (selectedIndices.size() == 0 || selectedIndices.get(0) == 0) {
            return;
        }

        if (umlController.isJavaSelected()) {
            deleteJavaElement(selectedIndices);
        } else if (umlController.isCppSelected()) {
            deleteCppElement(selectedIndices);
        }

        Stage stage = (Stage) cdTreeView.getScene().getWindow();
        stage.close();
    }

    private void deleteJavaElement(ObservableList<Integer> selectedIndices) {
        // 既存のJava用削除処理をそのまま移動
        String className = "";
        if (cdTreeItemList.get(selectedIndices.get(0)) instanceof Class) {
            className = ((Class) cdTreeItemList.get(selectedIndices.get(0))).getName();
            javaModel.delete(className);
        } else {
            for (int i = selectedIndices.get(0) - 1; i > 0; i--) {
                if (cdTreeItemList.get(i) instanceof Class) {
                    className = ((Class) cdTreeItemList.get(i)).getName();
                    break;
                }
            }
            if (cdTreeItemList.get(selectedIndices.get(0)) instanceof Attribute) {
                javaModel.delete(className, (Attribute) cdTreeItemList.get(selectedIndices.get(0)));
            } else if (cdTreeItemList.get(selectedIndices.get(0)) instanceof Operation) {
                javaModel.delete(className, (Operation) cdTreeItemList.get(selectedIndices.get(0)));
            } else if (cdTreeItemList.get(selectedIndices.get(0)).equals("Generalization")) {
                javaModel.deleteSuperClass(className);
            }
        }
    }

    private void deleteCppElement(ObservableList<Integer> selectedIndices) {
        String className = "";
        Object selectedItem = cdTreeItemList.get(selectedIndices.get(0));

        if (selectedItem instanceof CppHeaderClass) {
            className = ((CppHeaderClass) selectedItem).getName();
            cppModel.delete(className);
        } else {
            // 対象クラスの探索
            for (int i = selectedIndices.get(0) - 1; i > 0; i--) {
                if (cdTreeItemList.get(i) instanceof CppHeaderClass) {
                    className = ((CppHeaderClass) cdTreeItemList.get(i)).getName();
                    break;
                }
            }

            if (selectedItem instanceof Attribute) {
                cppModel.delete(className, (Attribute) selectedItem);
            } else if (selectedItem instanceof Operation) {
                cppModel.delete(className, (Operation) selectedItem);
            } else if (selectedItem instanceof RelationshipInfo) {
                // RelationshipInfo relation = (RelationshipInfo) selectedItem;
                // switch (relation.getType()) {
                // case INHERITANCE:
                // cppModel.deleteSuperClass(className);
                // break;
                // case REALIZATION:
                // cppModel.deleteRealization(className, relation.getTargetClass());
                // break;
                // default:
                // // コンポジション、集約、関連は属性の削除として処理
                // if (!relation.getElements().isEmpty()) {
                // RelationshipElement elem = relation.getElements().iterator().next();
                // Attribute attr = new Attribute(new Name(elem.getName()));
                // attr.setType(new Type(relation.getTargetClass()));
                // cppModel.delete(className, attr);
                // }
                // break;
            }
        }
    }

    // フォーマットメソッド群
    private String formatAttribute(Attribute attr, CppHeaderClass cls) {
        StringBuilder sb = new StringBuilder(attr.toString());
        Set<Modifier> modifiers = cls.getModifiers(attr.getName().getNameText());
        if (!modifiers.isEmpty()) {
            sb.append(" {")
                    // .append(modifiers.stream()
                    // .map(Modifier::getCppText(false))
                    // .collect(Collectors.joining(", ")))
                    .append("}");
        }
        return sb.toString();
    }

    private String formatOperation(Operation op, CppHeaderClass cls) {
        StringBuilder sb = new StringBuilder(op.toString());
        Set<Modifier> modifiers = cls.getModifiers(op.getName().getNameText());
        if (!modifiers.isEmpty()) {
            sb.append(" {")
                    .append(modifiers.stream()
                            .map(mod -> mod.getCppText(false))
                            .collect(Collectors.joining(", ")))
                    .append("}");
        }
        return sb.toString();
    }

    private String formatRelationship(RelationshipInfo relation) {
        return String.format("%s : %s",
                relation.getType().name(),
                relation.getTargetClass());
    }

    private Boolean isComposition(Type type) {
        // 既存のメソッドをそのまま維持
        for (Class umlClass : javaModel.getUmlClassList()) {
            if (type.getName().getNameText().equals(umlClass.getName())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }
}