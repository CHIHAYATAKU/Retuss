package io.github.morichan.retuss.model.uml;

import java.util.*;
import java.util.stream.Collectors;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;

public class CppClass extends Class {
    public enum Modifier {
        STATIC,
        CONST,
        VOLATILE,
        MUTABLE,
        VIRTUAL,
        ABSTRACT,
        OVERRIDE,
        FINAL
    }

    // メンバー名と修飾子のマッピング
    private final Map<String, Set<Modifier>> memberModifiers = new HashMap<>();
    private Map<String, Type> memberTypes = new HashMap<>();
    private Map<String, List<Parameter>> methodParameters = new HashMap<>();
    private List<Relationship> relationships = new ArrayList<>();
    private final Set<String> dependencies = new HashSet<>();
    private final Set<String> compositions = new HashSet<>();

    public CppClass(String name) {
        super(name);
    }

    public CppClass(String name, Boolean isActive) {
        super(name, isActive);
    }

    // 修飾子の追加
    public void addMemberModifiers(String memberName, Set<Modifier> modifiers) {
        memberModifiers.put(memberName, EnumSet.copyOf(modifiers));
    }

    public void addMemberModifier(String memberName, Modifier modifier) {
        memberModifiers.computeIfAbsent(memberName, k -> EnumSet.noneOf(Modifier.class))
                .add(modifier);
    }

    public Set<Modifier> getModifiers(String memberName) {
        return memberModifiers.getOrDefault(memberName, EnumSet.noneOf(Modifier.class));
    }

    public boolean hasModifier(String memberName, Modifier modifier) {
        return getModifiers(memberName).contains(modifier);
    }

    public Type getMemberType(String memberName) {
        return memberTypes.get(memberName);
    }

    public List<Parameter> getMethodParameters(String methodName) {
        return methodParameters.getOrDefault(methodName, new ArrayList<>());
    }

    // UMLの表記に変換するユーティリティメソッド
    public String getUmlModifierNotation(String memberName) {
        Set<Modifier> modifiers = getModifiers(memberName);
        List<String> notations = new ArrayList<>();

        if (modifiers.contains(Modifier.STATIC))
            notations.add("static");
        if (modifiers.contains(Modifier.CONST))
            notations.add("readOnly");
        if (modifiers.contains(Modifier.VIRTUAL) ||
                modifiers.contains(Modifier.ABSTRACT))
            notations.add("abstract");

        if (notations.isEmpty())
            return "";
        return "{" + String.join(",", notations) + "}";
    }

    // メソッドのシグネチャを取得
    public String getMethodSignature(String methodName) {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName).append("(");

        List<Parameter> params = getMethodParameters(methodName);
        if (params != null) {
            List<String> paramStrings = params.stream()
                    .map(p -> p.getType() + " " + p.getName())
                    .collect(Collectors.toList());
            sb.append(String.join(", ", paramStrings));
        }

        sb.append(")");

        Type returnType = getMemberType(methodName);
        if (returnType != null && !returnType.toString().equals("void")) {
            sb.append(" : ").append(returnType);
        }

        return sb.toString();
    }

    public void addRelationship(Relationship relationship) {
        relationships.add(relationship);
    }

    public List<Relationship> getRelationships() {
        return Collections.unmodifiableList(relationships);
    }

    public void addDependency(String className) {
        if (!className.equals(getName())) {
            dependencies.add(className);
        }
    }

    public void addComposition(String className) {
        if (!className.equals(getName())) {
            compositions.add(className);
        }
    }

    public Set<String> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    public Set<String> getCompositions() {
        return Collections.unmodifiableSet(compositions);
    }

    @Override
    public void addOperation(Operation operation) {
        super.addOperation(operation);
        memberTypes.put(operation.getName().getNameText(), operation.getReturnType());
        try {
            methodParameters.put(operation.getName().getNameText(),
                    new ArrayList<>(operation.getParameters()));
        } catch (IllegalStateException e) {
            methodParameters.put(operation.getName().getNameText(), new ArrayList<>());
        }
    }

    @Override
    public void addAttribute(Attribute attribute) {
        super.addAttribute(attribute);
        memberTypes.put(attribute.getName().getNameText(), attribute.getType());
    }

    private void analyzeAttributeRelationships(Attribute attribute) {
        String typeName = attribute.getType().toString();
        boolean isPointer = typeName.contains("*");
        boolean isReference = typeName.contains("&");

        // ポインタや参照の場合は集約関係
        if (isPointer || isReference) {
            addRelationship(new Relationship(
                    this.getName(),
                    cleanTypeName(typeName),
                    Relationship.RelationType.AGGREGATION,
                    "",
                    attribute.getName().toString(),
                    "",
                    "1",
                    true));
        }
        // 値型の場合はコンポジション関係
        else if (isUserDefinedType(typeName)) {
            addRelationship(new Relationship(
                    this.getName(),
                    cleanTypeName(typeName),
                    Relationship.RelationType.COMPOSITION,
                    "",
                    attribute.getName().toString(),
                    "",
                    "1",
                    true));
        }
    }

    private String cleanTypeName(String typeName) {
        return typeName.replaceAll("[*&]", "").trim();
    }

    private boolean isUserDefinedType(String typeName) {
        // 基本型のリスト
        Set<String> basicTypes = Set.of("void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed");
        String cleanType = cleanTypeName(typeName);
        return !basicTypes.contains(cleanType) && !cleanType.startsWith("std::");
    }
}