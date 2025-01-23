package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.model.uml.cpp.utils.Modifier;
import io.github.morichan.retuss.model.uml.cpp.utils.RelationType;
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
import io.github.morichan.fescue.feature.parameter.Parameter;

import java.util.ArrayList;
import java.util.Collections;
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
        System.out.println("DEBUG: Initializing DeleteDialogControllerCD");
        TreeItem<String> root = new TreeItem<>("Class List");
        root.setExpanded(true);
        cdTreeItemList.clear();
        cdTreeItemList.add("Class List");

        if (umlController != null) {
            if (umlController.isJavaSelected()) {
                System.out.println("DEBUG: Initializing Java tree");
                initializeJavaTree(root);
            } else if (umlController.isCppSelected()) {
                System.out.println("DEBUG: Initializing C++ tree");
                initializeCppTree(root);
            }
        }

        cdTreeView.setRoot(root);
        cdTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        printTreeContent(root, 0);
    }

    private void printTreeContent(TreeItem<String> item, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }
        System.out.println("DEBUG: Tree item: " + indent + item.getValue());
        for (TreeItem<String> child : item.getChildren()) {
            printTreeContent(child, depth + 1);
        }
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

            // 属性ノード
            cdTreeItemList.add("Attributes");
            TreeItem<String> attributesNode = new TreeItem<>("Attributes");
            System.out.println("Attributes found in " + cls.getName() + ": " + cls.getAttributeList().size()); // デバッグ用
            for (Attribute attr : cls.getAttributeList()) {
                System.out.println("Adding attribute: " + attr.getName()); // デバッグ用
                cdTreeItemList.add(attr);
                TreeItem<String> attrItem = new TreeItem<>(formatCppAttribute(attr, cls));
                attributesNode.getChildren().add(attrItem);
            }
            classTreeItem.getChildren().add(attributesNode);

            // 操作ノード
            cdTreeItemList.add("Operations");
            TreeItem<String> operationsNode = new TreeItem<>("Operations");
            System.out.println("Operations found in " + cls.getName() + ": " + cls.getOperationList().size()); // デバッグ用
            for (Operation op : cls.getOperationList()) {
                System.out.println("Adding operation: " + op.getName()); // デバッグ用
                if (!op.getName().getNameText().equals(cls.getName())
                        && !op.getName().getNameText().equals("~" + cls.getName())) {
                    cdTreeItemList.add(op);
                    TreeItem<String> opItem = new TreeItem<>(formatCppOperation(op, cls));
                    operationsNode.getChildren().add(opItem);
                }
            }
            classTreeItem.getChildren().add(operationsNode);

            // 関係ノード
            cdTreeItemList.add("relations");
            TreeItem<String> relationsNode = new TreeItem<>("Relations");
            for (RelationshipInfo relation : cls.getRelationshipManager().getAllRelationships()) {
                if (relation.getType() == RelationType.INHERITANCE || relation.getType() == RelationType.REALIZATION) {
                    String relationText = formatCppRelationship(relation);
                    if (relationText != null) {
                        System.out.println("Adding relation: " + relationText); // デバッグ用
                        cdTreeItemList.add(relation);
                        TreeItem<String> relationItem = new TreeItem<>(relationText);
                        relationsNode.getChildren().add(relationItem);
                    }
                }
            }
            classTreeItem.getChildren().add(relationsNode);

            // クラスをルートに追加
            root.getChildren().add(classTreeItem);
            System.out.println("Finished processing class: " + cls.getName()); // デバッグ用
        }
    }

    @FXML
    private void delete() {
        try {
            TreeItem<String> selectedItem = (TreeItem<String>) cdTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem == null || selectedItem.getValue().equals("Class List")) {
                System.err.println("DEBUG: No valid selection for deletion");
                messageLabel.setText("Please select an item to delete");
                return;
            }

            // 選択されたアイテムのパスを取得
            List<String> path = new ArrayList<>();
            TreeItem<String> current = selectedItem;
            while (current != null && !current.getValue().equals("Class List")) {
                path.add(0, current.getValue());
                current = current.getParent();
            }

            System.err.println("DEBUG: Selected path: " + String.join(" -> ", path));

            // 選択されたアイテムに対応するオブジェクトを特定
            int selectedIndex = findSelectedObjectIndex(path);
            System.err.println("DEBUG: Selected index in cdTreeItemList: " + selectedIndex);

            if (selectedIndex >= 0) {
                Object selectedObject = cdTreeItemList.get(selectedIndex);
                System.out.println("DEBUG: Selected object type: " + selectedObject.getClass().getName());
                System.out.println("DEBUG: Selected object toString: " + selectedObject.toString());

                // 言語に応じた削除処理
                if (umlController.isJavaSelected()) {
                    deleteJavaElement(selectedObject, path);
                } else if (umlController.isCppSelected()) {
                    deleteCppElement(selectedObject, path);
                }

                // ツリーを再構築
                initialize();
                messageLabel.setText("Item deleted successfully");
            } else {
                System.out.println("DEBUG: Could not find corresponding object for selection");
                messageLabel.setText("Could not find item to delete");
            }

        } catch (Exception e) {
            System.err.println("ERROR during deletion: " + e.getMessage());
            e.printStackTrace();
            messageLabel.setText("Error during deletion: " + e.getMessage());
        }
    }

    private int findSelectedObjectIndex(List<String> path) {
        if (path.isEmpty())
            return -1;

        String targetName = path.get(path.size() - 1);
        System.out.println("DEBUG: Looking for object with name: " + targetName);

        // 最後の要素の種類を判断する補助変数
        boolean isAttribute = path.contains("Attributes");
        boolean isOperation = path.contains("Operations");
        boolean isRelation = path.contains("Relations");

        CppHeaderClass cls = cppModel.getHeaderClasses().stream()
                .filter(c -> c.getName().equals(path.get(0)))
                .findFirst()
                .orElse(null);

        if (cls == null)
            return -1;

        for (int i = 0; i < cdTreeItemList.size(); i++) {
            Object item = cdTreeItemList.get(i);

            if (isAttribute && item instanceof Attribute) {
                Attribute attr = (Attribute) item;
                // formatAttributeStringの代わりにformatCppAttributeを使用
                String attrStr = formatCppAttribute(attr, cls);
                if (attrStr.equals(targetName)) {
                    return i;
                }
            } else if (isOperation && item instanceof Operation) {
                Operation op = (Operation) item;
                // formatOperationStringの代わりにformatCppOperationを使用
                String opStr = formatCppOperation(op, cls);
                if (opStr.equals(targetName)) {
                    return i;
                }
            } else if (isRelation && item instanceof RelationshipInfo) {
                RelationshipInfo rel = (RelationshipInfo) item;
                String relStr = formatRelationshipString(rel);
                System.out.println("DEBUG: Comparing relation: " + relStr + " with " + targetName);
                if (relStr.equals(targetName)) {
                    return i;
                }
            } else if (item instanceof Class || item instanceof CppHeaderClass) {
                String className = item instanceof Class ? ((Class) item).getName() : ((CppHeaderClass) item).getName();
                System.out.println("DEBUG: Comparing class: " + className + " with " + targetName);
                if (targetName.equals(className)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String formatAttributeString(Attribute attr) {
        StringBuilder sb = new StringBuilder();
        sb.append(attr.getVisibility().toString().substring(0, 1).toLowerCase())
                .append(" ")
                .append(attr.getName().getNameText())
                .append(" : ")
                .append(attr.getType().toString());
        return sb.toString();
    }

    private String formatOperationString(Operation op) {
        StringBuilder sb = new StringBuilder();
        sb.append(op.getVisibility().toString().substring(0, 1).toLowerCase())
                .append(" ")
                .append(op.getName().getNameText())
                .append("(");

        try {
            List<Parameter> params = op.getParameters();
            if (params != null && !params.isEmpty()) {
                List<String> paramStrings = new ArrayList<>();
                for (Parameter param : params) {
                    paramStrings.add(param.getName().getNameText() + " : " + param.getType().toString());
                }
                sb.append(String.join(", ", paramStrings));
            }

            sb.append(")");
            if (op.getReturnType() != null) {
                sb.append(" : ");
                sb.append(op.getReturnType().toString());
            }
        } catch (IllegalStateException e) {
        }
        return sb.toString();
    }

    private String formatRelationshipString(RelationshipInfo rel) {
        StringBuilder sb = new StringBuilder();
        sb.append(rel.getType().name())
                .append(" -> ")
                .append(rel.getTargetClass());

        RelationshipElement elem = rel.getElement();
        if (elem != null) {
            sb.append(" (")
                    .append(elem.getVisibility())
                    .append(" ")
                    .append(elem.getName())
                    .append(")");
        }
        return sb.toString();
    }

    private void deleteJavaElement(Object selectedObject, List<String> path) {
        System.out.println("DEBUG: Deleting Java element of type: " + selectedObject.getClass().getName());

        if (selectedObject instanceof Class) {
            Class javaClass = (Class) selectedObject;
            System.out.println("DEBUG: Deleting Java class: " + javaClass.getName());
            javaModel.delete(javaClass.getName());
        } else {
            String className = path.get(0); // パスの最初の要素がクラス名
            System.out.println("DEBUG: Parent class name: " + className);

            if (selectedObject instanceof Attribute) {
                System.out.println("DEBUG: Deleting attribute: " + ((Attribute) selectedObject).getName());
                javaModel.delete(className, (Attribute) selectedObject);
            } else if (selectedObject instanceof Operation) {
                System.out.println("DEBUG: Deleting operation: " + ((Operation) selectedObject).getName());
                javaModel.delete(className, (Operation) selectedObject);
            } else if (selectedObject instanceof String && selectedObject.equals("Generalization")) {
                System.out.println("DEBUG: Deleting generalization for class: " + className);
                javaModel.deleteSuperClass(className);
            }
        }
    }

    private void deleteCppElement(Object selectedObject, List<String> path) {
        System.out.println("DEBUG: Deleting C++ element of type: " + selectedObject.getClass().getName());

        if (selectedObject instanceof CppHeaderClass) {
            CppHeaderClass cppClass = (CppHeaderClass) selectedObject;
            System.out.println("DEBUG: Deleting C++ class: " + cppClass.getName());
            cppModel.delete(cppClass.getName());
        } else {
            String className = path.get(0); // パスの最初の要素がクラス名
            System.out.println("DEBUG: Parent class name: " + className);

            if (selectedObject instanceof Attribute) {
                System.out.println("DEBUG: Deleting attribute: " + ((Attribute) selectedObject).getName());
                cppModel.delete(className, (Attribute) selectedObject);
            } else if (selectedObject instanceof Operation) {
                System.out.println("DEBUG: Deleting operation: " + ((Operation) selectedObject).getName());
                cppModel.delete(className, (Operation) selectedObject);
            } else if (selectedObject instanceof RelationshipInfo) {
                RelationshipInfo relation = (RelationshipInfo) selectedObject;
                System.out.println(
                        "DEBUG: Deleting relationship: " + relation.getType() + " with " + relation.getTargetClass());
                handleRelationshipDeletion(className, relation);
            }
        }
    }

    private void handleRelationshipDeletion(String className, RelationshipInfo relation) {
        System.out.println("DEBUG: Handling relationship deletion for class " + className);
        System.out.println("DEBUG: Relationship type: " + relation.getType());
        System.out.println("DEBUG: Target class: " + relation.getTargetClass());

        switch (relation.getType()) {
            case INHERITANCE:
                cppModel.removeGeberalization(className, relation.getTargetClass());
                break;
            case REALIZATION:
                cppModel.removeRealization(className, relation.getTargetClass());
                break;
            default:
                System.out.println("DEBUG: Unhandled relationship type: " + relation.getType());
                break;
        }
    }

    private String formatCppAttribute(Attribute attr, CppHeaderClass cls) {
        StringBuilder sb = new StringBuilder();
        sb.append(attr.getVisibility());
        // Set<Modifier> modifiers = cls.getModifiers(attr.getName().getNameText());
        // if (!modifiers.isEmpty()) {
        // sb.append(" {")
        // .append(modifiers.stream()
        // .map(mod -> mod.getCppText(false)) // メソッド参照を修正
        // .collect(Collectors.joining(", ")))
        // .append("} ");
        // }
        sb.append(" ")
                .append(attr.getName())
                .append(" : ")
                .append(attr.getType());

        return sb.toString();
    }

    private String formatCppOperation(Operation op, CppHeaderClass cls) {
        if (op == null || cls == null) {
            System.out.println("Error: Null operation or class passed to formatCppOperation");
            return "Error: Null Operation or Class";
        }

        StringBuilder sb = new StringBuilder();
        try {
            // 可視性
            String visibility = (op.getVisibility() != null) ? op.getVisibility().toString() : "Unknown";
            sb.append(visibility).append(" ");
            String name = (op.getName() != null) ? op.getName().getNameText() : "Unnamed";
            boolean isConstructorOrDestructor = name.equals(cls.getName()) ||
                    name.equals("~" + cls.getName());
            // 修飾子
            // Set<Modifier> modifiers = cls.getModifiers(name);
            // if (modifiers != null && !modifiers.isEmpty()) {
            // sb.append("{")
            // .append(modifiers.stream()
            // .map(mod -> mod.getCppText(false))
            // .collect(Collectors.joining(", ")))
            // .append("} ");
            // }

            // 名前
            sb.append(name).append("(");

            // パラメータ
            List<Parameter> parameters = null;
            try {
                parameters = op.getParameters();
                if (parameters == null) {
                    System.out.println("Parameters are null for operation: " + name);
                    parameters = Collections.emptyList();
                }
            } catch (Exception e) {
                parameters = Collections.emptyList();
            }

            if (!parameters.isEmpty()) {
                sb.append(parameters.stream()
                        .map(param -> {
                            String type = (param.getType() != null) ? param.getType().toString() : "UnknownType";
                            String paramName = (param.getName() != null) ? param.getName().getNameText()
                                    : "Unnamed";
                            return paramName + " : " + type;
                        })
                        .collect(Collectors.joining(", ")));
            }
            sb.append(")");

            // 戻り値
            if (!isConstructorOrDestructor && op.getReturnType() != null &&
                    !op.getReturnType().toString().equals("")) {
                sb.append(" : ").append(op.getReturnType().toString());
            }
        } catch (Exception e) {
            System.out.println("Exception in formatCppOperation: " + e.getMessage());
            e.printStackTrace();
            return "Error formatting operation: " + e.getMessage();
        }

        return sb.toString();
    }

    private String formatCppRelationship(RelationshipInfo relation) {
        StringBuilder sb = new StringBuilder();
        sb.append(relation.getType().name())
                .append(" -> ")
                .append(relation.getTargetClass());

        RelationshipElement elem = relation.getElement();
        if (elem != null) {
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