package io.github.morichan.retuss.translator.model;

import java.util.List;

public class MethodCall {
    private final String caller;
    private final String callee;
    private final String methodName;
    private final List<String> arguments;
    private final int nestingLevel;
    private final String condition; // 制御構造の条件
    private final ControlStructureType structureType;

    public enum ControlStructureType {
        NONE, IF, LOOP
    }

    public MethodCall(String caller, String callee, String methodName,
            List<String> arguments, int nestingLevel) {
        this(caller, callee, methodName, arguments, nestingLevel, null, ControlStructureType.NONE);
    }

    public MethodCall(String caller, String callee, String methodName,
            List<String> arguments, int nestingLevel,
            String condition, ControlStructureType structureType) {
        this.caller = caller;
        this.callee = callee;
        this.methodName = methodName;
        this.arguments = arguments;
        this.nestingLevel = nestingLevel;
        this.condition = condition;
        this.structureType = structureType;
    }

    // Getters
    public String getCaller() {
        return caller;
    }

    public String getCallee() {
        return callee;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public int getNestingLevel() {
        return nestingLevel;
    }

    public String getCondition() {
        return condition;
    }

    public ControlStructureType getStructureType() {
        return structureType;
    }
}