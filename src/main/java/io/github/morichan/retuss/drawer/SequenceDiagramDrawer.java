package io.github.morichan.retuss.drawer;

import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.*;
import javafx.scene.control.TabPane;
import javafx.scene.web.WebView;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class SequenceDiagramDrawer {
    private TabPane tabPaneInSequenceTab;
    private JavaModel model = JavaModel.getInstance();

    public SequenceDiagramDrawer(TabPane tabPaneInSequenceTab) {
        this.tabPaneInSequenceTab = tabPaneInSequenceTab;
    }

    public void draw(CodeFile codeFile, Interaction interaction, WebView webView) {
        // plantUML構文を生成する
        StringBuilder puStrBuilder = new StringBuilder("@startuml\n");
        puStrBuilder.append("scale 1.5\n");
        puStrBuilder.append("skinparam style strictuml\n");
        puStrBuilder.append(interactionToPlantUml(codeFile, interaction));
        puStrBuilder.append("@enduml\n");

        System.out.print(puStrBuilder.toString());
        // plantUMLでSDのSVGを生成する
        SourceStringReader reader = new SourceStringReader(puStrBuilder.toString());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        // Write the first image to "os"
        try {
            String desc = reader.generateImage(os, new FileFormatOption(FileFormat.SVG));
            os.close();
        } catch (Exception e) {
            return;
        }

        // The XML is stored into svg
        final String svg = new String(os.toByteArray(), Charset.forName("UTF-8"));

        webView.getEngine().loadContent(svg);

    }

    private String interactionToPlantUml(CodeFile codeFile, Interaction interaction) {
        StringBuilder sb = new StringBuilder();

        // フレーム
        sb.append(String.format("mainframe %s\n", interaction.getName()));

        // 図示対象の操作呼び出しメッセージ
        String mainLifelineName = String.format(":%s", codeFile.getUmlClassList().get(0).getName());
        sb.append(String.format("[-> \"%s\" \n", mainLifelineName));
        // 活性区間開始
        sb.append(String.format("activate \"%s\"\n", mainLifelineName));

        // メッセージ
        for (InteractionFragment interactionFragment : interaction.getInteractionFragmentList()) {
            sb.append(interactionFragmentToPlantUml(interactionFragment, mainLifelineName));
        }

        // 図示対象の操作呼び出しメッセージの戻りメッセージ
        sb.append(String.format("[<<-- \"%s\"\n", mainLifelineName));
        // 図示対象の操作呼び出しメッセージの活性区間終了
        sb.append(String.format("deactivate \"%s\"\n", mainLifelineName));

        return sb.toString();
    }

    private String interactionFragmentToPlantUml(InteractionFragment interactionFragment,
            String startLifelineSignature) {
        StringBuilder sb = new StringBuilder();

        if (interactionFragment instanceof OccurenceSpecification) {
            OccurenceSpecification occurenceSpecification = (OccurenceSpecification) interactionFragment;
            Lifeline startLifeline = occurenceSpecification.getLifeline();
            Lifeline endLifeline = occurenceSpecification.getMessage().getMessageEnd().getLifeline();
            Message message = occurenceSpecification.getMessage();

            if (message.getMessageSort() == MessageSort.synchCall) {
                InteractionUse interactionUse = (InteractionUse) occurenceSpecification.getMessage().getMessageEnd()
                        .getInteractionFragmentList().get(0);

                if (startLifeline.getSignature().equals(endLifeline.getSignature())) {
                    // 自ライフラインに対するメッセージ
                    sb.append(String.format("\"%s\" -> \"%s\": %s\n", startLifelineSignature, startLifelineSignature,
                            message.getSignature()));
                    if (model.findClass(endLifeline.getType()).isPresent() && model.findClass(endLifeline.getType())
                            .get().findInteraction(interactionUse.getInteractionName()).isPresent()) {
                        sb.append(String.format("activate \"%s\"\n", startLifelineSignature));
                        for (InteractionFragment ifInMessage : model.findClass(endLifeline.getType()).get()
                                .findInteraction(interactionUse.getInteractionName()).get()
                                .getInteractionFragmentList()) {
                            sb.append(interactionFragmentToPlantUml(ifInMessage, endLifeline.getSignature()));
                        }
                        sb.append(String.format("deactivate \"%s\"\n", startLifelineSignature));
                    } else {
                        sb.append(String.format("ref over \"%s\" : %s \n", startLifelineSignature,
                                interactionUse.getSignature()));
                    }
                } else {
                    // 他ライフラインに対するメッセージ
                    sb.append(String.format("\"%s\" -> \"%s\": %s\n", startLifelineSignature,
                            endLifeline.getSignature(), message.getSignature()));
                    sb.append(String.format("activate \"%s\"\n", endLifeline.getSignature()));
                    if (model.findClass(endLifeline.getType()).isPresent() && model.findClass(endLifeline.getType())
                            .get().findInteraction(interactionUse.getInteractionName()).isPresent()) {
                        for (InteractionFragment ifInMessage : model.findClass(endLifeline.getType()).get()
                                .findInteraction(interactionUse.getInteractionName()).get()
                                .getInteractionFragmentList()) {
                            sb.append(interactionFragmentToPlantUml(ifInMessage, endLifeline.getSignature()));
                        }
                    } else {
                        sb.append(String.format("ref over \"%s\" : %s \n", endLifeline.getSignature(),
                                interactionUse.getSignature()));
                    }
                    sb.append(
                            String.format("\"%s\" <<-- \"%s\" \n", startLifelineSignature, endLifeline.getSignature()));
                    sb.append(String.format("deactivate \"%s\"\n", endLifeline.getSignature()));
                }
            } else if (message.getMessageSort() == MessageSort.createMessage) {
                sb.append(String.format("\"%s\" -->> \"%s\" ** : %s\n", startLifeline.getSignature(),
                        endLifeline.getSignature(), message.getSignature()));
            }

        } else if (interactionFragment instanceof CombinedFragment) {
            CombinedFragment combinedFragment = (CombinedFragment) interactionFragment;
            ArrayList<InteractionOperand> interactionOperandList = combinedFragment.getInteractionOperandList();

            for (int i = 0; i < interactionOperandList.size(); i++) {
                InteractionOperand interactionOperand = interactionOperandList.get(i);
                if (i == 0) {
                    sb.append(String.format("%s %s\n", combinedFragment.getKind().toString().toLowerCase(),
                            interactionOperand.getGuard()));
                } else {
                    // altでのみ使用する
                    sb.append(String.format("else %s\n", interactionOperand.getGuard()));
                }

                for (InteractionFragment interactionFragmentInCF : interactionOperand.getInteractionFragmentList()) {
                    sb.append(interactionFragmentToPlantUml(interactionFragmentInCF, startLifelineSignature));
                }
            }
            sb.append("end\n");
        }

        return sb.toString();
    }

}
