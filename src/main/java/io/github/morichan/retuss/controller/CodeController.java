package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.CppFile;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.CppModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CodeController {
    @FXML
    private TabPane codeTabPane;
    private JavaModel javaModel = JavaModel.getInstance();
    private CppModel cppModel = CppModel.getInstance();
    private List<Pair<CodeFile, Tab>> fileTabList = new ArrayList<>();
    private List<Pair<CppFile, Tab>> cppFileTabList = new ArrayList<>();

    @FXML
    private void initialize() {
        javaModel.setCodeController(this);
        cppModel.setCodeController(this);
    }

    @FXML
    private void showNewFileDialog() {
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

    @FXML
    private void showNewCppFileDialog() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/newCppFileDialog.fxml"));
            Parent parent = fxmlLoader.load();
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New C++ File");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // 既存のJava用メソッド
    public void updateCodeTab(CodeFile changedCodeFile) {
        UUID changedFileId = changedCodeFile.getID();

        changedCodeFile.addFileNameChangeListener((oldName, newName) -> {
            for (Pair<CodeFile, Tab> fileTabPair : fileTabList) {
                if (fileTabPair.getKey().getID().equals(changedFileId)) {
                    fileTabPair.getValue().setText(newName);
                    break;
                }
            }
        });

        for (Pair<CodeFile, Tab> fileTabPair : fileTabList) {
            if (changedFileId.equals(fileTabPair.getKey().getID())) {
                Tab targetTab = fileTabPair.getValue();
                AnchorPane anchorPane = (AnchorPane) targetTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                codeArea.replaceText(changedCodeFile.getCode());
                codeTabPane.getSelectionModel().select(targetTab);
                return;
            }
        }

        Tab targetTab = createCodeTab(changedCodeFile);
        codeTabPane.getSelectionModel().select(targetTab);
    }

    // C++用の新しいメソッド
    public void updateCodeTab(CppFile file) {
        try {
            Optional<Tab> existingTab = findTab(file.getID());

            if (existingTab.isPresent()) {
                Tab targetTab = existingTab.get();
                // タブ名の更新
                updateTabTitle(targetTab, file);

                // コードの更新
                AnchorPane anchorPane = (AnchorPane) targetTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                int caretPosition = codeArea.getCaretPosition();

                if (!codeArea.getText().equals(file.getCode())) {
                    codeArea.replaceText(file.getCode());
                    // カーソル位置の復元
                    Platform.runLater(() -> {
                        int newPosition = Math.min(caretPosition, codeArea.getLength());
                        codeArea.moveTo(newPosition);
                        codeArea.requestFollowCaret();
                    });
                }
            } else {
                Tab newTab = createCppCodeTab(file);
                codeTabPane.getTabs().add(newTab);
                // 新規タブの場合のみ選択
                codeTabPane.getSelectionModel().select(newTab);
            }
        } catch (Exception e) {
            System.err.println("Failed to update code tab: " + e.getMessage());
        }
    }

    private void updateTabTitle(Tab tab, CppFile file) {
        String fileName = file.getFileName();
        String extension = file.isHeader() ? ".hpp" : ".cpp";
        String baseName = fileName.replace(extension, "");
        tab.setText(fileName);

        // ツールチップにフルパスやファイルタイプの情報を表示
        tab.setTooltip(new Tooltip("Class: " + baseName + "\nType: " +
                (file.isHeader() ? "Header File" : "Implementation File")));
    }

    private Optional<Tab> findTab(UUID fileId) {
        return cppFileTabList.stream()
                .filter(pair -> pair.getKey().getID().equals(fileId))
                .map(Pair::getValue)
                .findFirst();
    }

    // 既存のJava用タブ作成メソッド
    private Tab createCodeTab(CodeFile codeFile) {
        // 既存の実装をそのまま維持
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

        codeFile.addFileNameChangeListener((oldName, newName) -> {
            System.out.println("ファイル名が変更されました: " + oldName + " -> " + newName);
            codeTab.setText(newName);
        });

        codeTabPane.getTabs().add(codeTab);
        fileTabList.add(new Pair<>(codeFile, codeTab));

        return codeTab;
    }

    // C++用の新しいタブ作成メソッド
    private Tab createCppCodeTab(CppFile cppFile) {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        // キー入力時の処理
        codeArea.setOnKeyTyped(event -> {
            int caretPosition = codeArea.getCaretPosition();
            updateCppCodeFile();
            Platform.runLater(() -> {
                int newPosition = Math.min(caretPosition, codeArea.getLength());
                codeArea.moveTo(newPosition);
                codeArea.requestFollowCaret();
            });
        });

        codeArea.replaceText(cppFile.getCode());

        AnchorPane codeAnchor = new AnchorPane(codeArea);
        AnchorPane.setBottomAnchor(codeArea, 0.0);
        AnchorPane.setTopAnchor(codeArea, 0.0);
        AnchorPane.setLeftAnchor(codeArea, 0.0);
        AnchorPane.setRightAnchor(codeArea, 0.0);

        Tab codeTab = new Tab();
        codeTab.setContent(codeAnchor);
        updateTabTitle(codeTab, cppFile);
        codeTab.setClosable(false);

        cppFileTabList.add(new Pair<>(cppFile, codeTab));

        return codeTab;
    }

    // 既存のJava用コード更新メソッド
    private void updateCodeFile() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null)
            return;

        for (Pair<CodeFile, Tab> fileTabPair : fileTabList) {
            if (fileTabPair.getValue().equals(selectedTab)) {
                CodeFile targetCodeFile = fileTabPair.getKey();
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                String code = codeArea.getText();
                javaModel.updateCodeFile(targetCodeFile, code);
                return;
            }
        }
    }

    // C++用の新しいコード更新メソッド
    private void updateCppCodeFile() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null)
            return;

        for (Pair<CppFile, Tab> fileTabPair : cppFileTabList) {
            if (fileTabPair.getValue().equals(selectedTab)) {
                CppFile targetCodeFile = fileTabPair.getKey();
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);

                int caretPosition = codeArea.getCaretPosition();
                String code = codeArea.getText();
                cppModel.updateCode(targetCodeFile, code);

                Platform.runLater(() -> {
                    codeArea.moveTo(caretPosition);
                    codeArea.requestFollowCaret();
                });
                return;
            }
        }
    }
}