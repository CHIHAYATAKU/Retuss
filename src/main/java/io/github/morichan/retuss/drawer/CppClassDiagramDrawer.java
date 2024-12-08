package io.github.morichan.retuss.drawer;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.UmlModel;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CppClassDiagramDrawer {
    private double currentScale = 1.0;
    private final CppModel model;
    private final UmlModel umlModel;
    private WebView webView;
    private TextArea codeArea;
    private final ExecutorService diagramExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<String> lastSvg = new AtomicReference<>();
    private volatile boolean isUpdating = false;

    public void setScale(double scale) {
        if (this.currentScale != scale) { // 値が実際に変化した場合のみ
            this.currentScale = scale;
            clearCache(); // キャッシュをクリアして
            draw(); // 再描画
        }
    }

    public CppClassDiagramDrawer(WebView webView) {
        this.model = CppModel.getInstance();
        this.umlModel = UmlModel.getInstance();
        this.webView = webView;
        this.codeArea = codeArea;
        System.out.println("CppClassDiagramDrawer initialized");
    }

    private void drawClass(StringBuilder pumlBuilder, CppHeaderClass cls) {
        try {
            if (cls.getAbstruct() && !cls.getInterface()) {
                pumlBuilder.append("abstract ");
            }
            pumlBuilder.append("class ").append(cls.getName());

            if (cls.getInterface() && cls.getAbstruct() && cls.getAttributeList().isEmpty()) {
                pumlBuilder.append(" <<interface>>");
            }

            pumlBuilder.append(" {\n");

            // 属性
            for (Attribute attr : cls.getAttributeList()) {
                appendAttribute(pumlBuilder, attr, cls);
            }

            // メソッド
            for (Operation op : cls.getOperationList()) {
                appendOperation(pumlBuilder, op, cls);
            }

            pumlBuilder.append("}\n\n");

            // 関係の描画
            pumlBuilder.append(cls.getRelationshipManager().generatePlantUmlRelationships());
        } catch (Exception e) {
            System.err.println("Error drawing class " + cls.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void appendAttribute(StringBuilder pumlBuilder, Attribute attr, CppHeaderClass cls) {
        try {
            pumlBuilder.append("  ")
                    .append(getVisibilitySymbol(attr.getVisibility()))
                    .append(" ");

            // 修飾子の追加
            Set<Modifier> modifiers = cls.getModifiers(attr.getName().getNameText());
            appendAttributeModifiers(pumlBuilder, modifiers);

            // 属性名と型を表示
            String cleanName = cleanName(attr.getName().getNameText());
            pumlBuilder.append(cleanName)
                    .append(" : ")
                    .append(formatType(attr.getType().toString()));

            // 初期値の追加（エラー処理を追加）
            try {
                if (attr.getDefaultValue() != null &&
                        attr.getDefaultValue().toString() != null &&
                        !attr.getDefaultValue().toString().isEmpty()) {
                    pumlBuilder.append(" = ")
                            .append(attr.getDefaultValue().toString());
                }
            } catch (Exception e) {
                // 初期値の処理でエラーが発生しても続行
                System.err.println("Warning: Could not append default value for " + cleanName);
            }

            pumlBuilder.append("\n");
        } catch (Exception e) {
            System.err.println("Error appending attribute " + attr.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void appendAttributeModifiers(StringBuilder pumlBuilder, Set<Modifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty())
            return;

        List<String> modifierStrings = new ArrayList<>();
        for (Modifier modifier : modifiers) {
            if (modifier.isApplicableTo(ElementType.ATTRIBUTE)) {
                modifierStrings.add(modifier.getPlantUmlText(false));
            }
        }

        if (!modifierStrings.isEmpty()) {
            pumlBuilder.append(String.join(" ", modifierStrings))
                    .append(" ");
        }
    }

    private void appendOperationModifiers(StringBuilder pumlBuilder, Set<Modifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty())
            return;

        List<String> modifierStrings = new ArrayList<>();
        for (Modifier modifier : modifiers) {
            if (modifier.isApplicableTo(ElementType.OPERATION) && !modifier.equals(Modifier.OVERRIDE)) {
                modifierStrings.add(modifier.getPlantUmlText(false));
            }
        }

        if (!modifierStrings.isEmpty()) {
            pumlBuilder.append(String.join(" ", modifierStrings))
                    .append(" ");
        }
    }

    private void appendOperation(StringBuilder pumlBuilder, Operation op, CppHeaderClass cls) {
        try {
            pumlBuilder.append("  ")
                    .append(getVisibilitySymbol(op.getVisibility()))
                    .append(" ");

            String methodName = op.getName().getNameText();
            boolean isConstructor = methodName.equals(cls.getName());
            boolean isDestructor = methodName.startsWith("~");

            // 修飾子の追加
            Set<Modifier> modifiers = cls.getModifiers(methodName);
            if (modifiers != null && !modifiers.isEmpty()) {
                appendOperationModifiers(pumlBuilder, modifiers);
            }

            // メソッド名
            pumlBuilder.append(op.getName().getNameText())
                    .append("(");

            try {
                // パラメータリストの表示
                List<Parameter> params = op.getParameters();
                if (params != null && !params.isEmpty()) {
                    List<String> paramStrings = new ArrayList<>();
                    for (Parameter param : params) {
                        if (param != null && param.getName() != null && param.getType() != null) {
                            String paramStr = param.getName().getNameText() + " : " +
                                    formatType(param.getType().toString());
                            paramStrings.add(paramStr);
                        }
                    }
                    pumlBuilder.append(String.join(", ", paramStrings));
                }
            } catch (Exception e) {
            }

            pumlBuilder.append(")");
            // 戻り値型の表示
            if (!isConstructor && !isDestructor) {
                String returnType = op.getReturnType().toString();
                if (returnType != null && !returnType.isEmpty()) {
                    pumlBuilder.append(" : ").append(formatType(returnType));
                }
            }

            if (modifiers.contains(Modifier.OVERRIDE)) {
                pumlBuilder.append(" " + Modifier.OVERRIDE.getPlantUmlText(false));
            }

            pumlBuilder.append("\n");
        } catch (Exception e) {
            System.err.println("Error appending operation " + op.getName() + ": " + e.getMessage());
        }
    }

    private String formatType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "void";
        }

        StringBuilder result = new StringBuilder();

        // 配列サイズの抽出
        String arraySize = "";
        if (type.matches(".*\\[\\d+\\]")) {
            arraySize = type.replaceAll(".*\\[(\\d+)\\].*", "[$1]");
            type = type.replaceAll("\\[\\d+\\]", "");
        }

        // 基本型の処理
        result.append(type.trim());

        // 配列サイズの追加
        if (!arraySize.isEmpty()) {
            result.append(arraySize);
        }

        return result.toString();
    }

    private boolean isCollectionType(String type) {
        // 型名からテンプレート部分を含めて判定
        return type.matches(".*(?:vector|list|set|map|array|queue|stack|deque)<.*>");
    }

    public void draw() {
        if (isUpdating)
            return; // スキップ if already updating
        isUpdating = true;

        CompletableFuture.supplyAsync(() -> {
            try {
                List<CppHeaderClass> classes = model.getHeaderClasses();
                System.out.println("DEBUG: CppClassDiagramDrawer - Number of classes: " + classes.size());
                for (CppHeaderClass cls : classes) {
                    System.out.println("DEBUG: CppClassDiagramDrawer - Drawing class: " + cls.getName());
                    System.out.println("DEBUG: CppClassDiagramDrawer - Class type: " + cls.getClass().getName());
                    System.out.println("DEBUG: CppClassDiagramDrawer - Attributes: " + cls.getAttributeList().size());
                    System.out.println("DEBUG: CppClassDiagramDrawer - Operations: " + cls.getOperationList().size());
                }

                StringBuilder pumlBuilder = new StringBuilder("@startuml\n");
                pumlBuilder.append("skinparam style strictuml\n");
                pumlBuilder.append(" skinparam linetype ortho\n");
                // pumlBuilder.append("skinparam linetype polyline\n");
                pumlBuilder.append("skinparam classAttributeIconSize 0\n");
                pumlBuilder.append("skinparam LineThickness 1.5\n");
                pumlBuilder.append("scale ").append(String.format("%.2f", currentScale)).append("\n");

                for (CppHeaderClass cls : classes) {
                    System.out.println("Processing class: " + cls.getName());
                    System.out.println("Attributes: " + cls.getAttributeList().size());
                    System.out.println("Operations: " + cls.getOperationList().size());
                    drawClass(pumlBuilder, cls);
                }

                pumlBuilder.append("@enduml\n");
                String puml = pumlBuilder.toString();
                System.out.println("Generated PlantUML:\n" + puml);

                // UMLModelの更新
                umlModel.setPlantUml(puml);

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

    private String cleanName(String name) {
        return name.replaceAll("[*&]", "").trim();
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
