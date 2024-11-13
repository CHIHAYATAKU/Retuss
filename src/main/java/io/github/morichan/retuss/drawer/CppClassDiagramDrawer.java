package io.github.morichan.retuss.drawer;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.model.uml.Relationship;
import javafx.application.Platform;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class CppClassDiagramDrawer {
    private final CppModel model;
    private WebView webView;
    private final ExecutorService diagramExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<String> lastSvg = new AtomicReference<>();
    private volatile boolean isUpdating = false;

    public CppClassDiagramDrawer(WebView webView) {
        this.model = CppModel.getInstance();
        this.webView = webView;
        System.out.println("CppClassDiagramDrawer initialized");
    }

    private void drawClass(StringBuilder pumlBuilder, Class cls) {
        if (!(cls instanceof CppClass))
            return;
        CppClass cppClass = (CppClass) cls;

        try {
            if (cls.getAbstruct()) {
                pumlBuilder.append("abstract ");
            }
            pumlBuilder.append("class ").append(cls.getName()).append(" {\n");

            // 属性
            for (Attribute attr : cls.getAttributeList()) {
                appendAttribute(pumlBuilder, attr, cppClass);
            }

            // メソッド
            for (Operation op : cls.getOperationList()) {
                appendOperation(pumlBuilder, op, cppClass);
            }

            pumlBuilder.append("}\n\n");
        } catch (Exception e) {
            System.err.println("Error drawing class " + cls.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isUserDefinedType(String type) {
        Set<String> basicTypes = Set.of("void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed");
        String cleanType = cleanTypeName(type);
        return !basicTypes.contains(cleanType) &&
                !cleanType.startsWith("std::") &&
                Character.isUpperCase(cleanType.charAt(0));
    }

    private boolean isPointerOrReference(String type) {
        return type.contains("*") || type.contains("&");
    }

    private String cleanTypeName(String typeName) {
        return typeName.replaceAll("[*&]", "") // ポインタ、参照記号を削除
                .replaceAll("\\s+", "") // 空白を削除
                .replaceAll("const", "") // constを削除
                .replaceAll("std::", "") // std::を削除
                .trim();
    }

    private void appendAttributeBase(StringBuilder pumlBuilder, Attribute attr) {
        pumlBuilder.append("  ")
                .append(getVisibilitySymbol(attr.getVisibility()))
                .append(" ")
                .append(attr.getName())
                .append(" : ")
                .append(attr.getType())
                .append("\n");
    }

    private void appendAttribute(StringBuilder pumlBuilder, Attribute attr, CppClass cls) {
        try {
            pumlBuilder.append("  ")
                    .append(getVisibilitySymbol(attr.getVisibility()))
                    .append(" ");

            // 修飾子の追加
            Set<CppClass.Modifier> modifiers = cls.getModifiers(attr.getName().getNameText());
            StringBuilder modifierStr = new StringBuilder();
            if (modifiers.contains(CppClass.Modifier.STATIC))
                modifierStr.append("{static} ");
            if (modifiers.contains(CppClass.Modifier.CONST))
                modifierStr.append("{constant} ");
            if (modifiers.contains(CppClass.Modifier.MUTABLE))
                modifierStr.append("{mutable} ");

            pumlBuilder.append(modifierStr);

            // 属性名と型
            pumlBuilder.append(attr.getName().getNameText())
                    .append(" : ");

            String type = attr.getType().toString();
            // constの場合は型の前に付加
            if (modifiers.contains(CppClass.Modifier.CONST)) {
                type = "const " + type;
            }
            pumlBuilder.append(formatType(type))
                    .append("\n");
        } catch (Exception e) {
            System.err.println("Error appending attribute " + attr.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void appendOperationBase(StringBuilder pumlBuilder, Operation op) {
        try {
            pumlBuilder.append("  ")
                    .append(getVisibilitySymbol(op.getVisibility()))
                    .append(" ")
                    .append(op.getName().getNameText())
                    .append("(");

            // パラメータ処理
            List<String> params = new ArrayList<>();
            for (Parameter param : op.getParameters()) {
                params.add(param.getType() + " " + param.getName());
            }
            pumlBuilder.append(String.join(", ", params));

            pumlBuilder.append(")");

            String returnType = op.getReturnType().toString();
            pumlBuilder.append(" : ")
                    .append(returnType);

            pumlBuilder.append("\n");
        } catch (Exception e) {
            System.err.println("Error appending operation " + op.getName() +
                    ": " + e.getMessage());
        }
    }

    private void appendOperation(StringBuilder pumlBuilder, Operation op, CppClass cls) {
        try {
            pumlBuilder.append("  ")
                    .append(getVisibilitySymbol(op.getVisibility()))
                    .append(" ");

            // 修飾子の追加
            Set<CppClass.Modifier> modifiers = cls.getModifiers(op.getName().getNameText());
            StringBuilder modifierStr = new StringBuilder();
            if (modifiers.contains(CppClass.Modifier.STATIC))
                modifierStr.append("{static} ");
            if (modifiers.contains(CppClass.Modifier.VIRTUAL))
                modifierStr.append("{abstract} ");
            if (modifiers.contains(CppClass.Modifier.CONST))
                modifierStr.append("{constant} ");

            pumlBuilder.append(modifierStr);

            // メソッド名とパラメータ
            pumlBuilder.append(op.getName().getNameText())
                    .append("(");

            // パラメータ処理
            try {
                List<String> params = new ArrayList<>();
                for (Parameter param : op.getParameters()) {
                    String paramType = formatType(param.getType().toString());
                    params.add(paramType + " " + param.getName().getNameText());
                }
                pumlBuilder.append(String.join(", ", params));
            } catch (IllegalStateException e) {
                // パラメータがない場合は無視
            }

            pumlBuilder.append(")");

            // 戻り値の型を必ず表示
            pumlBuilder.append(" : ")
                    .append(formatType(op.getReturnType().toString()));

            pumlBuilder.append("\n");
        } catch (Exception e) {
            System.err.println("Error appending operation " + op.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String formatType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "void";
        }

        // 空白を正規化
        type = type.trim().replaceAll("\\s+", " ");

        // constを一時的に除去（後で必要な位置に追加するため）
        boolean isConst = type.startsWith("const ");
        if (isConst) {
            type = type.substring(6).trim();
        }

        // ポインタ/参照修飾子を抽出
        String suffix = "";
        while (type.endsWith("*") || type.endsWith("&")) {
            suffix = type.charAt(type.length() - 1) + suffix;
            type = type.substring(0, type.length() - 1).trim();
        }

        // 結果を組み立て
        StringBuilder result = new StringBuilder();
        if (suffix.isEmpty()) {
            if (isConst)
                result.append("const ");
            result.append(type);
        } else {
            // ポインタ/参照型の場合は型の前に付ける
            if (isConst)
                result.append("const ");
            result.append(suffix).append(type);
        }

        return result.toString();
    }

    public void draw() {
        if (isUpdating)
            return; // スキップ if already updating
        isUpdating = true;

        CompletableFuture.supplyAsync(() -> {
            try {
                List<Class> classes = model.getUmlClassList();
                System.out.println("DEBUG: CppClassDiagramDrawer - Number of classes: " + classes.size());
                for (Class cls : classes) {
                    System.out.println("DEBUG: CppClassDiagramDrawer - Drawing class: " + cls.getName());
                    System.out.println("DEBUG: CppClassDiagramDrawer - Class type: " + cls.getClass().getName());
                    System.out.println("DEBUG: CppClassDiagramDrawer - Attributes: " + cls.getAttributeList().size());
                    System.out.println("DEBUG: CppClassDiagramDrawer - Operations: " + cls.getOperationList().size());
                }

                StringBuilder pumlBuilder = new StringBuilder();
                pumlBuilder.append("@startuml\n")
                        .append("skinparam style strictuml\n")
                        .append("skinparam classAttributeIconSize 0\n")
                        .append("skinparam stereotypePosition inside\n");

                for (Class cls : classes) {
                    System.out.println("Processing class: " + cls.getName());
                    System.out.println("Attributes: " + cls.getAttributeList().size());
                    System.out.println("Operations: " + cls.getOperationList().size());
                    drawClass(pumlBuilder, cls);
                }

                // 関係の描画を追加
                for (Class cls : classes) {
                    drawRelationships(pumlBuilder, cls);
                }

                pumlBuilder.append("@enduml\n");
                String puml = pumlBuilder.toString();
                System.out.println("Generated PlantUML:\n" + puml);

                // SVG生成と表示, キャッシュと比較して変更がなければスキップ
                String currentSvg = lastSvg.get();
                if (currentSvg != null && puml.equals(currentSvg)) {
                    return null;
                }

                SourceStringReader reader = new SourceStringReader(puml);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
                String svg = new String(os.toByteArray(), StandardCharsets.UTF_8);
                lastSvg.set(svg);
                return svg;
            } catch (Exception e) {
                System.err.println("Error generating diagram: " + e.getMessage());
                e.printStackTrace();
                return null;
            } finally {
                System.out.println("DEBUG: CppClassDiagramDrawer draw completed");
            }
        }, diagramExecutor)
                .thenAcceptAsync(svg -> {
                    try {
                        if (svg != null) {
                            System.out.println("DEBUG: Updating WebView with new SVG");
                            webView.getEngine().loadContent(svg);
                        }
                    } finally {
                        isUpdating = false;
                    }
                }, Platform::runLater);
    }

    // SVGのキャッシュをクリア
    public void clearCache() {
        lastSvg.set(null);
    }

    public void shutdown() {
        if (diagramExecutor != null) {
            diagramExecutor.shutdown();
            try {
                if (!diagramExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    diagramExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                diagramExecutor.shutdownNow();
            }
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

    private Boolean isComposition(Type type) {
        for (Class umlClass : model.getUmlClassList()) {
            if (type.getName().getNameText().equals(umlClass.getName())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
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

    private void drawRelationships(StringBuilder pumlBuilder, Class cls) {
        if (!(cls instanceof CppClass))
            return;
        CppClass cppClass = (CppClass) cls;

        // 継承関係
        if (cppClass.getSuperClass().isPresent()) {
            pumlBuilder.append(cppClass.getSuperClass().get().getName())
                    .append(" <|-- ")
                    .append(cppClass.getName())
                    .append("\n");
        }

        // コンポジション関係
        for (String composition : cppClass.getCompositions()) {
            pumlBuilder.append(cppClass.getName())
                    .append(" *-- ")
                    .append(composition)
                    .append("\n");
        }

        // 依存関係
        for (String dependency : cppClass.getDependencies()) {
            pumlBuilder.append(cppClass.getName())
                    .append(" ..> ")
                    .append(dependency)
                    .append("\n");
        }
    }

    private String getRelationshipArrow(Relationship.RelationType type) {
        switch (type) {
            case INHERITANCE:
                return "<|--";
            case AGGREGATION:
                return "o--";
            case COMPOSITION:
                return "*--";
            case DEPENDENCY:
                return "<..";
            case ASSOCIATION:
                return "--";
            default:
                return "--";
        }
    }
}
