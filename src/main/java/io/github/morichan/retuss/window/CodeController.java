package io.github.morichan.retuss.window;

import io.github.morichan.retuss.language.Model;
import io.github.morichan.retuss.language.cpp.Cpp;
import io.github.morichan.retuss.language.java.Java;
import io.github.morichan.retuss.language.uml.Package;
import io.github.morichan.retuss.listener.CppLanguage;
import io.github.morichan.retuss.listener.JavaLanguage;
import io.github.morichan.retuss.translator.Language;
import io.github.morichan.retuss.translator.Translator;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/**
 * <p> RETUSSコードウィンドウの動作管理クラス </p>
 *
 * <p>
 * {@link MainController}クラスで生成したretussCode.fxmlファイルにおけるシグナルハンドラを扱います。
 * </p>
 */
public class CodeController {
    @FXML
    private TabPane codeTabPane;
    private MainController mainController;
    private Model model;
    private Translator translator;
    private JavaLanguage javaLanguage = new JavaLanguage();
    private CppLanguage cppLanguage = new CppLanguage();
    private int createdClassCount = 0;

    @FXML
    private void initialize() {
        codeTabPane.getTabs().add(createLanguageTab(Language.Java));
        codeTabPane.getTabs().add(createLanguageTab(Language.Cpp));
    }

    @FXML
    private void createClass() {
        mainController.createClass("NewClass" + createdClassCount);
        createdClassCount++;
    }

    public void setModel(Model model) {
        this.model = model;
        this.translator = new Translator(model);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * <p> ソースコード文字列→ソースコード情報→UML情報→UML描画を行う </p>
     *
     * @param language 変換するソースコードの言語
     * @return なし
     */
    public void convertCodeToUml(Language language) {
        // ソースコード文字列→ソースコード情報→UML情報
        if(language == Language.Java) {
            convertJavaToUml();
        } else if (language == Language.Cpp) {
            convertCppToUml();
        }

        // UML情報 → UMLダイアグラムの描画
        mainController.writeUml(model.getUml());
    }

    /**
     * <p> ソースコードを更新する </p>
     */
    public void updateCode() {
        if(mainController.isSelectedSDTab()) {
            updateCodeFromSD();
        } else {
            updateCodeFromCD();
        }
    }

    public void importCode(Language language, String code) {
        if (language == Language.Java) {
            javaLanguage.parseForClassDiagram(code);
            model.getJava().addClass(javaLanguage.getJava().getClasses().get(0));
            translator.translateToUML(model.getJava());
        } else if (language == Language.Cpp) {
            cppLanguage.parseForClassDiagram(code);
            model.getCpp().addClass(cppLanguage.getCpp().getClasses().get(0));
            translator.translateToUML(model.getCpp());
        }

        setCodeTabs(model.getJava());
        setCodeTabs(model.getCpp());
    }

    /**
     * <p> Javaソースコード文字列→Java情報→UML情報 </p>
     */
    private void convertJavaToUml() {
        String code = "";

        // 選択されているクラスタブのソースコードを取得
        for(Tab classTab : ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(0).getContent()).getChildren().get(0)).getTabs()) {
            if(classTab.isSelected()) {
                code = ((CodeArea)((AnchorPane)classTab.getContent()).getChildren().get(0)).getText();
            }
        }

        // ソースコード→Java情報に変換
        try {
            javaLanguage.parseForClassDiagram(code);
        } catch (NullPointerException e) {
            System.out.println("This is Parse Error because JavaEvalListener object is null, but no problem.");
        } catch (IllegalArgumentException e) {
            System.out.println("This is Parse Error because JavaEvalListener object was set IllegalArgument, but no problem.");
        } catch (IndexOutOfBoundsException e) {
            System.out.println("This is Parse Error because JavaEvalListener object was not found Class name, but no problem.");
        }

        // 既存のJava情報を更新
        model.getJava().updateClass(javaLanguage.getJava().getClasses().get(0));

        // Java情報→UML情報の変換と、既存のUML情報を更新
        model.getUml().updateClass(translator.translateToUML(javaLanguage.getJava()).getClasses().get(0));
    }

    /**
     * <p> C++ソースコード文字列→Cpp情報→UML情報 </p>
     */
    private void convertCppToUml() {
        String code = "";

        // 選択されているクラスタブのソースコードを取得
        for(Tab classTab : ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(1).getContent()).getChildren().get(0)).getTabs()) {
            if(classTab.isSelected()) {
                code = ((CodeArea)((AnchorPane)classTab.getContent()).getChildren().get(0)).getText();
            }
        }

        // ソースコード→Cpp情報に変換
        try {
            cppLanguage.parseForClassDiagram(code);
        } catch (NullPointerException e) {
            System.out.println("This is Parse Error because JavaEvalListener object is null, but no problem.");
        } catch (IllegalArgumentException e) {
            System.out.println("This is Parse Error because JavaEvalListener object was set IllegalArgument, but no problem.");
        } catch (IndexOutOfBoundsException e) {
            System.out.println("This is Parse Error because JavaEvalListener object was not found Class name, but no problem.");
        }

        // 既存のCpp情報を更新
        model.getCpp().updateClass(cppLanguage.getCpp().getClasses().get(0));

        // Cpp情報→UML情報の変換と、既存のUML情報を更新
        model.getUml().updateClass(translator.translateToUML(cppLanguage.getCpp()).getClasses().get(0));
    }

    /**
     * <p> 選択されている(変更があった)クラスのシーケンス図を、コードに反映する </p>
     */
    private void updateCodeFromCD() {
        model.setJava(translator.translateToJava(model.getUml()));
        model.setCpp(translator.translateToCpp(model.getUml()));
        setCodeTabs(model.getJava());
        setCodeTabs(model.getCpp());
    }

    /**
     * <p> 選択されている(変更があった)クラスのシーケンス図を、コードに反映する </p>
     */
    private void updateCodeFromSD() {
        // 選択されたクラスのみを格納UML情報
        Package targetUml = new Package();

        // 選択されているクラスの特定
        for (Tab classTab : mainController.getTabPaneInSequenceTab().getTabs()) {
            if(classTab.isSelected()){
                // 選択されているUMLクラスのみ変換するため、modelから選択されているUMLクラスを抽出する
                targetUml.addClass(model.getUml().searchClass(classTab.getText()));
                break;
            }
        }

        // 選択されているUMLクラス → Java情報の変換
        Java targetJava = translator.translateToJava(targetUml);
        // Jave情報の更新
        model.getJava().updateClass(targetJava.getClasses().get(0));

        // Java情報 → ソースコード文字列の生成
        setCodeTabs(model.getJava());
    }

    private Tab createLanguageTab(Language language) {

        TabPane codeTabPane = new TabPane();
        AnchorPane languageAnchor = new AnchorPane(codeTabPane);
        AnchorPane.setBottomAnchor(codeTabPane, 0.0);
        AnchorPane.setTopAnchor(codeTabPane, 0.0);
        AnchorPane.setLeftAnchor(codeTabPane, 0.0);
        AnchorPane.setRightAnchor(codeTabPane, 0.0);

        Tab languageTab = new Tab();
        languageTab.setContent(languageAnchor);

        languageTab.setOnSelectionChanged(event -> {
            try {
                updateCode();
            } catch (NullPointerException e) {
                System.out.println("This is null problem because ClassDiagramDrawer's umlPackage is null, so event was not set.");
            }
        });

        if (language == Language.Java) {
            languageTab.setText("Java");
        } else { // if (language == Language.Cpp) {
            languageTab.setText("C++");
        }

        return languageTab;
    }

    private Tab createCodeTab(io.github.morichan.retuss.language.java.Class javaClass) {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setOnKeyTyped(event -> convertCodeToUml(Language.Java));

        if (javaClass != null) codeArea.replaceText(javaClass.toString());

        if (javaClass == null) return createTab(codeArea, null);
        else return createTab(codeArea, javaClass.getName());
    }

    private Tab createCodeTab(io.github.morichan.retuss.language.cpp.Class cppClass) {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setOnKeyTyped(event -> convertCodeToUml(Language.Cpp));

        if (cppClass != null) codeArea.replaceText(cppClass.toString());
        // if (cppClass != null) codeArea.replaceText(cppClass.cppFile_toString());

        if (cppClass == null) return createTab(codeArea, null);
        else return createTab(codeArea, cppClass.getName());
    }

    private Tab createTab(CodeArea area, String title) {
        AnchorPane codeAnchor = new AnchorPane(area);
        AnchorPane.setBottomAnchor(area, 0.0);
        AnchorPane.setTopAnchor(area, 0.0);
        AnchorPane.setLeftAnchor(area, 0.0);
        AnchorPane.setRightAnchor(area, 0.0);

        Tab codeTab = new Tab();
        codeTab.setContent(codeAnchor);
        if (title == null) codeTab.setText("<Unknown Title>");
        else codeTab.setText(title);
        codeTab.setClosable(false);

        return codeTab;
    }

    private void setCodeTabs(Java java) {
        ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(0).getContent()).getChildren().get(0)).getTabs().clear();
        for (io.github.morichan.retuss.language.java.Class javaClass : java.getClasses()) {
            Tab tab = createCodeTab(javaClass);
            ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(0).getContent()).getChildren().get(0)).getTabs().add(tab);
        }
    }

    private void setCodeTabs(Cpp cpp) {
        ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(1).getContent()).getChildren().get(0)).getTabs().clear();
        for (io.github.morichan.retuss.language.cpp.Class cppClass : cpp.getClasses()) {
            Tab tab = createCodeTab(cppClass);
            ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(1).getContent()).getChildren().get(0)).getTabs().add(tab);
        }
    }
}
