package io.github.morichan.retuss;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;

public class RetussWindow extends Application {
    private ExecutorService windowExecutor;

    /**
     * <p>
     * RETUSSを開始します
     * </p>
     *
     * @param mainStage 初期ステージ：{@link Application} クラス参照
     */
    @Override
    public void start(Stage mainStage) { // throwsを削除
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
        setupCodeWindow(mainStage, umlController);
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

    private void setupCodeWindow(Stage mainStage, UmlController umlController) throws IOException {
        FXMLLoader codeLoader = new FXMLLoader(getClass().getResource("/retussCode.fxml"));
        Parent codeRoot = codeLoader.load();
        CodeController codeController = codeLoader.getController();
        codeController.setUmlController(umlController);

        double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
        double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();

        Scene codeScene = new Scene(codeRoot);
        // コード用のスタイルシートを追加
        codeScene.getStylesheets().addAll(
                getClass().getResource("/custom-radio.css").toExternalForm(),
                getClass().getResource("/styles.css").toExternalForm() // コード用のスタイル
        );

        Stage codeStage = new Stage();
        codeStage.initOwner(mainStage);
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