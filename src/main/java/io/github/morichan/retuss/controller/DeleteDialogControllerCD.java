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
                cdTreeItemList.add(op);
                TreeItem<String> opItem = new TreeItem<>(formatCppOperation(op, cls));
                operationsNode.getChildren().add(opItem);
            }
            classTreeItem.getChildren().add(operationsNode);

            // 関係ノード
            cdTreeItemList.add("relations");
            TreeItem<String> relationsNode = new TreeItem<>("Relations");
            for (RelationshipInfo relation : cls.getRelationshipManager().getAllRelationships()) {
                String relationText = formatCppRelationship(relation);
                if (relationText != null) {
                    System.out.println("Adding relation: " + relationText); // デバッグ用
                    cdTreeItemList.add(relation);
                    TreeItem<String> relationItem = new TreeItem<>(relationText);
                    relationsNode.getChildren().add(relationItem);
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
        // 選択されたインデックスのリストを取得
        ObservableList<Integer> selectedIndices = cdTreeView.getSelectionModel().getSelectedIndices();

        // インデックスが空でないか、無効なインデックスでないか確認
        if (selectedIndices.isEmpty() || selectedIndices.get(0) == 0) {
            System.out.println("No valid selection for deletion.");
            return;
        }

        System.out.println("cdTreeItemList contents:");
        for (int i = 0; i < cdTreeItemList.size(); i++) {
            Object item = cdTreeItemList.get(i);
            System.out.println("Index " + i + ": " + item);
        }

        // 選択されたインデックスとその中身を表示
        System.out.println("Selected indices: " + selectedIndices); // インデックスの表示
        for (int index : selectedIndices) {
            Object selectedItem = cdTreeItemList.get(index);
            System.out.println("Item at index " + index + ": " + selectedItem); // 各インデックスの中身を表示
        }

        try {
            // 言語がJavaかC++に応じて削除処理を呼び出す
            if (umlController.isJavaSelected()) {
                deleteJavaElement(selectedIndices);
            } else if (umlController.isCppSelected()) {
                deleteCppElement(selectedIndices);
            }

            // 削除後にツリーを再構築
            initialize();

            if (!cdTreeItemList.isEmpty()) {
                // 削除したアイテムの次のアイテムや前のアイテムを選択するなどのロジック
                int nextIndex = selectedIndices.get(0) < cdTreeItemList.size() ? selectedIndices.get(0)
                        : cdTreeItemList.size() - 1;
                cdTreeView.getSelectionModel().select(nextIndex);
            }

            // 閉じる
            Stage stage = (Stage) cdTreeView.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            System.out.println("Deletion failed: " + e.getMessage());
            e.printStackTrace();
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
        // チェック: 選択項目がない、または無効なインデックスの場合は終了
        if (selectedIndices.isEmpty() || selectedIndices.get(0) == 0) {
            System.out.println("No valid selection or invalid index. Exiting deletion.");
            return;
        }

        // 最初の選択された項目を取得
        int selectedIndex = selectedIndices.get(0);
        System.out.println("Selected index: " + selectedIndex); // 選択されたインデックスを表示
        Object selectedItem = cdTreeItemList.get(selectedIndex); // 選択されたアイテムを取得
        String className = "";

        // 選択された項目に応じた削除処理
        if (selectedItem instanceof CppHeaderClass) {
            // クラス全体を削除
            className = ((CppHeaderClass) selectedItem).getName();
            System.out.println("Attempting to delete class: " + className); // クラス名を表示
            cppModel.delete(className);
        } else {
            // 親クラスを取得
            className = findParentClassName(selectedIndex);
            if (className.isEmpty()) {
                System.out.println("Parent class name is empty. Exiting deletion.");
                return;
            }

            if (selectedItem instanceof Attribute) {
                // 属性の削除
                // インデックスを2つ戻す
                int adjustedIndex = selectedIndex;
                if (adjustedIndex >= 0) {
                    Attribute selectedAttribute = (Attribute) cdTreeItemList.get(adjustedIndex); // 修正されたインデックスを使用
                    System.out.println("Attempting to delete attribute: " + selectedAttribute.getName().getNameText()
                            + " from class: " + className); // 属性名を表示
                    cppModel.delete(className, selectedAttribute);
                }
            } else if (selectedItem instanceof Operation) {
                // 操作の削除
                // インデックスを2つ戻す
                int adjustedIndex = selectedIndex;
                if (adjustedIndex >= 0) {
                    Operation selectedOperation = (Operation) cdTreeItemList.get(adjustedIndex); // 修正されたインデックスを使用
                    System.out.println("Attempting to delete operation: " + selectedOperation.getName().getNameText()
                            + " from class: " + className); // 操作名を表示
                    cppModel.delete(className, selectedOperation);
                }
            } else if (selectedItem instanceof RelationshipInfo) {
                // 関係の削除
                // インデックスを3つ戻す（関係の場合はさらに戻す必要がある）
                int adjustedIndex = selectedIndex;
                if (adjustedIndex >= 0) {
                    RelationshipInfo selectedRelationship = (RelationshipInfo) cdTreeItemList.get(adjustedIndex); // 修正されたインデックスを使用
                    System.out.println("Attempting to delete relationship involving class: " + className); // 関係を表示
                    handleRelationshipDeletion(className, selectedRelationship);
                }
            } else {
                System.out.println("Unknown item type selected. No action taken.");
            }
        }
    }

    /**
     * 上位クラス (CppHeaderClass) の名前を取得する。
     *
     * @param currentIndex 現在の選択インデックス
     * @return クラス名 (見つからない場合は空文字)
     */
    private String findParentClassName(int currentIndex) {
        for (int i = currentIndex - 1; i > 0; i--) {
            Object item = cdTreeItemList.get(i);
            if (item instanceof CppHeaderClass) {
                return ((CppHeaderClass) item).getName();
            }
        }
        return "";
    }

    /**
     * RelationshipInfo を基に関係の削除を実行する。
     *
     * @param className 関係元のクラス名
     * @param relation  RelationshipInfo オブジェクト
     */
    private void handleRelationshipDeletion(String className, RelationshipInfo relation) {
        switch (relation.getType()) {
            case INHERITANCE:
                cppModel.removeInheritance(className, relation.getTargetClass());
                break;
            case REALIZATION:
                cppModel.removeRealization(className, relation.getTargetClass());
                break;
            default:
                System.out.println("Unhandled relationship type: " + relation.getType());
                break;
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
        if (op == null || cls == null) {
            System.out.println("Error: Null operation or class passed to formatCppOperation");
            return "Error: Null Operation or Class";
        }

        StringBuilder sb = new StringBuilder();
        try {
            // 可視性
            String visibility = (op.getVisibility() != null) ? op.getVisibility().toString() : "Unknown";
            sb.append(visibility).append(" ");

            // 名前
            String name = (op.getName() != null) ? op.getName().getNameText() : "Unnamed";
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
                System.out.println("Exception while fetching parameters for operation: " + name);
                e.printStackTrace();
                parameters = Collections.emptyList();
            }

            if (!parameters.isEmpty()) {
                sb.append(parameters.stream()
                        .map(param -> {
                            String type = (param.getType() != null) ? param.getType().toString() : "UnknownType";
                            String paramName = (param.getName() != null) ? param.getName().getNameText() : "Unnamed";
                            return type + " " + paramName;
                        })
                        .collect(Collectors.joining(", ")));
            }
            sb.append(")");

            // 戻り値
            String returnType = (op.getReturnType() != null) ? op.getReturnType().toString() : "void";
            sb.append(" : ").append(returnType);

            // 修飾子
            Set<Modifier> modifiers = cls.getModifiers(name);
            if (modifiers != null && !modifiers.isEmpty()) {
                sb.append(" {")
                        .append(modifiers.stream()
                                .map(mod -> mod.getCppText(false))
                                .collect(Collectors.joining(", ")))
                        .append("}");
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