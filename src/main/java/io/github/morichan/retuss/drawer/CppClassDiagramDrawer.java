package io.github.morichan.retuss.drawer;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.model.uml.CppClass.RelationshipInfo;
import javafx.application.Platform;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        if (!(cls instanceof CppClass)) {
            System.err.println("Warning: Not a CppClass instance: " + cls.getName());
            return;
        }
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

            // 関係の描画
            drawRelationships(pumlBuilder, cppClass);
        } catch (Exception e) {
            System.err.println("Error drawing class " + cls.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void appendAttribute(StringBuilder pumlBuilder, Attribute attr, CppClass cls) {
        try {
            pumlBuilder.append("  ")
                    .append(getVisibilitySymbol(attr.getVisibility()))
                    .append(" ");

            // 修飾子の追加
            Set<CppClass.Modifier> modifiers = cls.getModifiers(attr.getName().getNameText());
            if (!modifiers.isEmpty()) {
                pumlBuilder.append("{");
                List<String> modifierStrings = new ArrayList<>();
                if (modifiers.contains(CppClass.Modifier.STATIC)) {
                    modifierStrings.add("static");
                }
                if (modifiers.contains(CppClass.Modifier.CONST)) {
                    modifierStrings.add("const");
                }
                if (modifiers.contains(CppClass.Modifier.MUTABLE)) {
                    modifierStrings.add("mutable");
                }
                pumlBuilder.append(String.join(",", modifierStrings));
                pumlBuilder.append("} ");
            }

            // 属性名
            pumlBuilder.append(attr.getName().getNameText())
                    .append(" : ")
                    .append(formatType(attr.getType().toString()))
                    .append("\n");
        } catch (Exception e) {
            System.err.println("Error appending attribute " + attr.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void appendOperation(StringBuilder pumlBuilder, Operation op, CppClass cls) {
        try {
            pumlBuilder.append("  ")
                    .append(getVisibilitySymbol(op.getVisibility()))
                    .append(" ");

            // 修飾子の追加
            appendModifiers(pumlBuilder, cls.getModifiers(op.getName().getNameText()));

            pumlBuilder.append(op.getName().getNameText())
                    .append("(");

            // パラメータの型情報を表示
            List<String> params = new ArrayList<>();
            try {
                for (Parameter param : op.getParameters()) {
                    params.add(formatType(param.getType().toString()));
                }
            } catch (IllegalStateException e) {
            }
            pumlBuilder.append(String.join(", ", params));

            pumlBuilder.append(") : ")
                    .append(formatType(op.getReturnType().toString()))
                    .append("\n");
        } catch (Exception e) {
            System.err.println("Error appending operation " + op.getName() + ": " + e.getMessage());
        }
    }

    private void appendModifiers(StringBuilder pumlBuilder, Set<CppClass.Modifier> modifiers) {
        List<String> modifierStrings = new ArrayList<>();

        if (modifiers.contains(CppClass.Modifier.VIRTUAL)) {
            modifierStrings.add("virtual");
        }
        if (modifiers.contains(CppClass.Modifier.STATIC)) {
            modifierStrings.add("static");
        }
        if (modifiers.contains(CppClass.Modifier.ABSTRACT)) {
            modifierStrings.add("abstract");
        }
        if (modifiers.contains(CppClass.Modifier.CONST)) {
            modifierStrings.add("const");
        }

        if (!modifierStrings.isEmpty()) {
            pumlBuilder.append("{")
                    .append(String.join(",", modifierStrings))
                    .append("} ");
        }
    }

    private String formatType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "void";
        }

        // 空白を正規化
        type = type.trim().replaceAll("\\s+", " ");

        // std::を除去
        type = type.replaceAll("std::", "");

        // コレクション型の処理
        if (type.contains("vector<") || type.contains("list<")) {
            // コレクション型の場合も内部の型をエスケープ
            int startIndex = type.indexOf('<');
            int endIndex = type.lastIndexOf('>');
            if (startIndex != -1 && endIndex != -1) {
                String innerType = type.substring(startIndex + 1, endIndex);
                String formattedInner = formatType(innerType); // 再帰的に内部の型を処理
                return type.substring(0, startIndex + 1) + formattedInner + type.substring(endIndex);
            }
            return type;
        }

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

        // staticを除去
        if (type.startsWith("static ")) {
            type = type.substring(7).trim();
        }

        // 配列表記の処理
        String arraySize = "";
        if (type.matches(".*\\[\\d+\\]")) {
            arraySize = type.replaceAll(".*\\[(\\d+)\\].*", "[$1]");
            type = type.replaceAll("\\[\\d+\\]", "");
        }

        // 結果を組み立て
        StringBuilder result = new StringBuilder();
        if (isConst) {
            result.append("const ");
        }

        // 基本型を追加
        result.append(type.trim());

        // ポインタ/参照をエスケープして追加
        if (!suffix.isEmpty()) {
            String escapedSuffix = suffix.replace("*", "\\*").replace("&", "\\&");
            result.append(escapedSuffix);
        }

        // 配列サイズを追加
        if (!arraySize.isEmpty()) {
            result.append(arraySize);
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

    private void drawRelationships(StringBuilder pumlBuilder, CppClass cls) {
        // ターゲットクラスごとに関係をグループ化
        Map<String, Map<RelationshipInfo.RelationType, Set<RelationshipInfo>>> relationshipsByTarget = new HashMap<>();

        // 関係を整理
        for (RelationshipInfo relation : cls.getRelationships()) {
            relationshipsByTarget
                    .computeIfAbsent(relation.getTargetClass(), k -> new EnumMap<>(RelationshipInfo.RelationType.class))
                    .computeIfAbsent(relation.getType(), k -> new HashSet<>())
                    .add(relation);
        }

        // 各ターゲットクラスとの関係を描画
        for (Map.Entry<String, Map<RelationshipInfo.RelationType, Set<RelationshipInfo>>> targetEntry : relationshipsByTarget
                .entrySet()) {

            String targetClass = targetEntry.getKey();
            Map<RelationshipInfo.RelationType, Set<RelationshipInfo>> relationsByType = targetEntry.getValue();

            for (Map.Entry<RelationshipInfo.RelationType, Set<RelationshipInfo>> typeEntry : relationsByType
                    .entrySet()) {

                RelationshipInfo.RelationType type = typeEntry.getKey();
                Set<RelationshipInfo> relations = typeEntry.getValue();

                // 継承関係の場合は向きを逆にする
                if (type == RelationshipInfo.RelationType.INHERITANCE) {
                    pumlBuilder.append(targetClass);
                    appendRelationshipSymbol(pumlBuilder, type);
                    pumlBuilder.append(cls.getName());
                } else {
                    pumlBuilder.append(cls.getName());
                    appendRelationshipSymbol(pumlBuilder, type, getAggregatedMultiplicity(relations));
                    pumlBuilder.append(targetClass);
                }

                // 関係の詳細情報を追加（重複を避ける）
                Set<String> uniqueDetails = new LinkedHashSet<>();
                for (RelationshipInfo relation : relations) {
                    for (RelationshipInfo.RelationshipElement element : relation.getElements()) {
                        uniqueDetails.add(formatElement(element));
                    }
                }

                if (!uniqueDetails.isEmpty()) {
                    pumlBuilder.append(" : ").append(String.join("\\n", uniqueDetails));
                }

                pumlBuilder.append("\n");
            }
        }
    }

    private void appendRelationshipSymbol(StringBuilder pumlBuilder,
            RelationshipInfo.RelationType type,
            String multiplicity) {
        switch (type) {
            case INHERITANCE:
                pumlBuilder.append(" <|-- ");
                break;
            case COMPOSITION:
                pumlBuilder.append(" \"1\" *-- \"").append(multiplicity).append("\" ");
                break;
            case AGGREGATION:
                pumlBuilder.append(" \"1\" o-- \"").append(multiplicity).append("\" ");
                break;
            case ASSOCIATION:
                pumlBuilder.append(" \"1\" -- \"").append(multiplicity).append("\" ");
                break;
            case DEPENDENCY:
                pumlBuilder.append(" ..> ");
                break;
            case REALIZATION:
                pumlBuilder.append(" ..|> ");
                break;
        }
    }

    private void appendRelationshipSymbol(StringBuilder pumlBuilder,
            RelationshipInfo.RelationType type) {
        // 多重度なしバージョン（継承用）
        switch (type) {
            case INHERITANCE:
                pumlBuilder.append(" <|-- ");
                break;
            case REALIZATION:
                pumlBuilder.append(" ..|> ");
                break;
            default:
                pumlBuilder.append(" -- ");
                break;
        }
    }

    private String formatElement(RelationshipInfo.RelationshipElement element) {
        StringBuilder sb = new StringBuilder();

        // 可視性の追加
        sb.append(getVisibilitySymbol(element.getVisibility())).append(" ");

        switch (element.getElemType()) {
            case OPERATION:
                // virtual修飾子の表示
                if (element.getModifiers().contains(CppClass.Modifier.VIRTUAL) ||
                        element.isPureVirtual()) {
                    sb.append("{virtual} ");
                }
                // staticの表示
                if (element.getModifiers().contains(CppClass.Modifier.STATIC)) {
                    sb.append("{static} ");
                }
                sb.append(cleanName(element.getName()));
                if (element.getType() != null) {
                    sb.append(" : ").append(cleanTypeForDisplay(element.getType()));
                }
                break;

            case ATTRIBUTE:
                if (element.getModifiers().contains(CppClass.Modifier.STATIC)) {
                    sb.append("{static} ");
                }
                if (element.getModifiers().contains(CppClass.Modifier.CONST)) {
                    sb.append("{const} ");
                }
                sb.append(cleanName(element.getName()));
                if (element.getType() != null) {
                    sb.append(" : ").append(cleanTypeForDisplay(element.getType()));
                }
                if (!element.getMultiplicity().equals("1")) {
                    sb.append("[").append(element.getMultiplicity()).append("]");
                }
                break;

            case PARAMETER:
                sb.append(cleanName(element.getName()));
                break;
        }

        return sb.toString();
    }

    private String cleanName(String name) {
        return name.replaceAll("[*&]", "").trim();
    }

    private String cleanTypeForDisplay(String type) {
        if (type == null)
            return "";
        return type.replaceAll("\\s+", " ")
                .replaceAll("const", "")
                .replaceAll("std::", "")
                .trim();
    }

    private String getAggregatedMultiplicity(Set<RelationshipInfo> relations) {
        Set<String> multiplicities = new HashSet<>();
        for (RelationshipInfo relation : relations) {
            for (RelationshipInfo.RelationshipElement element : relation.getElements()) {
                multiplicities.add(element.getMultiplicity());
            }
        }

        if (multiplicities.contains("*"))
            return "*";

        try {
            return multiplicities.stream()
                    .map(Integer::parseInt)
                    .max(Integer::compareTo)
                    .map(String::valueOf)
                    .orElse("1");
        } catch (NumberFormatException e) {
            return "1";
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
}
