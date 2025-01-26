package io.github.morichan.retuss.model.uml.cpp.utils;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum Modifier {
    // 属性用の修飾子
    STATIC("static", "{static}", ElementType.ATTRIBUTE, ElementType.OPERATION),
    READONLY("const", "{readOnly}", ElementType.ATTRIBUTE, ElementType.OPERATION),
    VOLATILE("volatile", "{volatile}", ElementType.ATTRIBUTE),
    MUTABLE("mutable", "{mutable}", ElementType.ATTRIBUTE),
    QUERY("const;", "{query}", ElementType.OPERATION),

    // メソッド用の修飾子
    VIRTUAL("virtual", "{virtual}", ElementType.OPERATION),
    OVERRIDE("override", "<<override>>", ElementType.OPERATION),
    FINAL("final", "{final}", ElementType.OPERATION,
            ElementType.CLASS),
    PURE_VIRTUAL("= 0", "{abstract}", ElementType.OPERATION);

    private final String cppText;
    private final String plantUmlText;
    private final Set<ElementType> applicableTypes;

    private Modifier(String cppText, String plantUmlText, ElementType... types) {
        this.cppText = cppText;
        this.plantUmlText = plantUmlText;
        this.applicableTypes = EnumSet.copyOf(Arrays.asList(types));
    }

    public String getCppText(boolean isPureVirtual) {
        if (this == VIRTUAL && isPureVirtual) {
            return cppText + " = 0";
        }
        return cppText;
    }

    public String getPlantUmlText(boolean isPureVirtual) {
        if (this == VIRTUAL && isPureVirtual) {
            return "{abstract}";
        }
        return plantUmlText;
    }

    public boolean isApplicableTo(ElementType type) {
        return applicableTypes.contains(type);
    }
}