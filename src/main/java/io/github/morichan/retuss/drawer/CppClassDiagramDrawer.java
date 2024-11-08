package io.github.morichan.retuss.drawer;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.Class;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CppClassDiagramDrawer {
    private CppModel model;
    private WebView webView;

    public CppClassDiagramDrawer(WebView webView) {
        this.model = CppModel.getInstance();
        this.webView = webView;
        System.out.println("CppClassDiagramDrawer initialized");
    }

    public void draw() {
        List<Class> umlClassList = model.getUmlClassList();
        System.out.println("Drawing C++ classes: " + umlClassList.size());

        StringBuilder pumlBuilder = new StringBuilder();
        pumlBuilder.append("@startuml\n");
        pumlBuilder.append("scale 1.5\n");
        pumlBuilder.append("skinparam style strictuml\n");
        pumlBuilder.append("skinparam classAttributeIconSize 0\n");

        // クラスの定義
        for (Class cls : umlClassList) {
            if (cls.getAbstruct()) {
                pumlBuilder.append("abstract ");
            }
            pumlBuilder.append("class ").append(cls.getName()).append(" {\n");

            // 属性の追加
            for (Attribute attr : cls.getAttributeList()) {
                pumlBuilder.append("  ")
                        .append(getVisibilitySymbol(attr.getVisibility()))
                        .append(" ")
                        .append(attr.getType())
                        .append(" ")
                        .append(attr.getName())
                        .append("\n");
            }

            // 操作の追加
            for (Operation op : cls.getOperationList()) {
                pumlBuilder.append("  ")
                        .append(getVisibilitySymbol(op.getVisibility()))
                        .append(" ")
                        .append(op.getReturnType())
                        .append(" ")
                        .append(op.getName())
                        .append("(");

                // パラメータの追加
                List<String> params = new ArrayList<>();
                for (Parameter param : op.getParameters()) {
                    params.add(param.getType() + " " + param.getName());
                }
                pumlBuilder.append(String.join(", ", params));
                pumlBuilder.append(")\n");
            }

            pumlBuilder.append("}\n\n");
        }

        // 継承関係の追加
        for (Class cls : umlClassList) {
            if (cls.getSuperClass().isPresent()) {
                pumlBuilder.append(cls.getSuperClass().get().getName())
                        .append(" <|-- ")
                        .append(cls.getName())
                        .append("\n");
            }
        }

        pumlBuilder.append("@enduml");

        String puml = pumlBuilder.toString();
        System.out.println("Generated PlantUML:\n" + puml);

        try {
            SourceStringReader reader = new SourceStringReader(puml);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            // SVGの生成
            reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
            String svg = new String(os.toByteArray(), Charset.forName("UTF-8"));

            // WebViewに表示
            if (svg != null && !svg.isEmpty()) {
                System.out.println("Loading SVG to WebView");
                webView.getEngine().loadContent(svg);
            } else {
                System.err.println("Generated SVG is empty");
            }
        } catch (Exception e) {
            System.err.println("Error generating class diagram: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getVisibilitySymbol(Visibility visibility) {
        switch (visibility) {
            case Public:
                return "+";
            case Protected:
                return "#";
            case Private:
                return "-";
            default:
                return "~";
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

    private String generateRelationships(Class umlClass) {
        StringBuilder sb = new StringBuilder();

        // 継承関係
        if (umlClass.getSuperClass().isPresent()) {
            sb.append(umlClass.getName())
                    .append(" --|> ")
                    .append(umlClass.getSuperClass().get().getName())
                    .append("\n");
        }

        // コンポジション関係
        for (Attribute attribute : umlClass.getAttributeList()) {
            Type attrType = attribute.getType();
            if (isClassType(attrType.toString())) {
                sb.append(umlClass.getName())
                        .append(" *-- ")
                        .append(attrType.toString())
                        .append(" : ")
                        .append(attribute.getName())
                        .append("\n");
            }
        }

        return sb.toString();
    }

    private boolean isClassType(String typeName) {
        // 基本型でないかどうかをチェック
        Set<String> basicTypes = Set.of(
                "int", "char", "bool", "float", "double",
                "void", "long", "short", "unsigned", "signed");
        return !basicTypes.contains(typeName) &&
                !typeName.startsWith("std::") &&
                Character.isUpperCase(typeName.charAt(0)); // クラス名は大文字で始まると仮定
    }
}
