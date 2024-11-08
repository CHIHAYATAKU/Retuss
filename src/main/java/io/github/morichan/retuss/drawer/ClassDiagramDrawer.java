package io.github.morichan.retuss.drawer;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class ClassDiagramDrawer {
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private WebView webView;

    public ClassDiagramDrawer(WebView webView) {
        this.webView = webView;
    }

    public void draw() {
        // UML情報を集める
        List<Class> umlClassList = new ArrayList<>();
        umlClassList.addAll(javaModel.getUmlClassList());
        umlClassList.addAll(cppModel.getUmlClassList());

        // plantUML構文を生成する
        StringBuilder puStrBuilder = new StringBuilder("@startuml\n");
        puStrBuilder.append("scale 1.5\n");
        puStrBuilder.append("skinparam style strictuml\n");
        puStrBuilder.append("skinparam classAttributeIconSize 0\n");
        for (Class umlClass : umlClassList) {
            puStrBuilder.append(umlClassToPlantUml(umlClass));
        }
        puStrBuilder.append("@enduml\n");

        // plantUMLでクラス図のSVGを生成する
        SourceStringReader reader = new SourceStringReader(puStrBuilder.toString());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        // Write the first image to "os"
        try {
            String desc = reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
            os.close();
        } catch (Exception e) {
            System.err.println("Error drawing class diagram: " + e.getMessage());
        }

        // The XML is stored into svg
        final String svg = new String(os.toByteArray(), Charset.forName("UTF-8"));

        webView.getEngine().loadContent(svg);
    }

    private String umlClassToPlantUml(Class umlClass) {
        StringBuilder sb = new StringBuilder();
        StringBuilder compositionSb = new StringBuilder();

        // 抽象クラス
        if (umlClass.getAbstruct()) {
            sb.append("abstract ");
        }
        sb.append("class ");
        sb.append(umlClass.getName());
        sb.append(" { \n");

        // 属性またはコンポジション関係
        for (Attribute attribute : umlClass.getAttributeList()) {
            if (isComposition(attribute.getType())) {
                // コンポジション関係
                compositionSb.append(umlClass.getName());
                compositionSb.append(" *-- ");
                compositionSb.append(" \" ");
                compositionSb.append(attribute.getVisibility()); // 可視性
                compositionSb.append(" ");
                compositionSb.append(attribute.getName()); // 関連端名
                compositionSb.append(" ");
                compositionSb.append("1"); // 多重度
                compositionSb.append(" \" ");
                compositionSb.append(attribute.getType().getName().getNameText());
                compositionSb.append("\n");
            } else {
                // 属性
                sb.append("{field} ");
                sb.append(attribute.toString());
                sb.append("\n");
            }
        }

        // 操作
        for (Operation operation : umlClass.getOperationList()) {
            sb.append("{method} ");
            sb.append(operation.toString());
            sb.append("\n");
        }
        sb.append("}\n");

        // 汎化関係
        if (umlClass.getSuperClass().isPresent()) {
            sb.append(umlClass.getName());
            sb.append(" --|> ");
            sb.append(umlClass.getSuperClass().get().getName());
            sb.append("\n");
        }

        // コンポジション関係のテキストを統合
        sb.append(compositionSb);

        return sb.toString();
    }

    private Boolean isComposition(Type type) {
        // Java・C++両方のクラスをチェック
        for (Class umlClass : javaModel.getUmlClassList()) {
            if (type.getName().getNameText().equals(umlClass.getName())) {
                return Boolean.TRUE;
            }
        }
        for (Class umlClass : cppModel.getUmlClassList()) {
            if (type.getName().getNameText().equals(umlClass.getName())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

}
