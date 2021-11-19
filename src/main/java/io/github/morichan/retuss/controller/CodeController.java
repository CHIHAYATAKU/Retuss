package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.Model;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CodeController {
    @FXML private TabPane codeTabPane;
    private Model model = Model.getInstance();
    private List<Pair<CodeFile, Tab>> fileTabList = new ArrayList<>();

    @FXML private void initialize() {
        model.setCodeController(this);
    }

    @FXML private void showNewFileDialog() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/newFileDialog.fxml"));
            Parent parent = fxmlLoader.load();
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New File Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * <p> ソースコードを更新する。AST->コードタブ </p>
     */
    public void updateCodeTab(CodeFile changedCodeFile) {
        UUID changedFileId = changedCodeFile.getID();

        // 更新対象のタブを探索
        Tab targetTab;
        for(Pair<CodeFile, Tab> fileTabPair : fileTabList) {
            if(changedFileId.equals(fileTabPair.getKey().getID())) {
                targetTab = fileTabPair.getValue();

                // コードタブを更新する。
                AnchorPane anchorPane = (AnchorPane)targetTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                codeArea.replaceText(changedCodeFile.getCode());

                targetTab.isSelected();
                return;
            }
        }

        // 対応するコードタブが存在しない場合は新たに生成する
        targetTab = createCodeTab(changedCodeFile);
        targetTab.isSelected();
    }

    /**
     * <p>コードファイルに対応するコードタブを生成する。</p>
     * @param codeFile
     * @return
     */
    private Tab createCodeTab(CodeFile codeFile) {
        // TabPane > Tab > AnchorPane > CodeArea の構造
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setOnKeyTyped(event -> updateCodeFile());
        codeArea.replaceText(codeFile.getCode());

        AnchorPane codeAnchor = new AnchorPane(codeArea);
        AnchorPane.setBottomAnchor(codeArea, 0.0);
        AnchorPane.setTopAnchor(codeArea, 0.0);
        AnchorPane.setLeftAnchor(codeArea, 0.0);
        AnchorPane.setRightAnchor(codeArea, 0.0);

        Tab codeTab = new Tab();
        codeTab.setContent(codeAnchor);
        codeTab.setText(codeFile.getFileName());
        codeTab.setClosable(false);

        codeTabPane.getTabs().add(codeTab);
        fileTabList.add(new Pair<>(codeFile, codeTab));

        return codeTab;
    }

    /**
     * <p>コードタブに記述されたソースコードを、コードファイルに反映する。</p>
     */
    private void updateCodeFile() {
        // 選択しているコードタブの探索
        Tab selectedTab = new Tab();
        for(Tab codeTab : codeTabPane.getTabs()) {
            if(codeTab.isSelected()) {
                selectedTab = codeTab;
                break;
            }
        }

        // 選択しているコードタブに対応するコードファイルを探索し、更新する
        for(Pair<CodeFile, Tab> fileTabPair : fileTabList) {
            if(fileTabPair.getValue().equals(selectedTab)) {
                CodeFile targetCodeFile = fileTabPair.getKey();
                // コードタブからソースコードを取得する
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                String code = codeArea.getText();
                // コードファイルを更新する
                model.updateCodeFile(targetCodeFile, code);
                return;
            }
        }
    }
}
