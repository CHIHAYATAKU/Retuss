package io.github.morichan.retuss.translator.cpp.header.util;

import java.util.ArrayList;
import java.util.List;

public class CollectionTypeInfo {
    String baseType; // コレクションの型名(vector, map等)
    List<String> parameterTypes = new ArrayList<>(); // テンプレートパラメータの型
    String multiplicity; // 多重度

    // multiplicity決定ロジック
    public void determineMultiplicity() {
        switch (baseType) {
            case "array":
                // array<T,N>のNを取得
                multiplicity = extractArraySize();
                break;
            case "unique_ptr":
                multiplicity = "0..1";
                break;
            case "shared_ptr":
            case "weak_ptr":
                multiplicity = "0..*";
                break;
            default:
                multiplicity = "*"; // vector, list, set, map等
        }
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public String getMultiplicity() {
        return multiplicity;
    }

    public String getBaseType() {
        return baseType;
    }

    public void setBaseType(String baseType) {
        this.baseType = baseType;
    }

    private String extractArraySize() {
        if (parameterTypes.size() >= 2) {
            String sizeParam = parameterTypes.get(1);
            if (sizeParam.matches("\\d+")) {
                return sizeParam;
            }
        }
        return "*"; // サイズが取得できない場合はデフォルト
    }
}