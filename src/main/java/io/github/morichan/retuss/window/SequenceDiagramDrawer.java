package io.github.morichan.retuss.window;

import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.language.uml.Package;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import io.github.morichan.retuss.window.diagram.sequence.Interaction;
import io.github.morichan.retuss.window.utility.UtilityJavaFXComponent;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SequenceDiagramDrawer {
    private String diagramFont = "Consolas";
    double space = 5.0;

    private TabPane tabPaneInSequenceTab;
    private Package umlPackage;
    private Map<String, Map<String, Interaction>> interactionSetFromClassNameAndOperation;
    private UtilityJavaFXComponent utilComponent = new UtilityJavaFXComponent();


    public void setSequenceDiagramTabPane(TabPane tabPane) {
        tabPaneInSequenceTab = tabPane;
    }

    public TabPane getTabPaneInSequenceTab() {
        return tabPaneInSequenceTab;
    }

    public void setUmlPackage(Package umlPackage) {
        this.umlPackage = umlPackage;
        resetLifelineFlag();
    }

    public Package getUmlPackage() {
        return umlPackage;
    }

    public void resetLifelineFlag() {
        for (Class umlClass : this.umlPackage.getClasses()) {
            for (OperationGraphic og : umlClass.getOperationGraphics()) {
                if (og.getInteraction() != null)
                    og.getInteraction().resetLifelineFlag();
            }
        }
    }

    public void createSequenceTabContent(Tab sequenceDiagramTab) {
        interactionSetFromClassNameAndOperation = new HashMap<>();

        for (Class umlClass : umlPackage.getClasses()) {
            Map<String, Interaction> interactionSetFromOperation = new HashMap<>();
            String className = umlClass.getName();
            Tab classTab = new Tab(className);
            classTab.setOnSelectionChanged(event -> draw());
            classTab.setId(className);

            for (OperationGraphic og : umlClass.getOperationGraphics()) {
                String tabName = createTabName(umlClass, og);
                Tab operationTab = createOperationTab(tabName);
                operationTab.setId(og.getOperation().toString());
                addOperationTabOnSequenceTab(classTab, operationTab);
                interactionSetFromOperation.put(og.getOperation().toString(), og.getInteraction());
            }

            addSequenceTabInSD(sequenceDiagramTab, classTab);
            interactionSetFromClassNameAndOperation.put(className, interactionSetFromOperation);
        }
    }

    public void draw() {
        if (interactionSetFromClassNameAndOperation == null || interactionSetFromClassNameAndOperation.isEmpty())
            return;

        int classIndex = 0;

        for (Tab classTab : tabPaneInSequenceTab.getTabs()) {
            if (!classTab.isSelected()) {
                classIndex++;
                continue;
            }

            if (classTab.getContent() == null)
                break;

            int operationIndex = 0;

            for (Tab operationTab : ((TabPane) ((AnchorPane) classTab.getContent()).getChildren().get(0)).getTabs()) {
                if (!operationTab.isSelected()) {
                    operationIndex++;
                    continue;
                }

                Class umlClass = umlPackage.getClasses().get(classIndex);
                OperationGraphic og = umlClass.getOperationGraphics().get(operationIndex);

                ScrollPane paneUnderCanvas = (ScrollPane) ((AnchorPane) operationTab.getContent()).getChildren().get(0);
                Canvas canvas = (Canvas) paneUnderCanvas.getContent();
                canvas.setWidth(tabPaneInSequenceTab.getWidth());
                canvas.setHeight(tabPaneInSequenceTab.getHeight());
                canvas.setOnMouseClicked(e -> createEvent(e, paneUnderCanvas, classTab.getId(), operationTab.getId()));

                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                drawDiagramCanvasEdge(gc);
                drawSequenceNameArea(gc, og.getOperation().toString());

                interactionSetFromClassNameAndOperation.get(classTab.getId()).get(operationTab.getId()).draw(gc);

                break;
            }

            break;
        }

        resetLifelineFlag();
    }

    private void drawSequenceNameArea(GraphicsContext gc, String text) {
        Text sd = new Text("sd");
        Text name = new Text(text);
        double titleFontSize = 15.0;

        sd.setFont(Font.font(diagramFont, FontWeight.BOLD, titleFontSize));
        name.setFont(Font.font(diagramFont, FontWeight.LIGHT, titleFontSize));

        gc.setFill(Color.BLACK);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(sd.getFont());
        gc.fillText(sd.getText(), space * 2, space * 2 + sd.getLayoutBounds().getHeight());
        gc.setFont(name.getFont());
        gc.fillText(name.getText(), space * 3 + sd.getLayoutBounds().getWidth(),
                space * 2 + sd.getLayoutBounds().getHeight());

        Point2D rightBottomCorner = new Point2D(
                space * 3 + sd.getLayoutBounds().getWidth() + name.getLayoutBounds().getWidth(),
                space * 2 + sd.getLayoutBounds().getHeight());
        gc.setStroke(Color.BLACK);
        gc.strokeLine(space, rightBottomCorner.getY() + space * 2, rightBottomCorner.getX(),
                rightBottomCorner.getY() + space * 2);
        gc.strokeLine(rightBottomCorner.getX() + space * 2, space, rightBottomCorner.getX() + space * 2,
                rightBottomCorner.getY());
        gc.strokeLine(rightBottomCorner.getX(), rightBottomCorner.getY() + space * 2,
                rightBottomCorner.getX() + space * 2, rightBottomCorner.getY());
    }

    /**
     * クラス図キャンバスの縁を描画する。
     */
    private void drawDiagramCanvasEdge(GraphicsContext gc) {
        double width = gc.getCanvas().getWidth();
        double height = gc.getCanvas().getHeight();
        gc.setStroke(Color.BLACK);
        gc.strokeRect(space, space, width - space * 2, height - space * 2);
    }

    private String createTabName(Class umlClass, OperationGraphic operationGraphic) {
        StringBuilder sb = new StringBuilder();
        int sameOperationNameCount = 0;

        for (int i = 0; i < umlClass.getOperationGraphics().size(); i++) {
            if (umlClass.getOperationGraphics().get(i).getOperation().getName().getNameText()
                    .equals(operationGraphic.getOperation().getName().getNameText())) {
                sameOperationNameCount++;
            }
        }

        if (sameOperationNameCount <= 1) {
            return operationGraphic.getOperation().getName().getNameText();
        }

        try {
            sb.append(operationGraphic.getOperation().getName().getNameText());
            sb.append("(");
            List<String> params = new ArrayList<>();
            operationGraphic.getOperation().getParameters().forEach(p -> params.add(p.toString()));
            sb.append(String.join(", ", params));
            sb.append(")");

        } catch (IllegalStateException e) {
            sb = new StringBuilder();
            sb.append(operationGraphic.getOperation().getName().getNameText());
            sb.append("()");
        }

        return sb.toString();
    }

    private Tab createOperationTab(String name) {
        Tab tab = new Tab(name);

        AnchorPane anchorPaneInChildTab = new AnchorPane();
        Canvas canvas = new Canvas();
        ScrollPane scrollPane = new ScrollPane(canvas);
        AnchorPane.setTopAnchor(scrollPane, 0.0);
        AnchorPane.setBottomAnchor(scrollPane, 0.0);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);
        anchorPaneInChildTab.getChildren().add(scrollPane);

        tab.setContent(anchorPaneInChildTab);
        tab.setOnSelectionChanged(e -> draw());

        return tab;
    }

    private void addOperationTabOnSequenceTab(Tab parentTab, Tab childTab) {

        if (parentTab.getContent() == null) {
            AnchorPane anchorPane = new AnchorPane();
            TabPane tabPane = new TabPane(childTab);

            AnchorPane.setTopAnchor(tabPane, 0.0);
            AnchorPane.setBottomAnchor(tabPane, 0.0);
            AnchorPane.setLeftAnchor(tabPane, 0.0);
            AnchorPane.setRightAnchor(tabPane, 0.0);
            anchorPane.getChildren().add(tabPane);

            parentTab.setContent(anchorPane);
        } else {
            ((TabPane) ((AnchorPane) parentTab.getContent()).getChildren().get(0)).getTabs().add(childTab);
        }
    }

    private void addSequenceTabInSD(Tab parentTab, Tab childTab) {
        AnchorPane anchorPaneOnTabPane = (AnchorPane) parentTab.getContent();
        BorderPane borderPaneOnAnchorPaneOnTabPane = (BorderPane) anchorPaneOnTabPane.getChildren().get(0);
        AnchorPane anchorPaneOnVBox = (AnchorPane) borderPaneOnAnchorPaneOnTabPane.getCenter();
        TabPane tabPane = (TabPane) anchorPaneOnVBox.getChildren().get(0);
        tabPane.getTabs().add(childTab);
    }

    private void createEvent(MouseEvent mouse, ScrollPane pane, String classId, String operationId) {
        Interaction interaction = interactionSetFromClassNameAndOperation.get(classId).get(operationId);

        if (mouse.getButton() == MouseButton.PRIMARY) {
            // メッセージの追加
        } else if (mouse.getButton() == MouseButton.SECONDARY) {
            ContextMenu popup = new ContextMenu();

            // MenuItem changeMenu = new MenuItem(interaction.getMessage().getName() +
            // "メッセージの変更");
            // changeMenu.setOnAction(e -> changeSequence(classId, operationId));
            MenuItem createMessageMenu = new MenuItem("メッセージの作成");
            createMessageMenu.setOnAction(e -> createMessage(classId, operationId));
            MenuItem deleteMenu = new MenuItem(interaction.getMessage().getName() + "メッセージをモデルから削除");
            deleteMenu.setOnAction(e -> deleteSequence(classId, operationId));
            // popup.getItems().add(changeMenu);
            popup.getItems().add(createMessageMenu);
            popup.getItems().add(deleteMenu);

            pane.setContextMenu(popup);
        }
    }

    private void createMessage(String classId, String operationId) {
        // 作成するメッセージの情報を入力する画面表示
        utilComponent.showCreateMessageInputDialog();
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/createMessageInputDialog.fxml"));
            Parent parent = fxmlLoader.load();
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("メッセージの作成");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch(Exception e) {

        }


        // メッセージの追加
    }

    private void deleteSequence(String classId, String operationId) {

        int classIndex = -1;
        int operationIndex = -1;

        becauseOperationIsSpecify: for (int i = 0; i < umlPackage.getClasses().size(); i++) {
            if (!umlPackage.getClasses().get(i).getName().equals(classId))
                continue;

            for (int j = 0; j < umlPackage.getClasses().get(i).getOperationGraphics().size(); j++) {
                if (!umlPackage.getClasses().get(i).getOperationGraphics().get(j).getOperation().toString()
                        .equals(operationId))
                    continue;
                classIndex = i;
                operationIndex = j;
                break becauseOperationIsSpecify;
            }
        }

        if (operationIndex == -1)
            return;

        interactionSetFromClassNameAndOperation.get(classId).remove(operationId);
        umlPackage.getClasses().get(classIndex).getOperationGraphics().remove(operationIndex);

        ((TabPane) ((AnchorPane) tabPaneInSequenceTab.getTabs().get(classIndex).getContent()).getChildren().get(0))
                .getTabs().remove(operationIndex);
        draw();
    }
}
