package io.github.morichan.retuss.window;

import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.window.diagram.AttributeGraphic;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import io.github.morichan.retuss.window.diagram.sequence.InteractionFragment;
import io.github.morichan.retuss.window.diagram.sequence.Lifeline;
import io.github.morichan.retuss.window.diagram.sequence.MessageOccurrenceSpecification;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * <p> メッセージの削除ダイアログの管理クラス</p>
 */

public class DeleteMessageDialogController {
    @FXML
    private ListView<String> messageList;

    @FXML
    private Button deleteButton;

    private MainController mainController;
    private SequenceDiagramDrawer sequenceDiagramDrawer;
    private Class targetClass;
    private OperationGraphic targetOg;

    public void initialize(MainController mainController, String classId, String operationId) {
        this.mainController = mainController;
        this.sequenceDiagramDrawer = mainController.getSequenceDiagramDrawer();
        this.targetClass = sequenceDiagramDrawer.getUmlPackage().searchClass(classId);
        this.targetOg = sequenceDiagramDrawer.getUmlPackage().searchOperatingGraphics(operationId);

        for (MessageOccurrenceSpecification message: targetOg.getInteraction().getMessage().getMessages()) {
            messageList.getItems().add(message.getMessageSignature());
        }
    }

    @FXML
    private void deleteButtonHandler() {
        MultipleSelectionModel selectionModel = messageList.getSelectionModel();
        String selectedMessage = selectionModel.getSelectedItem().toString();
    }


}
