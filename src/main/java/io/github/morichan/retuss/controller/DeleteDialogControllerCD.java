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
import javafx.scene.control.Label;
import io.github.morichan.fescue.feature.name.Name;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DeleteDialogControllerCD {
    @FXML
    private TreeView cdTreeView;
    @FXML
    private Label messageLabel;
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private UmlController umlController;
    private List<Object> cdTreeItemList = new ArrayList<>();

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        initialize();
    }

    public void initialize() {
        TreeItem<String> root = new TreeItem<>("Class List");
        root.setExpanded(true);
        cdTreeItemList.clear();
        cdTreeItemList.add("Class List");

        if (umlController != null) {
            if (umlController.isJavaSelected()) {
                initializeJavaTree(root);
            } else if (umlController.isCppSelected()) {
                initializeCppTree(root);
            }
        }

        cdTreeView.setRoot(root);
        cdTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private void initializeJavaTree(TreeItem<String> root) {
        List<Class> umlClassList = javaModel.getUmlClassList();
        System.out.println("Java classes found: " + umlClassList.size()); // デバッグ用

        for (Class umlClass : umlClassList) {
            cdTreeItemList.add(umlClass);
            TreeItem<String> classTreeItem = new TreeItem<>(umlClass.getName());
            classTreeItem.setExpanded(false);

            // 属性とコンポジション
            for (Attribute attribute : umlClass.getAttributeList()) {
                cdTreeItemList.add(attribute);
                if (isJavaComposition(attribute.getType())) {
                    TreeItem<String> compositionTreeItem = new TreeItem<>(
                            String.format("Composition : %s", attribute.getType().getName()));
                    classTreeItem.getChildren().add(compositionTreeItem);
                } else {
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

            // 汎化
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
        System.out.println("C++ classes found: " + cppClasses.size()); // デバッグ用

        for (CppHeaderClass cls : cppClasses) {
            System.out.println("Processing class: " + cls.getName()); // デバッグ用
            cdTreeItemList.add(cls);
            TreeItem<String> classTreeItem = new TreeItem<>(cls.getName());
            classTreeItem.setExpanded(false);

            // 属性
            System.out.println("Attributes found in " + cls.getName() + ": " + cls.getAttributeList().size()); // デバッグ用
            for (Attribute attr : cls.getAttributeList()) {
                System.out.println("Adding attribute: " + attr.getName()); // デバッグ用
                cdTreeItemList.add(attr);
                TreeItem<String> attrItem = new TreeItem<>(formatCppAttribute(attr, cls));
                classTreeItem.getChildren().add(attrItem);
            }

            // 操作
            System.out.println("Operations found in " + cls.getName() + ": " + cls.getOperationList().size()); // デバッグ用
            for (Operation op : cls.getOperationList()) {
                System.out.println("Adding operation: " + op.getName()); // デバッグ用
                cdTreeItemList.add(op);
                TreeItem<String> opItem = new TreeItem<>(formatCppOperation(op, cls));
                classTreeItem.getChildren().add(opItem);
            }

            // 関係
            // System.out.println("Relationships found in " + cls.getName() + ": "
            // + cls.getRelationshipManager().getAllRelationships().size()); // デバッグ用
            // for (RelationshipInfo relation :
            // cls.getRelationshipManager().getAllRelationships()) {
            // System.out
            // .println("Adding relationship: " + relation.getType() + " -> "
            // + relation.getTargetClass().toString()); // デバッグ用
            // cdTreeItemList.add(relation);
            // TreeItem<String> relationItem = new
            // TreeItem<>(formatCppRelationship(relation));
            // classTreeItem.getChildren().add(relationItem);
            // }

            root.getChildren().add(classTreeItem);
            System.out.println("Finished processing class: " + cls.getName()); // デバッグ用
        }
    }

    @FXML
    private void delete() {
        ObservableList<Integer> selectedIndices = cdTreeView.getSelectionModel().getSelectedIndices();
        if (selectedIndices.isEmpty() || selectedIndices.get(0) == 0)
            return;

        try {
            if (umlController.isJavaSelected()) {
                deleteJavaElement(selectedIndices);
            } else if (umlController.isCppSelected()) {
                deleteCppElement(selectedIndices);
            }

            Stage stage = (Stage) cdTreeView.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            messageLabel.setText("Failed to delete: " + e.getMessage());
        }
    }

    private void deleteJavaElement(ObservableList<Integer> selectedIndices) {
        String className = "";
        Object selectedItem = cdTreeItemList.get(selectedIndices.get(0));

        if (selectedItem instanceof Class) {
            className = ((Class) selectedItem).getName();
            javaModel.delete(className);
        } else {
            for (int i = selectedIndices.get(0) - 1; i > 0; i--) {
                if (cdTreeItemList.get(i) instanceof Class) {
                    className = ((Class) cdTreeItemList.get(i)).getName();
                    break;
                }
            }
            if (selectedItem instanceof Attribute) {
                javaModel.delete(className, (Attribute) selectedItem);
            } else if (selectedItem instanceof Operation) {
                javaModel.delete(className, (Operation) selectedItem);
            } else if (selectedItem.equals("Generalization")) {
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
                RelationshipInfo relation = (RelationshipInfo) selectedItem;
                if (!relation.getElements().isEmpty()) {
                    RelationshipElement elem = relation.getElements().iterator().next();
                    Attribute attr = new Attribute(new Name(elem.getName()));
                    attr.setType(new Type(relation.getTargetClass()));
                    cppModel.delete(className, attr);
                }
            }
        }
    }

    private String formatCppAttribute(Attribute attr, CppHeaderClass cls) {
        StringBuilder sb = new StringBuilder();
        sb.append(attr.getVisibility())
                .append(" ")
                .append(attr.getName())
                .append(" : ")
                .append(attr.getType());

        Set<Modifier> modifiers = cls.getModifiers(attr.getName().getNameText());
        if (!modifiers.isEmpty()) {
            sb.append(" {")
                    .append(modifiers.stream()
                            .map(mod -> mod.getCppText(false)) // メソッド参照を修正
                            .collect(Collectors.joining(", ")))
                    .append("}");
        }
        return sb.toString();
    }

    private String formatCppOperation(Operation op, CppHeaderClass cls) {
        StringBuilder sb = new StringBuilder();
        sb.append(op.getVisibility())
                .append(" ")
                .append(op.getName())
                .append("(");

        if (!op.getParameters().isEmpty()) {
            sb.append(op.getParameters().stream()
                    .map(param -> param.getType() + " " + param.getName())
                    .collect(Collectors.joining(", ")));
        }
        sb.append(") : ")
                .append(op.getReturnType());

        Set<Modifier> modifiers = cls.getModifiers(op.getName().getNameText());
        if (!modifiers.isEmpty()) {
            sb.append(" {")
                    .append(modifiers.stream()
                            .map(mod -> mod.getCppText(false)) // メソッド参照を修正
                            .collect(Collectors.joining(", ")))
                    .append("}");
        }
        return sb.toString();
    }

    private String formatCppRelationship(RelationshipInfo relation) {
        StringBuilder sb = new StringBuilder();
        sb.append(relation.getType().name())
                .append(" -> ")
                .append(relation.getTargetClass());

        if (!relation.getElements().isEmpty()) {
            RelationshipElement elem = relation.getElements().iterator().next();
            sb.append(" (")
                    .append(elem.getVisibility())
                    .append(" ")
                    .append(elem.getName())
                    .append(")");
        }
        return sb.toString();
    }

    private boolean isJavaComposition(Type type) {
        for (Class umlClass : javaModel.getUmlClassList()) {
            if (type.getName().getNameText().equals(umlClass.getName())) {
                return true;
            }
        }
        return false;
    }
}