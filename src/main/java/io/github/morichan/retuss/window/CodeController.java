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
    private Translator translator = new Translator();
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
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void createCodeTabs() {
        model.setJava(translator.translateToJava(model.getUml()));
        model.setCpp(translator.translateToCpp(model.getUml()));
        setCodeTabs(model.getJava());
        setCodeTabs(model.getCpp());
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
                createCodeTabs();
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

    public void convertCodeToUml(Language language) {
        Java java = new Java();
        Cpp cpp = new Cpp();

        // ソースコード → Java,C++情報
//        if(mainController.isSelectedSDTab()) {
//            // シーケンス図タブが選択されている場合、選択されているクラスのコードだけをJava情報に変換する
//            for (Tab classTab : ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(0).getContent()).getChildren().get(0)).getTabs()) {
//                if (classTab.isSelected()){
//                    try {
//                        String code = ((CodeArea)((AnchorPane)classTab.getContent()).getChildren().get(0)).getText();
//                        if (language == Language.Java) {
//                            javaLanguage.parseForClassDiagram(code);
//                            java.addClass(javaLanguage.getJava().getClasses().get(0));
//                        } else {
//                            cppLanguage.parseForClassDiagram(code);
//                            cpp.addClass(cppLanguage.getCpp().getClasses().get(0));
//                        }
//                    }  catch (NullPointerException e) {
//                        System.out.println("This is Parse Error because JavaEvalListener object is null, but no problem.");
//                    } catch (IllegalArgumentException e) {
//                        System.out.println("This is Parse Error because JavaEvalListener object was set IllegalArgument, but no problem.");
//                    } catch (IndexOutOfBoundsException e) {
//                        System.out.println("This is Parse Error because JavaEvalListener object was not found Class name, but no problem.");
//                    }
//                }
//            }
//        } else {
            // クラス図タブが選択されている場合、すべてのコードをJava情報に変換する
            for (int i = 0; i < ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(0).getContent()).getChildren().get(0)).getTabs().size(); i++) {
                try {
                    if (language == Language.Java) {
                        javaLanguage.parseForClassDiagram(getCode(0, i));
                        java.addClass(javaLanguage.getJava().getClasses().get(0));
                    } else {
                        cppLanguage.parseForClassDiagram(getCode(1, i));
                        // 複数クラスに対応中、今のところで来ていない
//                    for (io.github.morichan.retuss.language.cpp.Class cppClass : cppLanguage.getCpp().getClasses()) {
//                        cpp.addClass(cppClass);
//                    }
                        cpp.addClass(cppLanguage.getCpp().getClasses().get(0));
                    }
                } catch (NullPointerException e) {
                    System.out.println("This is Parse Error because JavaEvalListener object is null, but no problem.");
                } catch (IllegalArgumentException e) {
                    System.out.println("This is Parse Error because JavaEvalListener object was set IllegalArgument, but no problem.");
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("This is Parse Error because JavaEvalListener object was not found Class name, but no problem.");
                }
            }
//        }

        // Java,C++情報 → UML情報
        if (language == Language.Java) {
            model.setJava(java);
            model.setUml(translator.translateToUML(java));
        } else {
            model.setCpp(cpp);
            model.setUml(translator.translateToUML(cpp));
        }

        // UML情報 → UMLダイアグラム
        mainController.writeUmlForCode(model.getUml());
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

    private String getCode(int languageNumber, int tabNumber) {
        // tabNumberのタブに記述されているソースコードを返す
        return ((CodeArea) ((AnchorPane) ((TabPane) ((AnchorPane) codeTabPane.getTabs().get(languageNumber).getContent()).getChildren().get(0)).getTabs().get(tabNumber).getContent()).getChildren().get(0)).getText();
    }
}
