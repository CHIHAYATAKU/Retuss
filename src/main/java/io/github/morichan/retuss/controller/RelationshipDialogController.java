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

import javax.management.relation.RelationType;

public class RelationshipDialogController {
    @FXML
    private Label messageLabel;
    @FXML
    private ComboBox<String> relationshipComboBox;
    @FXML
    private ComboBox<String> class1ComboBox;
    @FXML
    private ComboBox<String> class2ComboBox;
    @FXML
    private ComboBox<String> visibilityComboBox;
    @FXML
    private ImageView relationshipImageView;
    @FXML
    private Button createBtn;

    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private UmlController umlController;
    private final List<String> javaRelationshipTypes = Arrays.asList(
            "Composition", "Generalization");
    private final List<String> cppRelationshipTypes = Arrays.asList(
            "Composition (Value Type)",
            "Composition (Pointer & Annotation)",
            "Association (Pointer)",
            "Aggregation (Pointer & Annotation)",
            "Generalization",
            "Realization");

    public void initialize() {
        // まず可視性コンボボックスを初期化
        visibilityComboBox.getItems().addAll("public", "protected", "private");
        visibilityComboBox.setValue("private");
        visibilityComboBox.setVisible(false); // デフォルトでは非表示
    }

    public void setUmlController(UmlController controller) {
        this.umlController = controller;

        // 言語に応じて関係タイプを設定
        if (umlController.isJavaSelected()) {
            relationshipComboBox.getItems().addAll(javaRelationshipTypes);
            relationshipComboBox.setValue(javaRelationshipTypes.get(0));
            visibilityComboBox.setVisible(false);
        } else if (umlController.isCppSelected()) {
            relationshipComboBox.getItems().addAll(cppRelationshipTypes);
            relationshipComboBox.setValue(cppRelationshipTypes.get(0));
            visibilityComboBox.setVisible(true);
        }

        // initializeClassList();
        updateRelationshipImage();
    }

    private void initializeClassList() {
        class1ComboBox.getItems().clear();
        class2ComboBox.getItems().clear();

        if (umlController.isJavaSelected()) {
            List<Class> classes = javaModel.getUmlClassList();
            for (Class cls : classes) {
                class1ComboBox.getItems().add(cls.getName());
                class2ComboBox.getItems().add(cls.getName());
            }
        } else if (umlController.isCppSelected()) {
            String selectedType = relationshipComboBox.getValue();
            if ("Realization".equals(selectedType)) {
                for (CppHeaderClass cls : cppModel.getHeaderClasses()) {
                    if (cls.getInterface()) {
                        class2ComboBox.getItems().add(cls.getName());
                    } else {
                        class1ComboBox.getItems().add(cls.getName());
                    }
                }
            } else {
                List<CppHeaderClass> classes = cppModel.getHeaderClasses();
                for (CppHeaderClass cls : classes) {
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
    private void updateRelationshipImage() {
        if (relationshipComboBox.getValue() == null)
            return;

        String selectedType = relationshipComboBox.getValue();
        String imageName;

        if (selectedType.contains("Composition") || selectedType.equals("Composition")) {
            imageName = "composition.png";
        } else if (selectedType.equals("Generalization") ||
                selectedType.contains("Generalization")) {
            imageName = "generalization.png";
        } else if (selectedType.contains("Association")) {
            imageName = "association.png";
        } else if (selectedType.contains("Aggregation")) {
            imageName = "aggregation.png";
        } else if (selectedType.contains("Realization")) {
            imageName = "realization.png";
        } else {
            imageName = "composition.png";
        }

        relationshipImageView.setImage(new Image(imageName));

        if (umlController != null && umlController.isCppSelected()) {
            boolean needsVisibility = !selectedType.equals("Generalization") &&
                    !selectedType.equals("Realization");
            visibilityComboBox.setVisible(needsVisibility);

            if (selectedType.equals("Realization")) {
                class1ComboBox.getItems().clear();
                class2ComboBox.getItems().clear();
                for (CppHeaderClass cls : cppModel.getHeaderClasses()) {
                    if (cls.getInterface()) {
                        class2ComboBox.getItems().add(cls.getName());
                    } else {
                        class1ComboBox.getItems().add(cls.getName());
                    }
                }
            }
        }

        initializeClassList(); // クラスリストの更新
    }

    @FXML
    private void createRelationship() {
        try {
            String sourceClass = class1ComboBox.getValue();
            String targetClass = class2ComboBox.getValue();
            String relationType = relationshipComboBox.getValue();

            if (umlController.isJavaSelected()) {
                if ("Composition".equals(relationType)) {
                    javaModel.addComposition(sourceClass, targetClass);
                } else if ("Generalization".equals(relationType)) {
                    javaModel.addGeneralization(sourceClass, targetClass);
                }
            } else if (umlController.isCppSelected()) {
                Visibility visibility = getSelectedVisibility();
                addCppRelationship(sourceClass, targetClass, relationType, visibility);
            }

            Stage stage = (Stage) createBtn.getScene().getWindow();
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
            case "Composition (Pointer & Annotation)":
                cppModel.addCompositionWithAnnotation(sourceClass, targetClass, visibility);
                break;
            case "Association (Pointer)":
                cppModel.addAssociation(sourceClass, targetClass, visibility);
                break;
            case "Aggregation (Pointer & Annotation)":
                cppModel.addAggregationWithAnnotation(sourceClass, targetClass, visibility);
                break;
            case "Generalization":
                cppModel.addGeneralization(sourceClass, targetClass);
                break;
            case "Realization":
                cppModel.addRealization(sourceClass, targetClass);
                break;
        }
    }

    private Visibility getSelectedVisibility() {
        String visibility = visibilityComboBox.getValue().toString();
        Visibility selectedVisibility;
        switch (visibility) {
            case "public":
                selectedVisibility = Visibility.Public;
                break;
            case "protected":
                selectedVisibility = Visibility.Protected;
                break;
            default:
                selectedVisibility = Visibility.Private;
                break;
        }
        return selectedVisibility;
    }
}