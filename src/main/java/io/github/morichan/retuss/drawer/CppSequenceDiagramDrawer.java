package io.github.morichan.retuss.drawer;

import io.github.morichan.retuss.model.CppFile;
import io.github.morichan.retuss.model.CppModel;
import javafx.scene.control.TabPane;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public class CppSequenceDiagramDrawer {
    private TabPane tabPaneInSequenceTab;
    private CppModel model = CppModel.getInstance();

    public CppSequenceDiagramDrawer(TabPane tabPaneInSequenceTab) {
        this.tabPaneInSequenceTab = tabPaneInSequenceTab;
    }

    public void draw(CppFile headerFile, CppFile implFile, String methodName, WebView webView) {
        System.out.println("シーケンスドロー！");
        StringBuilder puStrBuilder = new StringBuilder();
        puStrBuilder.append("@startuml\n");
        puStrBuilder.append("scale 1.5\n");
        puStrBuilder.append("skinparam style strictuml\n");

        // シーケンス図の生成
        String sequenceDiagram = model.generateSequenceDiagram(
                headerFile.getFileName().replace(".hpp", ""),
                methodName);

        if (!sequenceDiagram.isEmpty()) {
            puStrBuilder.append(sequenceDiagram);
            System.out.println("シーケンスからっぽ！");
        } else {
            // 空の場合はデフォルトの表示
            String className = headerFile.getFileName().replace(".hpp", "");
            puStrBuilder.append(String.format("participant \"%s\"\n", className));
            puStrBuilder.append("[-> \"").append(className).append("\" : ").append(methodName).append("\n");
            puStrBuilder.append("activate \"").append(className).append("\"\n");
            puStrBuilder.append("[<-- \"").append(className).append("\"\n");
            puStrBuilder.append("deactivate \"").append(className).append("\"\n");
        }

        puStrBuilder.append("@enduml\n");

        System.out.println("Generated PlantUML:\n" + puStrBuilder.toString());

        // PlantUMLでSVGを生成
        try {
            SourceStringReader reader = new SourceStringReader(puStrBuilder.toString());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
            os.close();

            String svg = new String(os.toByteArray(), Charset.forName("UTF-8"));
            webView.getEngine().loadContent(svg);
        } catch (Exception e) {
            System.err.println("Failed to generate sequence diagram: " + e.getMessage());
            e.printStackTrace();
        }
    }
}