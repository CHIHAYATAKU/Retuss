package io.github.morichan.retuss.drawer;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.UmlModel;
import io.github.morichan.retuss.model.uml.Class;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class JavaClassDiagramDrawer {
    private JavaModel javaModel = JavaModel.getInstance();
    private final UmlModel umlModel;
    private WebView webView;
    private TextArea codeArea;
    private final ExecutorService diagramExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<String> lastSvg = new AtomicReference<>();
    private volatile boolean isUpdating = false;

    public JavaClassDiagramDrawer(WebView webView) {
        this.javaModel = JavaModel.getInstance();
        this.umlModel = UmlModel.getInstance();
        this.webView = webView;
        this.codeArea = codeArea;
        System.out.println("JavaClassDiagramDrawer initialized");
    }

    public void draw() {
        if (isUpdating)
            return; // スキップ if already updating
        isUpdating = true;

        CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("DEBUG: Starting to draw Java class diagram");
                List<Class> umlClassList = JavaModel.getInstance().getUmlClassList();
                System.out.println("DEBUG: Found " + umlClassList.size() + " classes to draw");

                System.err.println("classList : " + umlClassList.toString());

                StringBuilder pumlBuilder = new StringBuilder("@startuml\n");
                pumlBuilder.append("skinparam style strictuml\n");
                pumlBuilder.append(" skinparam linetype ortho\n");
                // pumlBuilder.append("skinparam linetype polyline\n");
                pumlBuilder.append("skinparam classAttributeIconSize 0\n");
                pumlBuilder.append("skinparam LineThickness 1.5\n");
                pumlBuilder.append("scale ").append("1.0").append("\n");
                for (Class umlClass : umlClassList) {
                    pumlBuilder.append(umlClassToPlantUml(umlClass));
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

                SourceStringReader reader = new SourceStringReader(pumlBuilder.toString());
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
        }, diagramExecutor).thenAcceptAsync(svg -> {
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
        for (Class umlClass : javaModel.getUmlClassList()) {
            if (type.getName().getNameText().equals(umlClass.getName())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }
}
