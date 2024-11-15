package io.github.morichan.retuss.controller;

import io.github.morichan.retuss.model.CodeFile;
import io.github.morichan.retuss.model.CppFile;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.common.FileChangeListener;
import io.github.morichan.retuss.model.common.ICodeFile;
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
    private List<Pair<CodeFile, Tab>> javaFileTabList = new ArrayList<>();
    private List<Pair<CppFile, Tab>> cppFileTabList = new ArrayList<>();
    private UmlController umlController;

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        // 双方向の参照を設定
        controller.setCodeController(this);
        System.out.println("UmlController set in CodeController"); // デバッグ用
    }

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
            NewFileDialogController controller = fxmlLoader.getController();

            if (umlController == null) {
                System.err.println("Warning: UmlController is not set!");
                return;
            }

            controller.setUmlController(umlController);
            Scene scene = new Scene(parent);
            Stage stage = new Stage();
            stage.setTitle("New File Dialog");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateCodeTab(ICodeFile file) {
        if (file instanceof CodeFile) {
            // 既存のJavaファイル処理
            updateCodeTab((CodeFile) file);
        } else if (file instanceof CppFile) {
            // C++ファイルの処理
            updateCodeTab((CppFile) file);
        }
    }

    // 既存のJava用メソッド
    public void updateCodeTab(CodeFile changedCodeFile) {
        UUID changedFileId = changedCodeFile.getID();

        changedCodeFile.addChangeListener(new FileChangeListener() {
            @Override
            public void onFileChanged(ICodeFile file) {
                // ファイル内容の変更時の処理は必要に応じて実装
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                Platform.runLater(() -> {
                    for (Pair<CodeFile, Tab> fileTabPair : javaFileTabList) {
                        if (fileTabPair.getKey().getID().equals(changedFileId)) {
                            fileTabPair.getValue().setText(newName);
                            break;
                        }
                    }
                });
            }
        });

        for (Pair<CodeFile, Tab> fileTabPair : javaFileTabList) {
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
                updateTabTitle(targetTab, file);

                AnchorPane anchorPane = (AnchorPane) targetTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);
                int caretPosition = codeArea.getCaretPosition();

                if (!codeArea.getText().equals(file.getCode())) {
                    codeArea.replaceText(file.getCode());
                    Platform.runLater(() -> {
                        int newPosition = Math.min(caretPosition, codeArea.getLength());
                        codeArea.moveTo(newPosition);
                        codeArea.requestFollowCaret();
                    });
                }
            } else {
                Tab newTab = createCppCodeTab(file);
                codeTabPane.getTabs().add(newTab);
                codeTabPane.getSelectionModel().select(newTab);
            }
        } catch (Exception e) {
            System.err.println("Failed to update code tab: " + e.getMessage());
        }
    }

    private void updateTabTitle(Tab tab, CppFile file) {
        String fileName = file.getFileName();
        String extension = file.isHeader() ? ".h" : ".cpp";
        String baseName = fileName.replace(extension, "");
        tab.setText(fileName);

        // ツールチップにフルパスやファイルタイプの情報を表示
        tab.setTooltip(new Tooltip("Class: " + baseName + "\nType: " +
                (file.isHeader() ? "Header File" : "Implementation File")));
    }

    private Optional<Tab> findTab(UUID fileId) {
        // C++ファイル用のタブを探す
        Optional<Pair<CppFile, Tab>> cppPair = cppFileTabList.stream()
                .filter(pair -> pair.getKey().getID().equals(fileId))
                .findFirst();
        if (cppPair.isPresent()) {
            return Optional.of(cppPair.get().getValue());
        }

        // Javaファイル用のタブを探す
        return javaFileTabList.stream()
                .filter(pair -> pair.getKey().getID().equals(fileId))
                .map(Pair::getValue)
                .findFirst();
    }

    // 既存のJava用タブ作成メソッド
    private Tab createCodeTab(CodeFile codeFile) {
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

        // 新しい共通インターフェースを使用
        codeFile.addChangeListener(new FileChangeListener() {
            @Override
            public void onFileChanged(ICodeFile file) {
                Platform.runLater(() -> {
                    CodeArea area = (CodeArea) ((AnchorPane) codeTab.getContent()).getChildren().get(0);
                    area.replaceText(file.getCode());
                });
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                Platform.runLater(() -> {
                    System.out.println("ファイル名が変更されました: " + oldName + " -> " + newName);
                    codeTab.setText(newName);
                });
            }
        });

        codeTabPane.getTabs().add(codeTab);
        javaFileTabList.add(new Pair<>(codeFile, codeTab));

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

        for (Pair<CodeFile, Tab> fileTabPair : javaFileTabList) {
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

    // C++用のコード更新メソッド
    private void updateCppCodeFile() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) {
            return;
        }

        for (Pair<CppFile, Tab> fileTabPair : cppFileTabList) {
            if (fileTabPair.getValue().equals(selectedTab)) {
                CppFile targetCodeFile = fileTabPair.getKey();
                System.out.println(" target C++" + fileTabPair.getKey().getCode());
                AnchorPane anchorPane = (AnchorPane) selectedTab.getContent();
                CodeArea codeArea = (CodeArea) anchorPane.getChildren().get(0);

                int caretPosition = codeArea.getCaretPosition();
                String code = codeArea.getText();
                System.out.println("DEBUG: Updating code for " + targetCodeFile.getFileName());

                // UMLコントローラーに直接通知して関係抽出を行う
                if (umlController != null) {
                    umlController.handleCodeUpdate(targetCodeFile);
                }

                cppModel.updateCodeFile(targetCodeFile, code);

                Platform.runLater(() -> {
                    try {
                        codeArea.moveTo(caretPosition);
                        codeArea.requestFollowCaret();
                    } catch (Exception e) {
                        System.err.println("Error updating caret position: " + e.getMessage());
                    }
                });
                return;
            }
        }
    }
}