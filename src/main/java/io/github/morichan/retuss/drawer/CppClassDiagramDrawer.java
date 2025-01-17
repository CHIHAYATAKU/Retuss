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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CppClassDiagramDrawer {
    private final CppModel model;
    private final UmlModel umlModel;
    private WebView webView;
    private TextArea codeArea;
    private final ExecutorService diagramExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<String> lastSvg = new AtomicReference<>();
    private volatile boolean isUpdating = false;
    private volatile boolean updatePending = false;
    private double savedScrollX = 0;
    private double savedScrollY = 0;

    private void saveScrollPosition() {
        try {
            Object x = webView.getEngine()
                    .executeScript("document.documentElement.scrollLeft || document.body.scrollLeft");
            Object y = webView.getEngine()
                    .executeScript("document.documentElement.scrollTop || document.body.scrollTop");
            savedScrollX = x instanceof Number ? ((Number) x).doubleValue() : 0;
            savedScrollY = y instanceof Number ? ((Number) y).doubleValue() : 0;
            System.out.println("Saving scroll position - X: " + savedScrollX + ", Y: " + savedScrollY);
        } catch (Exception e) {
            System.err.println("Error saving scroll position: " + e.getMessage());
        }
    }

    private void restoreScrollPosition() {
        try {
            String script = String.format(
                    "setTimeout(function() { window.scrollTo(%f, %f); }, 100);",
                    savedScrollX, savedScrollY);
            webView.getEngine().executeScript(script);
            System.out.println("Restored scroll position - X: " + savedScrollX + ", Y: " + savedScrollY);
        } catch (Exception e) {
            System.err.println("Error restoring scroll position: " + e.getMessage());
        }
    }

    public CppClassDiagramDrawer(WebView webView) {
        this.model = CppModel.getInstance();
        this.umlModel = UmlModel.getInstance();
        this.webView = webView;
        this.codeArea = codeArea;
        System.out.println("CppClassDiagramDrawer initialized");
    }

    private void drawClass(StringBuilder pumlBuilder, CppHeaderClass cls, String selectedClassName) {
        try {
            String namespace = cls.getNamespace();
            if (!namespace.isEmpty()) {
                pumlBuilder.append("namespace ").append(namespace).append(" {\n");
            }
            // enumの場合の処理を分離
            if (cls.getEnum()) {
                pumlBuilder.append("enum ");
                pumlBuilder.append(cls.getName());

                // ハイライト処理
                if (cls.getName().equals(selectedClassName)) {
                    pumlBuilder.append(" #90EE90");
                } else if (!cls.getRelationshipManager().getRelationshipsWith(selectedClassName).isEmpty()) {
                    pumlBuilder.append(" #ADD8E6");
                }

                pumlBuilder.append(" {\n");

                // enum値の表示
                for (CppHeaderClass.EnumValue value : cls.getEnumValues()) {
                    pumlBuilder.append("  ").append(value.getName());
                    value.getValue().ifPresent(v -> pumlBuilder.append(" = ").append(v));
                    pumlBuilder.append("\n");
                }

                pumlBuilder.append("}\n\n");
                // 関係の描画
                pumlBuilder.append(cls.getRelationshipManager().generatePlantUmlRelationships());
                return;
            }

            if (cls.getInterface() && cls.getAbstruct() && cls.getAttributeList().isEmpty()) {
                pumlBuilder.append("interface ");
                pumlBuilder.append(cls.getName());
            } else {
                if (cls.getAbstruct() && !cls.getInterface()) {
                    pumlBuilder.append("abstract ");
                }
                pumlBuilder.append("class ").append(cls.getName());
            }

            if (!cls.getRelationshipManager().getRelationshipsWith(selectedClassName).isEmpty()) {
                pumlBuilder.append(" #ADD8E6");
            } else if (cls.getName().equals(selectedClassName)) {
                pumlBuilder.append(" #90EE90");
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
            if (!namespace.isEmpty()) {
                pumlBuilder.append("}\n");
            }
        } catch (Exception e) {
            System.err.println("Error drawing class " + cls.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void drawSimpleClass(StringBuilder pumlBuilder, CppHeaderClass cls) {
        try {
            String namespace = cls.getNamespace();
            if (!namespace.isEmpty()) {
                pumlBuilder.append("namespace ").append(namespace).append(" {\n");
            }
            if (cls.getEnum()) {
                pumlBuilder.append("enum ");
                pumlBuilder.append(cls.getName());
                pumlBuilder.append(" {\n");

                // enum値の表示
                for (CppHeaderClass.EnumValue value : cls.getEnumValues()) {
                    pumlBuilder.append("  ").append(value.getName());
                    value.getValue().ifPresent(v -> pumlBuilder.append(" = ").append(v));
                    pumlBuilder.append("\n");
                }

                pumlBuilder.append("}\n\n");
                pumlBuilder.append(cls.getRelationshipManager().generatePlantUmlRelationships());
                return;
            }

            if (cls.getInterface() && cls.getAbstruct() && cls.getAttributeList().isEmpty()) {
                pumlBuilder.append("interface ");
                pumlBuilder.append(cls.getName());
            } else {
                if (cls.getAbstruct() && !cls.getInterface()) {
                    pumlBuilder.append("abstract ");
                }
                pumlBuilder.append("class ").append(cls.getName());
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
            if (!namespace.isEmpty()) {
                pumlBuilder.append("}\n");
            }
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
            if (modifiers != null) {
                if (modifiers.contains(Modifier.OVERRIDE)) {
                    pumlBuilder.append(" " + Modifier.OVERRIDE.getPlantUmlText(false));
                }
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

    public void draw(String selectedClassName) {
        if (isUpdating) {
            updatePending = true; // 更新中の場合は次の更新をマーク
            return;
        }
        System.out.println("DEBUG: selectedClassName = " + selectedClassName);
        executeDraw(selectedClassName);
    }

    private void executeDraw(String selectedClassName) {
        isUpdating = true;
        updatePending = false;

        CompletableFuture.supplyAsync(() -> {
            try {
                List<CppHeaderClass> classes = model.getHeaderClasses();
                System.out.println("DEBUG: CppClassDiagramDrawer - Number of classes: " + classes.size());

                StringBuilder pumlBuilder = new StringBuilder("@startuml\n");
                // pumlBuilder.append("skinparam style strictuml\n");
                pumlBuilder.append(" skinparam linetype ortho\n");
                // pumlBuilder.append("skinparam linetype polyline\n");
                pumlBuilder.append("skinparam classAttributeIconSize 0\n");
                pumlBuilder.append("skinparam LineThickness 1.5\n");
                pumlBuilder.append("hide empty members\n"); // 空のメンバーを非表示
                pumlBuilder.append("skinparam enumBackgroundColor White\n"); // enum背景色
                pumlBuilder.append("skinparam enumBorderColor Black\n");
                pumlBuilder.append("scale ").append("1.0").append("\n");

                if (!classes.isEmpty() && classes != null) {
                    for (CppHeaderClass cls : classes) {
                        drawClass(pumlBuilder, cls, selectedClassName);
                    }
                }

                pumlBuilder.append("@enduml\n");
                String puml = pumlBuilder.toString();
                System.out.println("Generated PlantUML:\n" + puml);

                // UMLModelの更新
                StringBuilder simplePumlBuilder = new StringBuilder("@startuml\n");
                // simplePumlBuilder.append("skinparam style strictuml\n");
                simplePumlBuilder.append(" skinparam linetype ortho\n");
                simplePumlBuilder.append("skinparam classAttributeIconSize 0\n");
                simplePumlBuilder.append("skinparam LineThickness 1.5\n");
                // enum用のスキンパラメータを追加
                // simplePumlBuilder.append("hide empty members\n");
                simplePumlBuilder.append("skinparam enumBackgroundColor White\n");
                simplePumlBuilder.append("skinparam enumBorderColor Black\n");

                simplePumlBuilder.append("scale ").append("1.0").append("\n");
                if (!classes.isEmpty() && classes != null) {
                    for (CppHeaderClass cls : classes) {
                        drawSimpleClass(pumlBuilder, cls);
                    }
                }

                simplePumlBuilder.append("@enduml\n");
                String simplePuml = simplePumlBuilder.toString();
                umlModel.setPlantUml(simplePuml);

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
                        saveScrollPosition();
                        if (svg != null) {
                            webView.getEngine().loadContent(svg);
                        }
                    } finally {
                        isUpdating = false;
                        // 保留中の更新があれば再度描画を実行
                        if (updatePending) {
                            Platform.runLater(() -> {
                                executeDraw(selectedClassName);
                                ;
                            });
                        }
                        // 少し遅延を入れてスクロール位置を復元
                        Platform.runLater(() -> {
                            restoreScrollPosition();
                        });
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
