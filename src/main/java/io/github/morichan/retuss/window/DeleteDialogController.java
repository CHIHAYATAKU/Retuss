package io.github.morichan.retuss.window;

import io.github.morichan.retuss.window.diagram.OperationGraphic;
import io.github.morichan.retuss.window.diagram.sequence.CombinedFragment;
import io.github.morichan.retuss.window.diagram.sequence.Interaction;
import io.github.morichan.retuss.window.diagram.sequence.InteractionFragment;
import io.github.morichan.retuss.window.diagram.sequence.InteractionOperand;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import java.util.ArrayList;

public class DeleteDialogController {
    @FXML
    private TreeView sdTreeView;

    @FXML
    private Button deleteButton;

    private MainController mainController;
    private SequenceDiagramDrawer sequenceDiagramDrawer;
    private String classId;
    private String operationId;
    private ArrayList sdTreeItem = new ArrayList();
    private Interaction targetInteraction;

    public void initialize(MainController mainController, String classId, String operationId) {
        this.mainController = mainController;
        this.sequenceDiagramDrawer = mainController.getSequenceDiagramDrawer();
        this.classId = classId;
        this.operationId = operationId;
        this.targetInteraction = sequenceDiagramDrawer.getUmlPackage().searchOperatingGraphics(operationId).getInteraction();

        initSdTreeview();
    }

    private void initSdTreeview() {
        sdTreeItem.add(targetInteraction);
        TreeItem<String> root = new TreeItem<>("メッセージ" + targetInteraction.getMessage().getMessageSignature());
        root.setExpanded(true);
        for (InteractionFragment interactionFragment : targetInteraction.getMessage().getInteractionFragmentList()) {
            if (interactionFragment instanceof CombinedFragment) {
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                sdTreeItem.add(cf);
                TreeItem<String> cfItem = new TreeItem<>("複合フラグメント" + cf.getInteractionOperandKind());
                cfItem.setExpanded(true);
                for (InteractionOperand io : cf.getInteractionOperandList()) {
                    TreeItem<String> ioItem;
                    sdTreeItem.add(io);
                    if (io.getGuard().isEmpty()) {
                        ioItem = new TreeItem<>(cf.getInteractionOperandKind() + "(" + cf.getTextNextToKind() + ")");
                    } else {
                        ioItem = new TreeItem<>("ガード条件 [ " + io.getGuard() + " ]");
                    }

                    for (InteractionFragment interactionFragmentInCf : io.getInteractionFragmentList()) {
                        ioItem.getChildren().add(new TreeItem<>("メッセージ " + interactionFragmentInCf.getMessage().getMessageSignature()));
                        sdTreeItem.add(interactionFragmentInCf);
                    }
                    ioItem.setExpanded(true);
                    cfItem.getChildren().add(ioItem);
                }
                root.getChildren().add(cfItem);
            } else {
                root.getChildren().add(new TreeItem<>("メッセージ " + interactionFragment.getMessage().getMessageSignature()));
                sdTreeItem.add(interactionFragment);
            }
        }
        sdTreeView.setRoot(root);
        sdTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @FXML
    private void delete() {
        ObservableList<Integer> selectedIndices = sdTreeView.getSelectionModel().getSelectedIndices();
        if (selectedIndices.size() == 0) {
            return;
        }

        for (Integer index : selectedIndices) {
            if (sdTreeItem.get(index) instanceof InteractionFragment) {
                targetInteraction.deleteInteractionFragment((InteractionFragment) sdTreeItem.get(index));
            } else if (sdTreeItem.get(index) instanceof InteractionOperand) {
                // interactionOperandが所属するCombinedFragmentを探す
                for (int i=index; i>0; i--) {
                    if (sdTreeItem.get(i) instanceof CombinedFragment) {
                        CombinedFragment cf = (CombinedFragment) sdTreeItem.get(i);
                        cf.deleteInteractionOperand((InteractionOperand) sdTreeItem.get(index));

                        // InteractionOperandがすべてなくなったCFは削除する
                        if (cf.getInteractionOperandList().size() == 0) {
                            targetInteraction.deleteInteractionFragment(cf);
                        }
                    }
                }
            }
        }

        Stage stage = (Stage) deleteButton.getScene().getWindow();
        stage.close();
        sequenceDiagramDrawer.draw();
        mainController.getCodeController().createCodeTabs();
    }


}
