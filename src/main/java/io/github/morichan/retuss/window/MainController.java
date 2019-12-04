package io.github.morichan.retuss.window;

import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.translator.Language;
import io.github.morichan.retuss.window.diagram.ContentType;
import io.github.morichan.retuss.window.diagram.NodeDiagram;
import io.github.morichan.retuss.window.diagram.RelationshipAttributeGraphic;
import io.github.morichan.retuss.window.utility.UtilityJavaFXComponent;
import io.github.morichan.retuss.language.uml.Package;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p> RETUSSメインウィンドウの動作管理クラス </p>
 *
 * <p>
 * {@link RetussWindow}クラスで用いているretussMain.fxmlファイルにおけるシグナルハンドラを扱います。
 * </p>
 */
public class MainController {
    @FXML
    private TreeView<String> classTree;

    @FXML
    private Tab classDiagramTab;
    @FXML
    private Button normalButtonInCD;
    @FXML
    private Button classButtonInCD;
    @FXML
    private Button noteButtonInCD;
    @FXML
    private Button compositionButtonInCD;
    @FXML
    private Button generalizationButtonInCD;
    @FXML
    private ScrollPane classDiagramScrollPane;
    @FXML
    private Canvas classDiagramCanvas;

    @FXML
    private Tab sequenceDiagramTab;
    @FXML
    private TabPane tabPaneInSequenceTab;
    @FXML
    private Button normalButtonInSD;
    @FXML
    private Button messageButtonInSD;

    private List<Button> buttonsInCD = new ArrayList<>();
    private List<Button> buttonsInSD = new ArrayList<>();

    private TextInputDialog mainWindowInputDialog;
    private File filePath = new File(System.getProperty("user.home")); // 初期ディレクトリをホームにする。

    private Stage mainStage;
    private Stage codeStage;
    private CodeController codeController;

    private UtilityJavaFXComponent util = new UtilityJavaFXComponent();
    private ClassDiagramDrawer classDiagramDrawer = new ClassDiagramDrawer();

    private SequenceDiagramDrawer sequenceDiagramDrawer = new SequenceDiagramDrawer();

    /**
     * <p> JavaFXにおけるデフォルトコンストラクタ </p>
     *
     * <p>
     * Javaにおける通常のコンストラクタ（ {@code Controller()} メソッド）は使えないため、
     * {@link RetussWindow} クラス内でのFXMLファイル読込み時に {@link #initialize()} メソッドを呼び出す仕様になっています。
     * </p>
     */
    @FXML
    private void initialize() {
        buttonsInCD.addAll(Arrays.asList(normalButtonInCD, classButtonInCD, noteButtonInCD, compositionButtonInCD, generalizationButtonInCD));
        buttonsInSD.addAll(Arrays.asList(normalButtonInSD, messageButtonInSD));
        GraphicsContext gc = classDiagramCanvas.getGraphicsContext2D();
        double scrollBarBreadth = 15.0;
        gc.getCanvas().setWidth(classDiagramScrollPane.getPrefWidth() - scrollBarBreadth);
        gc.getCanvas().setHeight(classDiagramScrollPane.getPrefHeight() - scrollBarBreadth);
        classDiagramDrawer = new ClassDiagramDrawer();
        classDiagramDrawer.setGraphicsContext(gc);
        classTree.setRoot(new TreeItem<>("Class"));
    }

    @FXML
    private void importJavaFile() {
        try {
            importCode(Language.Java, importFile(Language.Java));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void importCppFile() {
        try {
            importCode(Language.Cpp, importFile(Language.Cpp));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void selectClassDiagramTab() {
        selectNormalInCD();
    }

    @FXML
    private void selectSequenceDiagramTab() {
        normalButtonInSD.setDefaultButton(true);
        tabPaneInSequenceTab.getTabs().clear();
        sequenceDiagramDrawer.setSequenceDiagramTabPane(tabPaneInSequenceTab);
        sequenceDiagramDrawer.setUmlPackage(codeController.getUmlPackage());
        sequenceDiagramDrawer.createSequenceTabContent(sequenceDiagramTab);

        try {
            sequenceDiagramDrawer.draw();
            codeController.createCodeTabs(sequenceDiagramDrawer.getUmlPackage());
        } catch (NullPointerException e) {
            // This is reason for codeController.getUmlPackage() is equal to one phase before now umlPackage
            tabPaneInSequenceTab.getTabs().clear();
            sequenceDiagramDrawer.setSequenceDiagramTabPane(tabPaneInSequenceTab);
            codeController.convertCodeToUml(Language.Java);
            sequenceDiagramDrawer.setUmlPackage(codeController.getUmlPackage());
            sequenceDiagramDrawer.createSequenceTabContent(sequenceDiagramTab);
            sequenceDiagramDrawer.draw();
        }

        classDiagramDrawer.setUmlPackage(sequenceDiagramDrawer.getUmlPackage());
    }

    /**
     * <p> Normalボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のNormalボタンで参照 </p>
     */
    @FXML
    private void selectNormalInCD() {
        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, normalButtonInCD);
    }

    /**
     * <p> Classボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のClassボタンで参照 </p>
     */
    @FXML
    private void selectClassInCD() {
        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, classButtonInCD);
    }

    /**
     * <p> Noteボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のNoteボタンで参照 </p>
     */
    @FXML
    private void selectNoteInCD() {
        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, noteButtonInCD);
    }

    /**
     * <p> Compositionボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のCompositionボタンで参照 </p>
     */
    @FXML
    private void selectCompositionInCD() {
        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, compositionButtonInCD);
    }

    /**
     * <p> Generalizationボタン選択時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるToolBar内のGeneralizationボタンで参照 </p>
     */
    @FXML
    private void selectGeneralizationInCD() {
        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, generalizationButtonInCD);
    }

    /**
     * <p> Canvasクリック時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるScrollPane内のCanvas（ {@link #classDiagramCanvas} ）で参照 </p>
     */
    @FXML
    private void clickedCanvasInCD(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            clickedCanvasByPrimaryButtonInCD(event.getX(), event.getY());
        } else if (event.getButton() == MouseButton.SECONDARY) {
            clickedCanvasBySecondaryButtonInCD(event.getX(), event.getY());
        }

        createClassTreeViewContents();
    }

    /**
     * <p> Canvasドラッグ時のシグナルハンドラ </p>
     *
     * <p> ClassDiagramTabにおけるScrollPane内のCanvas（ {@link #classDiagramCanvas} ）で参照 </p>
     */
    @FXML
    private void draggedCanvasInCD(MouseEvent event) {
        if (util.searchSelectedButtonIn(buttonsInCD) == normalButtonInCD &&
                classDiagramDrawer.isAlreadyDrawnAnyDiagram(event.getX(), event.getY())) {
            classDiagramDrawer.setMouseCoordinates(event.getX(), event.getY());
            classDiagramDrawer.moveTo(classDiagramDrawer.getNodeDiagramId(event.getX(), event.getY()), new Point2D(event.getX(), event.getY()));
            classDiagramDrawer.allReDrawCanvas();
        }
    }

    /**
     * <p> シーケンス図タブのノーマルボタン選択時のシグナルハンドラ</>
     */
    @FXML
    private void normalMessageInSD() {
        buttonsInSD = util.bindAllButtonsFalseWithout(buttonsInSD, normalButtonInSD);
    }

    /**
     * <p> シーケンス図タブのメッセージボタン選択時のシグナルハンドラ</>
     */
    @FXML
    private void selectMessageInSD() {
        buttonsInSD = util.bindAllButtonsFalseWithout(buttonsInSD, messageButtonInSD);
    }

    //
    // シグナルハンドラここまで
    //

    /**
     * <p> クラス図キャンバス上で（通常）左クリックした際に実行します </p>
     *
     * <p>
     * 操作ボタンにより動作が異なるが、通常操作以外は描画のみを行います。
     * また、通常操作時は何も動作しません。
     * </p>
     *
     * @param mouseX 左クリック時のマウス位置のX軸
     * @param mouseY 左クリック時のマウス位置のY軸
     */
    private void clickedCanvasByPrimaryButtonInCD(double mouseX, double mouseY) {
        if (classDiagramDrawer.isAlreadyDrawnAnyDiagram(mouseX, mouseY)) {
            if (util.searchSelectedButtonIn(buttonsInCD) == compositionButtonInCD) {
                if (!classDiagramDrawer.hasWaitedCorrectDrawnDiagram(ContentType.Composition, mouseX, mouseY)) {
                    classDiagramDrawer.setMouseCoordinates(mouseX, mouseY);
                    classDiagramDrawer.allReDrawCanvas();
                } else {
                    String compositionName = showCreateCompositionNameInputDialog();
                    classDiagramDrawer.addDrawnEdge(buttonsInCD, compositionName, mouseX, mouseY);
                    classDiagramDrawer.allReDrawCanvas();
                    convertUmlToCode();
                }
            } else if (util.searchSelectedButtonIn(buttonsInCD) == generalizationButtonInCD) {
                if (!classDiagramDrawer.hasWaitedCorrectDrawnDiagram(ContentType.Generalization, mouseX, mouseY)) {
                    classDiagramDrawer.setMouseCoordinates(mouseX, mouseY);
                    classDiagramDrawer.allReDrawCanvas();
                } else {
                    classDiagramDrawer.addDrawnEdge(buttonsInCD, "", mouseX, mouseY);
                    classDiagramDrawer.allReDrawCanvas();
                    convertUmlToCode();
                }
            }
        } else {
            classDiagramDrawer.setMouseCoordinates(mouseX, mouseY);
            if (util.searchSelectedButtonIn(buttonsInCD) == classButtonInCD) {
                String className = showCreateClassNameInputDialog();
                classDiagramDrawer.setNodeText(className);
                classDiagramDrawer.addDrawnNode(buttonsInCD);
                classDiagramDrawer.allReDrawCanvas();
                convertUmlToCode();
                writeUmlForCode(classDiagramDrawer.getPackage());
            } else if (util.searchSelectedButtonIn(buttonsInCD) == compositionButtonInCD) {
                classDiagramDrawer.resetNodeChosen(classDiagramDrawer.getCurrentNodeNumber());
                classDiagramDrawer.allReDrawCanvas();
            } else if (util.searchSelectedButtonIn(buttonsInCD) == generalizationButtonInCD) {
                classDiagramDrawer.resetNodeChosen(classDiagramDrawer.getCurrentNodeNumber());
                classDiagramDrawer.allReDrawCanvas();
            }
        }
    }

    /**
     * <p> クラス図キャンバス上で（通常）右クリックした際に実行します </p>
     *
     * <p>
     * 通常操作時のみ動作します。
     * メニュー表示を行うが、右クリックしたキャンバス上の位置により動作は異なります。
     * </p>
     *
     * @param mouseX 右クリック時のマウス位置のX軸
     * @param mouseY 右クリック時のマウス位置のY軸
     */
    private void clickedCanvasBySecondaryButtonInCD(double mouseX, double mouseY) {
        classDiagramScrollPane.setContextMenu(null);

        ContentType currentType = classDiagramDrawer.searchDrawnAnyDiagramType(mouseX, mouseY);

        if (currentType == ContentType.Undefined) return;
        if (util.searchSelectedButtonIn(buttonsInCD) != normalButtonInCD) return;

        if (currentType == ContentType.Class) {
            NodeDiagram nodeDiagram = classDiagramDrawer.findNodeDiagram(mouseX, mouseY);
            ContextMenu contextMenu = util.createClassContextMenuInCD(nodeDiagram.getNodeText(), nodeDiagram.getNodeType(),
                    classDiagramDrawer.getDrawnNodeTextList(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Attribute),
                    classDiagramDrawer.getDrawnNodeTextList(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Operation),
                    classDiagramDrawer.getDrawnNodeContentsBooleanList(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Attribute, ContentType.Indication),
                    classDiagramDrawer.getDrawnNodeContentsBooleanList(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Operation, ContentType.Indication));

            classDiagramScrollPane.setContextMenu(formatContextMenuInCD(contextMenu, nodeDiagram.getNodeType(), mouseX, mouseY));
        } else if (currentType == ContentType.Composition) {
            RelationshipAttributeGraphic relation = classDiagramDrawer.searchDrawnEdge(mouseX, mouseY);
            ContextMenu contextMenu = util.createClassContextMenuInCD(
                    relation.getAttribute().getVisibility() + " " + relation.getAttribute().getName(), relation.getType());
            classDiagramScrollPane.setContextMenu(formatContextMenuInCD(contextMenu, relation.getType(), mouseX, mouseY));

        } else if (currentType == ContentType.Generalization) {
            RelationshipAttributeGraphic relation = classDiagramDrawer.searchDrawnEdge(mouseX, mouseY);
            ContextMenu contextMenu = util.createClassContextMenuInCD("", relation.getType());
            classDiagramScrollPane.setContextMenu(formatContextMenuInCD(contextMenu, relation.getType(), mouseX, mouseY));
        }
    }

    /**
     * <p> シーケンス図キャンバス上で（通常）左クリックした際に実行します </p>
     *
     * <p>
     * 操作ボタンにより動作が異なるが、通常操作以外は描画のみを行います。
     * また、通常操作時は何も動作しません。
     * </p>
     *

     */
    private void clickedCanvasByPrimaryButtonInSD() {
        if (util.searchSelectedButtonIn(buttonsInSD) == messageButtonInSD) {
            String className = showCreateClassNameInputDialog();
//            classDiagramDrawer.setNodeText(className);
//            classDiagramDrawer.addDrawnNode(buttonsInCD);
//            classDiagramDrawer.allReDrawCanvas();
//            convertUmlToCode();
//            writeUmlForCode(classDiagramDrawer.getPackage());
        }
    }

    /**
     * <p> コードウィンドウを表示します </p>
     *
     * <p>
     *     同時に、コードウィンドウのコントローラクラスのインスタンスを取得しています。
     *     これはJavaFX仕様の取得方法です。
     * </p>
     *
     * <p>
     *     参照: <a href="http://hideoku.hatenablog.jp/entry/2013/06/07/205016"> FXML Controller で Stage を使うためのアレコレ - Java開発のんびり日記 </a>
     * </p>
     * @param mainController メインウィンドウのコントローラクラスのインスタンス
     * @param parent 親ウィンドウ
     * @param filePath ウィンドウFXMLファイルのパス
     * @param title ウィンドウのタイトル
     */
    public void showCodeStage(MainController mainController, Stage parent, String filePath, String title) {
        try {
            mainStage = parent;
            codeStage = new Stage();
            codeStage.initOwner(mainStage);
            codeStage.setTitle(title);
            FXMLLoader codeLoader = new FXMLLoader(getClass().getResource(filePath));
            codeStage.setScene(new Scene(codeLoader.load()));
            codeStage.show();
            codeController = codeLoader.getController();
            codeController.setMainController(mainController);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Package getClassDiagramDrawerUmlPackage() {
        return classDiagramDrawer.getPackage();
    }

    public void writeUmlForCode(Package umlPackage) {
        if (classDiagramTab.isSelected()) {
            writeClassDiagramForCode(umlPackage);
        } else { // if (sequenceDiagramTab.isSelected()) {
            writeSequenceDiagramForCode(umlPackage);
        }
    }

    private void writeClassDiagramForCode(Package umlPackage) {
        if (umlPackage.getClasses().size() <= 0) return;

        // i番目のクラスが持つj番目の属性はk番目のクラスとコンポジション関係を持つ
        Map<Integer, Map<Integer, Integer>> relationsIds = new HashMap<>();

        for (int i = 0; i < umlPackage.getClasses().size(); i++) {
            Map<Integer, Integer> relationIds = new HashMap<>();
            for (int j = 0; j < umlPackage.getClasses().get(i).extractAttributes().size(); j++) {
                for (int k = 0; k < umlPackage.getClasses().size(); k++) {
                    if (umlPackage.getClasses().get(k).getName().equals(umlPackage.getClasses().get(i).extractAttributes().get(j).getType().getName().getNameText())) {
                        relationIds.put(j, k);
                        break;
                    }
                }
            }
            relationsIds.put(i, relationIds);
        }

        classDiagramDrawer.clearAllRelations();

        for (int i = 0; i < umlPackage.getClasses().size(); i++) {
            classDiagramDrawer.changeDrawnNodeText(i, ContentType.Title, i, umlPackage.getClasses().get(i).getName());
            classDiagramDrawer.deleteAllDrawnNodeText(i, ContentType.Attribute);
            classDiagramDrawer.deleteAllDrawnNodeText(i, ContentType.Operation);
            classDiagramDrawer.deleteAllDrawnNodeText(i, ContentType.Composition);

            for (int j = 0; j < umlPackage.getClasses().get(i).extractRelations().size(); j++) {
                String content = umlPackage.getClasses().get(i).extractRelations().get(j).toString();
                classDiagramDrawer.createDrawnEdge(ContentType.Composition, content, umlPackage.getClasses().get(i).getName(), umlPackage.getClasses().get(i).extractRelations().get(j).getType().getName().getNameText());
            }

            for (int count = 0, j = 0; j < umlPackage.getClasses().get(i).extractAttributes().size(); j++) {
                if (relationsIds.get(i).containsKey(count)) {
                    String content = umlPackage.getClasses().get(i).extractAttributes().get(j).toString();
                    classDiagramDrawer.createDrawnEdge(ContentType.Composition, content, umlPackage.getClasses().get(i).getName(), umlPackage.getClasses().get(relationsIds.get(i).get(count)).getName());
                    umlPackage.getClasses().get(i).addRelation(umlPackage.getClasses().get(i).extractAttributes().get(j));
                    umlPackage.getClasses().get(i).getAttributeGraphics().remove(j);
                    j--;
                } else {
                    classDiagramDrawer.addDrawnNodeText(i, ContentType.Attribute, umlPackage.getClasses().get(i).extractAttributes().get(j).toString());
                }
                count++;
            }

            for (int j = 0; j < umlPackage.getClasses().get(i).extractOperations().size(); j++) {
                classDiagramDrawer.addDrawnNodeText(i, ContentType.Operation, umlPackage.getClasses().get(i).extractOperations().get(j).toString());
                String abstractOrNot = umlPackage.getClasses().get(i).getOperationGraphics().get(j).isAbstract()
                        ? "abstract"
                        : "not abstract";
                classDiagramDrawer.changeDrawnNodeText(i, ContentType.Abstraction, j, abstractOrNot);
            }

            if (umlPackage.getClasses().get(i).getGeneralizationClass() != null) {
                classDiagramDrawer.createDrawnEdge(ContentType.Generalization, "", umlPackage.getClasses().get(i).getName(), umlPackage.getClasses().get(i).getGeneralizationClass().getName());
            }
        }

        classDiagramDrawer.allReDrawCanvas();
        createClassTreeViewContents();
    }

    private void writeSequenceDiagramForCode(Package umlPackage) {
        tabPaneInSequenceTab.getTabs().clear();
        sequenceDiagramDrawer.setSequenceDiagramTabPane(tabPaneInSequenceTab);
        sequenceDiagramDrawer.setUmlPackage(umlPackage);
        sequenceDiagramDrawer.createSequenceTabContent(sequenceDiagramTab);
        sequenceDiagramDrawer.draw();
    }

    private String importFile(Language language) throws IOException {
        FileChooser fileChooser = new FileChooser();

        if (language == Language.Java) {
            fileChooser.setTitle("Javaファイルを選択してください。");
            // 拡張子フィルタを設定
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Javaファイル", "*.java"));
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("すべてのファイル", "*.*"));
        } else if (language == Language.Cpp) {
            fileChooser.setTitle("C++ファイルを選択してください。");
            // 拡張子フィルタを設定
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ヘッダファイル", "*.h", "*.hpp"));
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C++ファイル", "*.cpp", "*.cxx"));
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("すべてのファイル", "*.*"));
        }

        fileChooser.setInitialDirectory(filePath);

        // ファイル選択
        File file = fileChooser.showOpenDialog(mainStage);
        if(file == null) throw new IOException();

        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String str;
        while((str = br.readLine()) != null) {
            sb.append(str);
        }
        filePath = new File(file.getParent());

        return sb.toString();
    }

    public void createClass(String className) {
        if (!classDiagramTab.isSelected()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "クラス図記述タブを表示中に押してください", ButtonType.YES);
            alert.showAndWait();
            return;
        }

        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, classButtonInCD);
        classDiagramDrawer.setNodeText(className);
        classDiagramDrawer.addDrawnNode(buttonsInCD);
        classDiagramDrawer.allReDrawCanvas();
        convertUmlToCode();
        writeUmlForCode(classDiagramDrawer.getPackage());
        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, normalButtonInCD);
    }

    private void importCode(Language language, String code) {
        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, classButtonInCD);
        classDiagramDrawer.setNodeText("ThisIsCurrentClassNameBecauseThisIsRewrittenInstantly");
        classDiagramDrawer.addDrawnNode(buttonsInCD);
        classDiagramDrawer.allReDrawCanvas();
        codeController.importCode(language, code);
        classDiagramDrawer.changeDrawnNodeText(classDiagramDrawer.getNodes().size() - 1, ContentType.Title, 0, codeController.getUmlPackage().getClasses().get(codeController.getUmlPackage().getClasses().size() - 1).getName());
        convertUmlToCode(codeController.getUmlPackage());
        writeUmlForCode(codeController.getUmlPackage());
        buttonsInCD = util.bindAllButtonsFalseWithout(buttonsInCD, normalButtonInCD);
    }

    /**
     * <p> コードステージを取得します </p>
     *
     * <p>
     *     テストコードでのみの使用を想定していますが、開発が進むことで変わる可能性があります。
     * </p>
     *
     * @return コードステージ
     */
    Stage getCodeStage() {
        return codeStage;
    }

    /**
     * <p> UMLをコードに変換してコードエリアに反映します </p>
     */
    private void convertUmlToCode() {
        if (codeController == null) return;
        codeController.createCodeTabs(classDiagramDrawer.getPackage());
    }

    /**
     * <p> UMLをコードに変換してコードエリアに反映します </p>
     */
    private void convertUmlToCode(Package umlPackage) {
        if (codeController == null) return;
        codeController.createCodeTabs(umlPackage);
    }

    /**
     * <p> クラス図キャンバス上での右クリックメニューの各メニューアイテムの動作を整形します </p>
     *
     * <p>
     * 名前の変更と内容の追加と内容の変更メニューではテキスト入力ダイアログを表示しますが、
     * それ以外ではメニューアイテムの選択直後にキャンバスを再描画します。
     * </p>
     *
     * @param contextMenu 右クリックメニューの見た目が整形済みの右クリックメニュー <br>
     *                    {@link UtilityJavaFXComponent#createClassContextMenuInCD(String, ContentType)} メソッドで取得したインスタンスを入れる必要がある。
     * @param type        右クリックした要素の種類
     * @return 動作整形済みの右クリックメニュー <br>
     * {@link UtilityJavaFXComponent} クラスで整形していないメニューや未分類の要素の種類を{@code contextMenu}や{@code type}に入れた場合は{@code null}を返す。
     */
    private ContextMenu formatContextMenuInCD(ContextMenu contextMenu, ContentType type, double mouseX, double mouseY) {
        if (type == ContentType.Class) {
            if (contextMenu.getItems().size() != 5) return null;
            contextMenu = formatClassContextMenuInCD(contextMenu);

        } else if (type == ContentType.Composition) {
            if (contextMenu.getItems().size() != 2) return null;
            contextMenu = formatCompositionContextMenuInCD(contextMenu, mouseX, mouseY);

        } else if (type == ContentType.Generalization) {
            if (contextMenu.getItems().size() != 1) return null;
            contextMenu = formatGeneralizationContextMenuInCD(contextMenu, mouseX, mouseY);

        } else {
            return null;
        }

        return contextMenu;
    }

    private ContextMenu formatClassContextMenuInCD(ContextMenu contextMenu) {
        // クラス名の変更
        contextMenu.getItems().get(0).setOnAction(event -> {
            String className = showChangeClassNameInputDialog(classDiagramDrawer.getNodes().get(classDiagramDrawer.getCurrentNodeNumber()).getNodeText());
            classDiagramDrawer.changeDrawnNodeText(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Title, 0, className);
            classDiagramDrawer.allReDrawCanvas();
            convertUmlToCode();
            writeUmlForCode(classDiagramDrawer.getPackage());
        });
        // クラスの削除
        contextMenu.getItems().get(1).setOnAction(event -> {
            classDiagramDrawer.deleteDrawnNode(classDiagramDrawer.getCurrentNodeNumber());
            classDiagramDrawer.allReDrawCanvas();
            convertUmlToCode();
            writeUmlForCode(classDiagramDrawer.getPackage());
        });
        // クラスの属性の追加
        ((Menu) contextMenu.getItems().get(3)).getItems().get(0).setOnAction(event -> {
            String addAttribute = showAddClassAttributeInputDialog();
            classDiagramDrawer.addDrawnNodeText(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Attribute, addAttribute);
            classDiagramDrawer.allReDrawCanvas();
            convertUmlToCode();
        });
        // クラスの操作の追加
        ((Menu) contextMenu.getItems().get(4)).getItems().get(0).setOnAction(event -> {
            String addOperation = showAddClassOperationInputDialog();
            classDiagramDrawer.addDrawnNodeText(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Operation, addOperation);
            classDiagramDrawer.allReDrawCanvas();
            convertUmlToCode();
        });
        List<String> attributes = classDiagramDrawer.getDrawnNodeTextList(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Attribute);
        List<String> operations = classDiagramDrawer.getDrawnNodeTextList(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Operation);
        // クラスの各属性の変更
        for (int i = 0; i < attributes.size(); i++) {
            int contentNumber = i;
            ((Menu) ((Menu) contextMenu.getItems().get(3)).getItems().get(1)).getItems().get(i).setOnAction(event -> {
                String changedAttribute = showChangeClassAttributeInputDialog(attributes.get(contentNumber));
                classDiagramDrawer.changeDrawnNodeText(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Attribute, contentNumber, changedAttribute);
                classDiagramDrawer.allReDrawCanvas();
                convertUmlToCode();
            });
        }
        // クラスの各属性の削除
        for (int i = 0; i < attributes.size(); i++) {
            int contentNumber = i;
            ((Menu) ((Menu) contextMenu.getItems().get(3)).getItems().get(2)).getItems().get(i).setOnAction(event -> {
                classDiagramDrawer.deleteDrawnNodeText(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Attribute, contentNumber);
                classDiagramDrawer.allReDrawCanvas();
                convertUmlToCode();
            });
        }
        // クラスの各属性の表示選択
        for (int i = 0; i < attributes.size(); i++) {
            int contentNumber = i;
            ((Menu) ((Menu) contextMenu.getItems().get(3)).getItems().get(3)).getItems().get(i).setOnAction(event -> {
                classDiagramDrawer.setDrawnNodeContentBoolean(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Attribute, ContentType.Indication, contentNumber,
                        ((CheckMenuItem) ((Menu) ((Menu) contextMenu.getItems().get(3)).getItems().get(3)).getItems().get(contentNumber)).isSelected());
                classDiagramDrawer.allReDrawCanvas();
            });
        }
        // クラスの各操作の変更
        for (int i = 0; i < operations.size(); i++) {
            int contentNumber = i;
            ((Menu) ((Menu) contextMenu.getItems().get(4)).getItems().get(1)).getItems().get(i).setOnAction(event -> {
                String changedOperation = showChangeClassOperationInputDialog(operations.get(contentNumber));
                classDiagramDrawer.changeDrawnNodeText(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Operation, contentNumber, changedOperation);
                classDiagramDrawer.allReDrawCanvas();
                convertUmlToCode();
            });
        }
        // クラスの各操作の削除
        for (int i = 0; i < operations.size(); i++) {
            int contentNumber = i;
            ((Menu) ((Menu) contextMenu.getItems().get(4)).getItems().get(2)).getItems().get(i).setOnAction(event -> {
                classDiagramDrawer.deleteDrawnNodeText(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Operation, contentNumber);
                classDiagramDrawer.allReDrawCanvas();
                convertUmlToCode();
            });
        }
        // クラスの各操作の表示選択
        for (int i = 0; i < operations.size(); i++) {
            int contentNumber = i;
            ((Menu) ((Menu) contextMenu.getItems().get(4)).getItems().get(3)).getItems().get(i).setOnAction(event -> {
                classDiagramDrawer.setDrawnNodeContentBoolean(classDiagramDrawer.getCurrentNodeNumber(), ContentType.Operation, ContentType.Indication, contentNumber,
                        ((CheckMenuItem) ((Menu) ((Menu) contextMenu.getItems().get(4)).getItems().get(3)).getItems().get(contentNumber)).isSelected());
                classDiagramDrawer.allReDrawCanvas();
            });
        }

        return contextMenu;
    }

    private ContextMenu formatCompositionContextMenuInCD(ContextMenu contextMenu, double mouseX, double mouseY) {
        // コンポジション関係の変更
        contextMenu.getItems().get(0).setOnAction(event -> {
            RelationshipAttributeGraphic composition = classDiagramDrawer.searchDrawnEdge(mouseX, mouseY);
            String compositionName = showChangeCompositionNameInputDialog(composition.getText());
            classDiagramDrawer.changeDrawnEdge(mouseX, mouseY, compositionName);
            classDiagramDrawer.allReDrawCanvas();
            convertUmlToCode();
        });
        // コンポジション関係の削除
        contextMenu.getItems().get(1).setOnAction(event -> {
            classDiagramDrawer.deleteDrawnEdge(mouseX, mouseY);
            classDiagramDrawer.allReDrawCanvas();
            convertUmlToCode();
        });

        return contextMenu;
    }

    private ContextMenu formatGeneralizationContextMenuInCD(ContextMenu contextMenu, double mouseX, double mouseY) {
        // 汎化関係の削除
        contextMenu.getItems().get(0).setOnAction(event -> {
            classDiagramDrawer.deleteDrawnEdge(mouseX, mouseY);
            classDiagramDrawer.allReDrawCanvas();
            convertUmlToCode();
        });

        return contextMenu;
    }

    private String showCreateClassNameInputDialog() {
        return util.showClassDiagramInputDialog("クラスの追加", "追加するクラスのクラス名を入力してください。", "");
    }

    private String showChangeClassNameInputDialog(String className) {
        return util.showClassDiagramInputDialog("クラス名の変更", "変更後のクラス名を入力してください。", className);
    }

    private String showAddClassAttributeInputDialog() {
        return util.showClassDiagramInputDialog("属性の追加", "追加する属性を入力してください。", "");
    }

    private String showChangeClassAttributeInputDialog(String attribute) {
        return util.showClassDiagramInputDialog("属性の変更", "変更後の属性を入力してください。", attribute);
    }

    private String showAddClassOperationInputDialog() {
        return util.showClassDiagramInputDialog("操作の追加", "追加する操作を入力してください。", "");
    }

    private String showChangeClassOperationInputDialog(String operation) {
        return util.showClassDiagramInputDialog("操作の変更", "変更後の操作を入力してください。", operation);
    }

    private String showCreateCompositionNameInputDialog() {
        return util.showClassDiagramInputDialog("コンポジションの追加", "コンポジション先の関連端名を入力してください。", "");
    }

    private String showChangeCompositionNameInputDialog(String composition) {
        return util.showClassDiagramInputDialog("コンポジションの変更", "変更後のコンポジション先の関連端名を入力してください。", composition);
    }

    private void createClassTreeViewContents() {
        classTree.refresh();

        TreeItem<String> classTreeItem = new TreeItem<>("Class");
        classTreeItem.setExpanded(true);
        for (Class umlClass : codeController.getUmlPackage().getClasses()) {
            // TestにおけるTab選択時の誤動作防止
            TreeItem<String> className = new TreeItem<>(" " + umlClass.getName());
            className.setExpanded(true);
            classTreeItem.getChildren().add(className);
        }

        classTree.setRoot(classTreeItem);
    }
}
