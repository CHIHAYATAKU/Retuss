package io.github.morichan.retuss.drawer;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
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

        StringBuilder puml = new StringBuilder();
        puml.append("@startuml\n");
        puml.append("scale 1.5\n");
        puml.append("skinparam style strictuml\n");
        puml.append("skinparam classAttributeIconSize 0\n");

        // クラス定義の生成
        for (Class umlClass : umlClassList) {
            puml.append(umlClassToPlantUml(umlClass));
        }

        // 関係性の生成（クラス定義の後に配置）
        for (Class umlClass : umlClassList) {
            puml.append(generateRelationships(umlClass));
        }

        puml.append("@enduml\n");

        // デバッグ出力
        System.out.println("Generated PlantUML:\n" + puml);
        SourceStringReader reader = new SourceStringReader(puml.toString());
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
            String svg = new String(os.toByteArray(), StandardCharsets.UTF_8);
            webView.getEngine().loadContent(svg);
        } catch (Exception e) {
            System.err.println("Error generating diagram: " + e.getMessage());
            e.printStackTrace();
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
