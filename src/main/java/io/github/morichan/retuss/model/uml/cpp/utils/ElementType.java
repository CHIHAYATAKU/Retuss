package io.github.morichan.retuss.model.uml.cpp.utils;

public enum ElementType {
    ATTRIBUTE("member", ""), // メンバ変数
    OPERATION("method", "()"), // メソッド
    PARAMETER("param", ""), // メソッド引数
    CLASS("class", "class"), // クラス自体
    LOCAL_VARIABLE("local", ""), // ローカル変数
    TEMPORARY("temp", ""), // 一時オブジェクト
    METHOD_CALL("call", "()"); // メソッド呼び出し

    private final String cppText;
    private final String plantUmlText;

    private ElementType(String cppText, String plantUmlText) {
        this.cppText = cppText;
        this.plantUmlText = plantUmlText;
    }

    public String getCppText() {
        return cppText;
    }

    public String getPlantUmlText() {
        return plantUmlText;
    }
}