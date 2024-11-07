package io.github.morichan.retuss.drawer;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.Class;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;

public class CppClassDiagramDrawer {
    private CppModel model;
    private WebView webView;

    public CppClassDiagramDrawer(WebView webView) {
        this.model = CppModel.getInstance();
        this.webView = webView;
    }

    public void draw() {
        List<Class> umlClassList = model.getUmlClassList();

        // クラスが存在する場合のみ描画
        if (!umlClassList.isEmpty()) {
            StringBuilder puStrBuilder = new StringBuilder("@startuml\n");
            puStrBuilder.append("scale 1.5\n");
            puStrBuilder.append("skinparam style strictuml\n");
            puStrBuilder.append("skinparam classAttributeIconSize 0\n");

            // クラス図の生成
            for (Class umlClass : umlClassList) {
                puStrBuilder.append(umlClassToPlantUml(umlClass));
            }

            puStrBuilder.append("@enduml\n");

            // デバッグ出力
            System.out.println("C++ PlantUML Generated:\n" + puStrBuilder.toString());

            // SVG生成と表示
            try {
                SourceStringReader reader = new SourceStringReader(puStrBuilder.toString());
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
                final String svg = new String(os.toByteArray(), Charset.forName("UTF-8"));
                webView.getEngine().loadContent(svg);
            } catch (Exception e) {
                System.err.println("Failed to generate diagram: " + e.getMessage());
            }
        }
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
        for (Class umlClass : model.getUmlClassList()) {
            if (type.getName().getNameText().equals(umlClass.getName())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }
}
