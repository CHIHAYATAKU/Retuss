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

    // public void draw() {
    // List<Class> umlClassList = model.getUmlClassList();
    // System.out.println("Drawing C++ classes: " + umlClassList.size());

    // StringBuilder puStrBuilder = new StringBuilder("@startuml\n");
    // puStrBuilder.append("scale 1.5\n");
    // puStrBuilder.append("skinparam style strictuml\n");
    // puStrBuilder.append("skinparam classAttributeIconSize 0\n");

    // // クラス図の生成
    // for (Class umlClass : umlClassList) {
    // puStrBuilder.append(umlClassToPlantUml(umlClass));
    // }

    // puStrBuilder.append("@enduml\n");

    // // デバッグ出力
    // System.out.println("Generated PlantUML:\n" + puStrBuilder.toString());

    // // SVG生成と表示
    // try {
    // SourceStringReader reader = new SourceStringReader(puStrBuilder.toString());
    // final ByteArrayOutputStream os = new ByteArrayOutputStream();
    // String desc = reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
    // final String svg = new String(os.toByteArray(), Charset.forName("UTF-8"));
    // webView.getEngine().loadContent(svg);
    // } catch (Exception e) {
    // System.err.println("Error generating diagram: " + e.getMessage());
    // e.printStackTrace();
    // }
    // }

    // private String umlClassToPlantUml(Class umlClass) {
    // StringBuilder sb = new StringBuilder();
    // StringBuilder compositionSb = new StringBuilder();

    // // 抽象クラス
    // if (umlClass.getAbstruct()) {
    // sb.append("abstract ");
    // }
    // sb.append("class ").append(umlClass.getName()).append(" {\n");

    // // 属性またはコンポジション関係
    // for (Attribute attribute : umlClass.getAttributeList()) {
    // if (isComposition(attribute.getType()))
    // // コンポジション関係
    // compositionSb.append(umlClass.getName());
    // compositionSb.append(" *-- ");
    // compositionSb.append(" \" ");
    // compositionSb.append(attribute.getVisibility()); // 可視性
    // compositionSb.append(" ");
    // compositionSb.append(attribute.getName()); // 関連端名
    // compositionSb.append(" ");
    // compositionSb.append("1"); // 多重度
    // compositionSb.append(" \" ");
    // compositionSb.append(attribute.getType().getName().getNameText());
    // compositionSb.append("\n");

    // }

    // // 属性
    // for (Attribute attribute : umlClass.getAttributeList()) {
    // sb.append(" {field} ");
    // String visibility = attribute.getVisibility().toString();
    // switch (visibility) {
    // case "PUBLIC":
    // sb.append("+ ");
    // break;
    // case "PRIVATE":
    // sb.append("- ");
    // break;
    // case "PROTECTED":
    // sb.append("# ");
    // break;
    // default:
    // sb.append("~ ");
    // break;
    // }
    // sb.append(attribute.toString()).append("\n");
    // }

    // // メソッド
    // for (Operation operation : umlClass.getOperationList()) {
    // sb.append(" {method} ");
    // String visibility = operation.getVisibility().toString();
    // switch (visibility) {
    // case "PUBLIC":
    // sb.append("+ ");
    // break;
    // case "PRIVATE":
    // sb.append("- ");
    // break;
    // case "PROTECTED":
    // sb.append("# ");
    // break;
    // default:
    // sb.append("~ ");
    // break;
    // }
    // sb.append(operation.toString()).append("\n");
    // }

    // sb.append("}\n\n");

    // // 継承関係
    // if (umlClass.getSuperClass().isPresent()) {
    // sb.append(umlClass.getName())
    // .append(" --|> ")
    // .append(umlClass.getSuperClass().get().getName())
    // .append("\n");
    // }

    // // 汎化関係
    // if (umlClass.getSuperClass().isPresent()) {
    // sb.append(umlClass.getName());
    // sb.append(" --|> ");
    // sb.append(umlClass.getSuperClass().get().getName());
    // sb.append("\n");
    // }

    // // コンポジション関係のテキストを統合
    // sb.append(compositionSb);

    // return sb.toString();
    // }

    // private Boolean isComposition(Type type) {
    // for (Class umlClass : model.getUmlClassList()) {
    // if (type.getName().getNameText().equals(umlClass.getName())) {
    // return Boolean.TRUE;
    // }
    // }
    // return Boolean.FALSE;
    // }
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
            puml.append(generateClassDefinition(umlClass));
        }

        // 関係性の生成（クラス定義の後に配置）
        for (Class umlClass : umlClassList) {
            puml.append(generateRelationships(umlClass));
        }

        puml.append("@enduml\n");

        // デバッグ出力
        System.out.println("Generated PlantUML:\n" + puml);

        try {
            SourceStringReader reader = new SourceStringReader(puml.toString());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
            String svg = new String(os.toByteArray(), StandardCharsets.UTF_8);
            webView.getEngine().loadContent(svg);
        } catch (Exception e) {
            System.err.println("Error generating diagram: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String generateClassDefinition(Class umlClass) {
        StringBuilder sb = new StringBuilder();

        // クラス宣言
        if (umlClass.getAbstruct()) {
            sb.append("abstract ");
        }
        sb.append("class ").append(umlClass.getName()).append(" {\n");

        // フィールド（属性）の生成
        for (Attribute attribute : umlClass.getAttributeList()) {
            sb.append("    {field} ");
            String visibility = convertVisibility(attribute.getVisibility());
            System.out.println("DEBUG: Drawing attribute with visibility: " + visibility);
            sb.append(visibility)
                    .append(" ")
                    .append(attribute.getName().getNameText())
                    .append(" : ")
                    .append(attribute.getType().toString())
                    .append("\n");
        }

        // メソッドの生成（属性とは別セクション）
        for (Operation operation : umlClass.getOperationList()) {
            sb.append("    {method} ");
            String visibility = convertVisibility(operation.getVisibility());
            System.out.println("DEBUG: Drawing method with visibility: " + visibility);
            sb.append(visibility)
                    .append(" ")
                    .append(operation.getName().getNameText())
                    .append("()") // パラメータ処理も必要に応じて追加
                    .append(" : ")
                    .append(operation.getReturnType().toString())
                    .append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String convertVisibility(Visibility visibility) {
        if (visibility == null) {
            return "~";
        }

        System.out.println("viz :" + visibility.toString());

        switch (visibility.toString()) {
            case "+":
                return "+";
            case "-":
                return "-";
            case "#":
                return "#";
            default:
                return "~";
        }

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

    private Visibility getVisibility(String visibility) {
        Visibility result;
        if (visibility == null) {
            System.out.println("DEBUG: Converting visibility string 'null' to default PRIVATE");
            result = Visibility.Private;
        } else {
            switch (visibility.toUpperCase()) {
                case "PUBLIC":
                    System.out.println("DEBUG: Converting visibility string '" + visibility + "' to PUBLIC");
                    result = Visibility.Public;
                    break;
                case "PROTECTED":
                    System.out.println("DEBUG: Converting visibility string '" + visibility + "' to PROTECTED");
                    result = Visibility.Protected;
                    break;
                case "PRIVATE":
                    System.out.println("DEBUG: Converting visibility string '" + visibility + "' to PRIVATE");
                    result = Visibility.Private;
                    break;
                default:
                    System.out.println("DEBUG: Converting visibility string '" + visibility + "' to default PRIVATE");
                    result = Visibility.Private;
                    break;
            }
        }
        System.out.println("DEBUG: Final visibility value: " + result);
        return result;
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
