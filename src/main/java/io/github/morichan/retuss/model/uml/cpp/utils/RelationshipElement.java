package io.github.morichan.retuss.model.uml.cpp.utils;

import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;

public class RelationshipElement {
    private final String name; // 要素の名前
    private final ElementType elemType; // 要素の種類
    private final String multiplicity; // 多重度
    private final Visibility visibility; // 可視性
    private final String type; // 型情報
    private final String returnType; // 戻り値の型（メソッドの場合）
    private final String defaultValue; // デフォルト値
    private final boolean isPureVirtual; // 純粋仮想メソッドかどうか
    private final Set<Modifier> modifiers; // 修飾子のセット

    public RelationshipElement(
            String name,
            ElementType elemType,
            String multiplicity,
            Visibility visibility,
            String type,
            String returnType,
            String defaultValue,
            boolean isPureVirtual,
            Set<Modifier> modifiers) {
        this.name = name;
        this.elemType = elemType;
        this.multiplicity = multiplicity;
        this.visibility = visibility;
        this.type = type;
        this.returnType = returnType;
        this.defaultValue = defaultValue;
        this.isPureVirtual = isPureVirtual;
        this.modifiers = (modifiers != null && !modifiers.isEmpty())
                ? EnumSet.copyOf(modifiers)
                : EnumSet.noneOf(Modifier.class);
    }

    // Getters
    public String getName() {
        return name;
    }

    public ElementType getElemType() {
        return elemType;
    }

    public String getMultiplicity() {
        return multiplicity;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public String getType() {
        return type;
    }

    public String getReturnType() {
        return returnType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isPureVirtual() {
        return isPureVirtual;
    }

    public Set<Modifier> getModifiers() {
        return Collections.unmodifiableSet(modifiers);
    }

    // C++コード生成
    public String generateCppCode() {
        StringBuilder sb = new StringBuilder();

        // 修飾子の追加
        for (Modifier mod : modifiers) {
            sb.append(mod.getCppText(isPureVirtual)).append(" ");
        }

        switch (elemType) {
            case ATTRIBUTE:
                generateAttributeCode(sb);
                break;
            case OPERATION:
                generateOperationCode(sb);
                break;
            case PARAMETER:
                generateParameterCode(sb);
                break;
            default:
                break;
        }

        return sb.toString();
    }

    private void generateAttributeCode(StringBuilder sb) {
        sb.append(type).append(" ").append(name);
        if (defaultValue != null && !defaultValue.isEmpty()) {
            sb.append(" = ").append(defaultValue);
        }
        sb.append(";");
    }

    private void generateOperationCode(StringBuilder sb) {
        sb.append(returnType).append(" ")
                .append(name)
                .append("(");
        // パラメータは別途処理が必要
        sb.append(")");
        if (isPureVirtual) {
            sb.append(" = 0");
        }
        sb.append(";");
    }

    private void generateParameterCode(StringBuilder sb) {
        sb.append(type).append(" ").append(name);
        if (defaultValue != null && !defaultValue.isEmpty()) {
            sb.append(" = ").append(defaultValue);
        }
    }

    // PlantUML生成
    public String generatePlantUmlCode() {
        StringBuilder sb = new StringBuilder();

        // 可視性の追加
        sb.append(getVisibilitySymbol(visibility)).append(" ");

        // 修飾子の追加
        for (Modifier mod : modifiers) {
            sb.append(mod.getPlantUmlText(isPureVirtual)).append(" ");
        }

        // 名前と型の追加
        switch (elemType) {
            case ATTRIBUTE:
                sb.append(name).append(" : ").append(type);
                break;
            case OPERATION:
                sb.append(name).append("()");
                if (returnType != null && !returnType.equals("void")) {
                    sb.append(" : ").append(returnType);
                }
                break;
            case PARAMETER:
                sb.append(name).append(" : ").append(type);
                break;
            default:
                break;
        }

        return sb.toString();
    }

    private String getVisibilitySymbol(Visibility visibility) {
        switch (visibility) {
            case Public:
                return "+";
            case Protected:
                return "#";
            case Private:
                return "-";
            default:
                return "~";
        }
    }
}