package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.Model;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.util.Pair;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CodeController {
    @FXML
    private TabPane codeTabPane;
    private Model model = Model.getInstance();
    private List<Pair<CodeFile, Tab>> fileTabList = new ArrayList<>();

    @FXML
    private void initialize() {
        model.setCodeController(this);
    }

    @FXML
    private void createNewFile() {
        model.addCodeFile();
    }

    /**
     * <p> CodeFileに対応するコードタブを更新する </p>
     */
    public void updateCodeTab(CodeFile changedCodeFile) {
        UUID changedFileId = changedCodeFile.getID();

        // 更新対象のタブを探索
        Tab targetTab;
        for(Pair<CodeFile, Tab> fileTabPair : fileTabList) {
            if(changedFileId.equals(fileTabPair.getKey().getID())) {
                targetTab = fileTabPair.getValue();
                CodeArea codeArea = createCodeArea();
                codeArea.replaceText("Test updateCodeTab");
                targetTab.setContent(codeArea);
                targetTab.isSelected();
                return;
            }
        }

        // タブが存在しない場合は新たに生成する
        targetTab = createCodeTab(changedCodeFile);
        codeTabPane.getTabs().add(targetTab);
        fileTabList.add(new Pair<>(changedCodeFile, targetTab));
        targetTab.isSelected();
    }

    private Tab createCodeTab(CodeFile codeFile) {
        CodeArea codeArea = createCodeArea();
        codeArea.replaceText("Test createCodeTab");

        AnchorPane codeAnchor = new AnchorPane(codeArea);
        AnchorPane.setBottomAnchor(codeArea, 0.0);
        AnchorPane.setTopAnchor(codeArea, 0.0);
        AnchorPane.setLeftAnchor(codeArea, 0.0);
        AnchorPane.setRightAnchor(codeArea, 0.0);

        Tab codeTab = new Tab();
        codeTab.setContent(codeAnchor);
        codeTab.setText(codeFile.getFileName());
        codeTab.setClosable(false);

        return codeTab;
    }

    private CodeArea createCodeArea() {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
//        codeArea.setOnKeyTyped(event -> convertCodeToUml(Language.Java));
        return codeArea;
    }
}
