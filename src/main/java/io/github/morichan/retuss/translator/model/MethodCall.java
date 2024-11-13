package io.github.morichan.retuss.translator.model;

import java.util.ArrayList;
import java.util.List;

public class MethodCall {
    private String caller;
    private String callee;
    private String methodName;
    private List<String> arguments;
    private int nestingLevel;
    private String condition;
    private ControlStructureType structureType;

    public enum ControlStructureType {
        NONE, IF, LOOP
    }

    public MethodCall() {
        this.arguments = new ArrayList<>();
        this.nestingLevel = 0;
        this.structureType = ControlStructureType.NONE;
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

    // setters
    public void setCaller(String caller) {
        this.caller = caller;
    }

    public void setCallee(String callee) {
        this.callee = callee;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setNestingLevel(int nestingLevel) {
        this.nestingLevel = nestingLevel;
    }

    public void addArgument(String argument) {
        if (this.arguments == null) {
            this.arguments = new ArrayList<>();
        }
        this.arguments.add(argument);
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