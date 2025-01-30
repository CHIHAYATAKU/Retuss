package io.github.morichan.retuss.model.uml.cpp.utils;

public enum RelationType {
    GENERALIZATION("extends", "--|>"),
    COMPOSITION("unique_ptr", "*--"),
    AGGREGATION("shared_ptr", "o--"),
    ASSOCIATION("pointer", "--"),
    DEPENDENCY_USE("use", "..>"),
    DEPENDENCY_PARAMETER("parameter", "..>"),
    DEPENDENCY_CALL("call", "..>"),
    DEPENDENCY_LOCAL("local", "..>"),
    REALIZATION("implements", "..|>");

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

    public String getStereotype() {
        return this.cppText;
    }

    public boolean isDependency() {
        return name().startsWith("DEPENDENCY_");
    }
}