package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.Class;
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
    @FXML private Label messageLabel;
    @FXML private ComboBox relationshipComboBox;
    @FXML private ComboBox class1ComboBox;
    @FXML private ComboBox class2ComboBox;
    @FXML private ImageView relationshipImageView;
    @FXML private Button createBtn;
    private Model model = Model.getInstance();
    final private List<String> relationshipList = Arrays.asList("Composition", "Generalization");

    public void initialize() {
        // Relationshipコンボボックに関連の種類を設定
        for(String relationship : relationshipList) {
            relationshipComboBox.getItems().add(relationship);
        }
        // Relationshioコンボボックスに初期値を設定
        relationshipComboBox.setValue(relationshipList.get(0));
        relationshipImageView.setImage(new Image("composition.png"));

        // 2つのクラスコンボボックスにumlClass名を設定
        for(Class umlClass : model.getUmlClassList()) {
            class1ComboBox.getItems().add(umlClass.getName());
            class2ComboBox.getItems().add(umlClass.getName());
        }
        // クラスコンボボックスの初期値を設定
        class1ComboBox.setValue(model.getUmlClassList().get(0).getName());
        class2ComboBox.setValue(model.getUmlClassList().get(0).getName());
    }

    @FXML private void changeRelationshipImage() {
        String selectedRelationship = relationshipComboBox.getValue().toString();
        if(selectedRelationship.equals(relationshipList.get(0))) {
            relationshipImageView.setImage(new Image("composition.png"));
        } else if (selectedRelationship.equals(relationshipList.get(1))) {
            relationshipImageView.setImage(new Image("generalization.png"));
        }
    }

    @FXML private void createRelationship() {
        Optional<Class> class1Optional = model.findClass(class1ComboBox.getValue().toString());
        Optional<Class> class2Optional = model.findClass(class2ComboBox.getValue().toString());

        if(class1Optional.isEmpty() || class2Optional.isEmpty()) {
            messageLabel.setText("Class is not exsting.");
            return;
        }

        if(relationshipComboBox.getValue().equals(relationshipList.get(0))) {
            model.addComposition(class1ComboBox.getValue().toString(), class2ComboBox.getValue().toString());
        } else if(relationshipComboBox.getValue().equals(relationshipList.get(1))) {
            model.addGeneralization(class1ComboBox.getValue().toString(), class2ComboBox.getValue().toString());
        }

        // ダイアログを閉じる
        Stage stage = (Stage) createBtn.getScene().getWindow();
        stage.close();

    }

}
