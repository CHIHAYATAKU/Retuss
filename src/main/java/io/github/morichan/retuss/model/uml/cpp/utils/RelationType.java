package io.github.morichan.retuss.model.uml.cpp.utils;

public enum RelationType {
    INHERITANCE("extends", "<|--"),
    COMPOSITION("unique_ptr", "*--"),
    AGGREGATION("shared_ptr", "o--"),
    ASSOCIATION("pointer", "--"),
    DEPENDENCY("uses", "<.."),
    REALIZATION("implements", "<|..");

    private final String cppText;
    private final String plantUmlText;

    private RelationType(String cppText, String plantUmlText) {
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