package io.github.morichan.retuss;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.CppModel;

public class RetussWindow extends Application {
    private ExecutorService windowExecutor;
    private Stage mainStage; // UMLウィンドウ
    private Stage codeStage; // コードウィンドウ

    /**
     * <p>
     * RETUSSを開始します
     * </p>
     *
     * @param mainStage 初期ステージ：{@link Application} クラス参照
     */
    @Override
    public void start(Stage mainStage) { // throwsを削除
        this.mainStage = mainStage;
        windowExecutor = Executors.newSingleThreadExecutor();

        try {
            setupWindows(mainStage);
        } catch (IOException e) {
            showError("Error initializing windows", e);
        }
    }

    private void setupWindows(Stage mainStage) throws IOException {
        // UMLウィンドウの設定
        FXMLLoader umlLoader = new FXMLLoader(getClass().getResource("/retussMain.fxml"));
        Parent umlRoot = umlLoader.load();
        UmlController umlController = umlLoader.getController();

        Scene mainScene = new Scene(umlRoot);
        mainScene.getStylesheets().add(getClass().getResource("/custom-radio.css").toExternalForm());

        setupMainStage(mainStage, mainScene);

        // コードウィンドウの設定
        FXMLLoader codeLoader = new FXMLLoader(getClass().getResource("/retussCode.fxml"));
        Parent codeRoot = codeLoader.load();
        CodeController codeController = codeLoader.getController();

        // コントローラー間の相互参照設定
        codeController.setUmlController(umlController);
        umlController.setCodeController(codeController);

        // モデルにリスナーを登録
        CppModel cppModel = CppModel.getInstance();
        cppModel.addChangeListener(codeController);
        cppModel.addChangeListener(umlController);

        codeStage = setupCodeWindow(mainStage, umlController, codeController, codeRoot);

        setupWindowClosing();
    }

    private void setupWindowClosing() {
        // 両ウィンドウ共通の終了処理
        EventHandler<WindowEvent> closeHandler = event -> {
            if (confirmClosing()) {
                cleanupAndExit();
            } else {
                event.consume();
            }
        };

        mainStage.setOnCloseRequest(closeHandler);
        codeStage.setOnCloseRequest(closeHandler);
    }

    private void cleanupAndExit() {
        try {
            // ExecutorServiceのシャットダウン
            if (windowExecutor != null) {
                windowExecutor.shutdown();
                try {
                    // より長めのタイムアウトを設定
                    if (!windowExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                        windowExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    windowExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // JavaFXアプリケーションの終了
            Platform.exit();

            // プロセスの完全終了
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
            // 強制終了
            System.exit(1);
        }
    }

    private boolean confirmClosing() {
        // 未保存の変更があるかチェック
        boolean hasUnsavedChanges = false; // ここに未保存チェックのロジックを実装

        if (hasUnsavedChanges) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Exit");
            alert.setHeaderText("There are unsaved changes.");
            alert.setContentText("Do you want to save changes before exiting?");

            ButtonType buttonTypeSave = new ButtonType("Save");
            ButtonType buttonTypeDontSave = new ButtonType("Don't Save");
            ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(buttonTypeSave, buttonTypeDontSave, buttonTypeCancel);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == buttonTypeSave) {
                    // 保存処理を実装
                    return true;
                } else if (result.get() == buttonTypeDontSave) {
                    return true;
                } else {
                    return false;
                }
            }
            return false;
        }
        return true;
    }

    private void setupMainStage(Stage mainStage, Scene mainScene) {
        double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
        double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();

        mainStage.setTitle("UML Window");
        mainStage.setScene(mainScene);
        mainStage.setMaxWidth(screenWidth);
        mainStage.setMaxHeight(screenHeight);
        mainStage.setWidth(screenWidth / 2);
        mainStage.setHeight(screenHeight);
        mainStage.setX(0.0);
        mainStage.setY(0.0);
        mainStage.show();
    }

    private Stage setupCodeWindow(Stage mainStage, UmlController umlController,
            CodeController codeController, Parent codeRoot) {
        double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
        double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();

        Scene codeScene = new Scene(codeRoot);
        codeScene.getStylesheets().addAll(
                getClass().getResource("/custom-radio.css").toExternalForm(),
                getClass().getResource("/styles.css").toExternalForm());

        Stage codeStage = new Stage();
        codeStage.initOwner(mainStage);
        codeStage.setAlwaysOnTop(false);
        codeStage.setTitle("Source Code Window");
        codeStage.setScene(codeScene);
        codeStage.setMaxWidth(screenWidth);
        codeStage.setMaxHeight(screenHeight);
        codeStage.setWidth(screenWidth / 2);
        codeStage.setHeight(screenHeight);
        codeStage.setX(screenWidth / 2);
        codeStage.setY(0.0);

        // ステージ表示前の初期化
        codeStage.setOnShowing(event -> {
            if (codeController != null) {
                Platform.runLater(() -> {
                    try {
                        codeController.postInitialize(); // 追加した初期化メソッドを呼び出し
                    } catch (Exception e) {
                        showError("Failed to initialize code window", e);
                    }
                });
            }
        });

        codeStage.show();
        return codeStage;
    }

    private void showError(String title, Exception e) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        });
    }

    @Override
    public void stop() {
        if (windowExecutor != null) {
            windowExecutor.shutdown();
            try {
                if (!windowExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    windowExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                windowExecutor.shutdownNow();
            }
        }
    }

    /**
     * <p>
     * メインメソッド
     * </p>
     *
     * <p>
     * JavaFXを用いる場合はデフォルトで存在するメソッドです。
     * ここから全てが始まります。
     * </p>
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        launch(args);
    }
}