package io.github.morichan.retuss.controller;

import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class RelationshipDialogController {
    @FXML
    private Label messageLabel;
    @FXML
    private ComboBox relationshipComboBox;
    @FXML
    private ComboBox class1ComboBox;
    @FXML
    private ComboBox class2ComboBox;
    @FXML
    private ComboBox visibilityComboBox;
    @FXML
    private ImageView relationshipImageView;
    @FXML
    private Button createBtn;
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private UmlController umlController;
    // private final List<String> relationshipTypes = Arrays.asList(
    // "Composition (Value Type)",
    // "Composition (Annotated Pointer)",
    // "Association (Pointer)",
    // "Aggregation (Annotated Pointer)",
    // "Inheritance",
    // "Realization");
    private final List<String> relationshipTypes = Arrays.asList(
            "Composition", "Aggregation");

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        initialize();
    }

    public void initialize() {
        relationshipTypes.forEach(type -> relationshipComboBox.getItems().add(type));
        relationshipComboBox.setValue(relationshipTypes.get(0));

        visibilityComboBox.getItems().addAll("public", "protected", "private");
        visibilityComboBox.setValue("private");

        // 初期状態では可視性選択を非表示に
        visibilityComboBox.setVisible(false);

        initializeClassList();
        updateRelationshipImage();
    }

    private void initializeClassList() {
        class1ComboBox.getItems().clear();
        class2ComboBox.getItems().clear();

        if (umlController.isJavaSelected()) {
            for (Class cls : javaModel.getUmlClassList()) {
                class1ComboBox.getItems().add(cls.getName());
                class2ComboBox.getItems().add(cls.getName());
            }
        } else if (umlController.isCppSelected()) {
            String selectedType = relationshipComboBox.getValue().toString();
            if (selectedType.equals("Realization")) {
                // Realizationの場合は、インターフェースクラスのみを表示
                for (CppHeaderClass cls : cppModel.getHeaderClasses()) {
                    if (cls.getInterface()) {
                        class2ComboBox.getItems().add(cls.getName());
                    } else {
                        class1ComboBox.getItems().add(cls.getName());
                    }
                }
            } else {
                for (CppHeaderClass cls : cppModel.getHeaderClasses()) {
                    class1ComboBox.getItems().add(cls.getName());
                    class2ComboBox.getItems().add(cls.getName());
                }
            }
        }

        if (!class1ComboBox.getItems().isEmpty()) {
            class1ComboBox.setValue(class1ComboBox.getItems().get(0));
            class2ComboBox.setValue(class2ComboBox.getItems().get(0));
        }
    }

    @FXML
    private void updateRelationshipImage() {
        String selectedType = relationshipComboBox.getValue().toString();

        // 可視性選択の表示/非表示を制御
        boolean needsVisibility = !selectedType.equals("Inheritance") &&
                !selectedType.equals("Realization");
        visibilityComboBox.setVisible(needsVisibility);

        // 画像の更新
        String imageName;
        switch (selectedType) {
            case "Composition (Value Type)":
            case "Composition (Annotated Pointer)":
                imageName = "composition.png";
                break;
            case "Association (Pointer)":
                imageName = "association.png";
                break;
            case "Aggregation (Annotated Pointer)":
                imageName = "aggregation.png";
                break;
            case "Inheritance":
                imageName = "generalization.png";
                break;
            case "Realization":
                imageName = "realization.png";
                break;
            default:
                imageName = "association.png";
                break;
        }

        relationshipImageView.setImage(new Image(imageName));

        // クラスリストの更新
        refreshClassList();
    }

    private void refreshClassList() {
        class1ComboBox.getItems().clear();
        class2ComboBox.getItems().clear();

        if (umlController.isCppSelected()) {
            String selectedType = relationshipComboBox.getValue().toString();
            if (selectedType.equals("Realization")) {
                // Realizationの場合のみインターフェース判定
                for (CppHeaderClass cls : cppModel.getHeaderClasses()) {
                    if (cls.getInterface()) {
                        class2ComboBox.getItems().add(cls.getName());
                    } else {
                        class1ComboBox.getItems().add(cls.getName());
                    }
                }
            } else {
                // その他の場合は全クラスを表示
                for (CppHeaderClass cls : cppModel.getHeaderClasses()) {
                    class1ComboBox.getItems().add(cls.getName());
                    class2ComboBox.getItems().add(cls.getName());
                }
            }
        }

        if (!class1ComboBox.getItems().isEmpty()) {
            class1ComboBox.setValue(class1ComboBox.getItems().get(0));
        }
        if (!class2ComboBox.getItems().isEmpty()) {
            class2ComboBox.setValue(class2ComboBox.getItems().get(0));
        }
    }

    @FXML
    private void createRelationship() {
        try {
            String sourceClass = class1ComboBox.getValue().toString();
            String targetClass = class2ComboBox.getValue().toString();
            String relationType = relationshipComboBox.getValue().toString();
            Visibility visibility = getSelectedVisibility();

            if (umlController.isJavaSelected()) {
                addJavaRelationship(sourceClass, targetClass, relationType);
            } else if (umlController.isCppSelected()) {
                addCppRelationship(sourceClass, targetClass, relationType, visibility);
            }

            Stage stage = (Stage) class1ComboBox.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            messageLabel.setText("Failed to create relationship: " + e.getMessage());
        }
    }

    private void addCppRelationship(String sourceClass, String targetClass,
            String relationType, Visibility visibility) {
        switch (relationType) {
            case "Composition (Value Type)":
                cppModel.addComposition(sourceClass, targetClass, visibility);
                break;
            case "Composition (Annotated Pointer)":
                cppModel.addCompositionWithAnnotation(sourceClass, targetClass, visibility);
                break;
            case "Association (Pointer)":
                cppModel.addAssociation(sourceClass, targetClass, visibility);
                break;
            case "Aggregation (Annotated Pointer)":
                cppModel.addAggregationWithAnnotation(sourceClass, targetClass, visibility);
                break;
            case "Inheritance":
                cppModel.addInheritance(sourceClass, targetClass);
                break;
            case "Realization":
                cppModel.addRealization(sourceClass, targetClass);
                break;
        }
    }

    private Visibility getSelectedVisibility() {
        String visibility = visibilityComboBox.getValue().toString();
        switch (visibility) {
            case "public":
                return Visibility.Public;
            case "protected":
                return Visibility.Protected;
            default:
                return Visibility.Private;
        }
    }

    private void addJavaRelationship(String sourceClass, String targetClass, String relationType) {
        switch (relationType) {
            case "Composition":
                javaModel.addComposition(sourceClass, targetClass);
                break;
            case "Aggregation":
                // Java doesn't distinguish between composition and aggregation
                javaModel.addComposition(sourceClass, targetClass);
                break;
            case "Association":
                // Handle as composition in Java
                javaModel.addComposition(sourceClass, targetClass);
                break;
            case "Inheritance":
                javaModel.addGeneralization(sourceClass, targetClass);
                break;
        }
    }

}
