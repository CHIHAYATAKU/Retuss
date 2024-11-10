package io.github.morichan.retuss.drawer;

import io.github.morichan.retuss.translator.model.MethodCall;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.CppFile;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.translator.cpp.CppTranslator;
import javafx.scene.control.TabPane;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;

public class CppSequenceDiagramDrawer {
    private TabPane tabPaneInSequenceTab;
    private CppTranslator translator;

    public CppSequenceDiagramDrawer(TabPane tabPaneInSequenceTab) {
        this.tabPaneInSequenceTab = tabPaneInSequenceTab;
        this.translator = new CppTranslator();
    }

    public void draw(CppFile headerFile, CppFile implFile, String methodName, WebView webView) {
        try {
            // translatorを使用してPlantUML文字列を生成
            String puml = translator.generateSequenceDiagram(
                    headerFile.getCode(),
                    implFile.getCode(),
                    methodName);

            System.out.println("Generated sequence diagram for " + methodName + ":\n" + puml);

            // PlantUMLからSVGを生成
            SourceStringReader reader = new SourceStringReader(puml);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            reader.generateImage(os, new FileFormatOption(FileFormat.SVG));

            // SVGを表示
            String svg = new String(os.toByteArray(), Charset.forName("UTF-8"));
            webView.getEngine().loadContent(svg);

            System.out.println("Loaded sequence diagram to WebView");
        } catch (Exception e) {
            System.err.println("Error drawing sequence diagram: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String generateSequence(CppFile headerFile, CppFile implFile, String methodName) {
        StringBuilder sb = new StringBuilder();

        if (!headerFile.getUmlClassList().isEmpty()) {
            Class mainClass = headerFile.getUmlClassList().get(0);
            String className = mainClass.getName();

            // フレーム名とメインのライフライン
            sb.append(String.format("mainframe %s::%s\n", className, methodName));
            String mainLifelineName = String.format(":%s", className);

            // 最初のメッセージとアクティベーション
            sb.append(String.format("[-> \"%s\" : %s()\n", mainLifelineName, methodName));
            sb.append(String.format("activate \"%s\"\n", mainLifelineName));

            // メソッドの実装を解析
            List<MethodCall> methodCalls = translator.analyzeMethodCalls(implFile.getCode(), methodName);
            for (MethodCall call : methodCalls) {
                addMethodCallToSequence(sb, call, mainLifelineName);
            }

            // 戻りメッセージとデアクティベーション
            sb.append(String.format("[<<-- \"%s\"\n", mainLifelineName));
            sb.append(String.format("deactivate \"%s\"\n", mainLifelineName));
        }

        return sb.toString();
    }

    private void addMethodCallToSequence(StringBuilder sb, MethodCall call, String mainLifelineName) {
        if (call.getCaller().equals(call.getCallee())) {
            // 自己呼び出し
            sb.append(String.format("\"%s\" -> \"%s\" : %s\n",
                    mainLifelineName,
                    mainLifelineName,
                    formatMethodCall(call)));
            sb.append(String.format("activate \"%s\"\n", mainLifelineName));
            sb.append(String.format("deactivate \"%s\"\n", mainLifelineName));
        } else {
            // 他オブジェクトへの呼び出し
            String calleeLifeline = String.format(":%s", call.getCallee());
            sb.append(String.format("\"%s\" -> \"%s\" : %s\n",
                    mainLifelineName,
                    calleeLifeline,
                    formatMethodCall(call)));
            sb.append(String.format("activate \"%s\"\n", calleeLifeline));
            sb.append(String.format("\"%s\" <<-- \"%s\"\n",
                    mainLifelineName,
                    calleeLifeline));
            sb.append(String.format("deactivate \"%s\"\n", calleeLifeline));
        }
    }

    private String formatMethodCall(MethodCall call) {
        String args = String.join(", ", call.getArguments());
        return String.format("%s(%s)", call.getMethodName(), args);
    }
}