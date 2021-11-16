package io.github.morichan.retuss;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;

public class RetussWindow extends Application {
    /**
     * <p> RETUSSを開始します </p>
     *
     * @param mainStage 初期ステージ：{@link Application} クラス参照
     * @throws IOException FXMLファイルの入力エラーの場合
     */
    @Override
    public void start(Stage mainStage) throws IOException {
        String mainFxmlFileName = "retussMain.fxml";
        String codeFxmlFileName = "retussCode.fxml";
        String resourcesPath = "/";
        double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
        double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();

        // UMLウィンドウの表示
        FXMLLoader umlLoader = new FXMLLoader(getClass().getResource(resourcesPath + mainFxmlFileName));
        Parent root = umlLoader.load();
        mainStage.setTitle("UML Window");
        mainStage.setScene(new Scene(root));
        mainStage.setMaxWidth(screenWidth);
        mainStage.setMaxHeight(screenHeight);
        mainStage.setWidth(screenWidth / 2);
        mainStage.setHeight(screenHeight);
        mainStage.setX(0.0);
        mainStage.setY(0.0);
        mainStage.show();

        // ソースコードウィンドウの表示
        Stage codeStage = new Stage();
        codeStage.initOwner(mainStage);
        codeStage.setTitle("Source Code Window");
        FXMLLoader codeLoader = new FXMLLoader(getClass().getResource(resourcesPath + codeFxmlFileName));
        codeStage.setScene(new Scene(codeLoader.load()));
        codeStage.setMaxWidth(screenWidth);
        codeStage.setMaxHeight(screenHeight);
        codeStage.setWidth(screenWidth / 2);
        codeStage.setHeight(screenHeight);
        codeStage.setX(screenWidth / 2);
        codeStage.setY(0.0);
        codeStage.show();
    }

    /**
     * <p> メインメソッド </p>
     *
     * <p>
     *     JavaFXを用いる場合はデフォルトで存在するメソッドです。
     *     ここから全てが始まります。
     * </p>
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        launch(args);
    }
}


