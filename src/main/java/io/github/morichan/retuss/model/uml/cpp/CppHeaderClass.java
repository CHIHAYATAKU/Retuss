package io.github.morichan.retuss.model.uml.cpp;

import java.util.*;
import io.github.morichan.fescue.feature.*;
import io.github.morichan.retuss.model.uml.Interaction;
import io.github.morichan.retuss.model.uml.cpp.utils.*;

public class CppHeaderClass {
    // メンバー変数
    // private String namespace = "";
    private String name = "";
    private Boolean isAbstruct = false;
    private Boolean isInterface = false;
    private Boolean isEnum = false;
    private List<EnumValue> enumValues = new ArrayList<>();
    private List<Attribute> attributeList = new ArrayList<>();
    private List<Operation> operationList = new ArrayList<>();
    private final Map<Object, Set<Modifier>> memberModifiers = new HashMap<>();
    private final RelationshipManager relationshipManager;

    public static class EnumValue {
        private final String name;
        private final String value;

        public EnumValue(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Optional<String> getValue() {
            return Optional.ofNullable(value);
        }
    }

    // public void setNamespace(String namespace) {
    // this.namespace = namespace;
    // }

    // public String getNamespace() {
    // return namespace;
    // }

    public void addEnumValue(String name, String value) {
        enumValues.add(new EnumValue(name, value));
    }

    public List<EnumValue> getEnumValues() {
        return Collections.unmodifiableList(enumValues);
    }

    // メンバー修飾子の管理
    public void addMemberModifier(Object member, Modifier modifier) {
        memberModifiers.computeIfAbsent(member, k -> EnumSet.noneOf(Modifier.class))
                .add(modifier);
    }

    public Set<Modifier> getModifiers(Object member) {
        return Collections.unmodifiableSet(
                memberModifiers.getOrDefault(member, EnumSet.noneOf(Modifier.class)));
    }

    // 関係の管理
    public void addRelationship(RelationshipInfo relationship) {
        relationshipManager.addRelationship(relationship);
    }

    public Set<RelationshipInfo> getRelationships() {
        return relationshipManager.getAllRelationships();
    }

    public RelationshipManager getRelationshipManager() {
        return relationshipManager;
    }

    public CppHeaderClass(String name) {
        this.name = name;
        this.relationshipManager = new RelationshipManager(name);
    }

    public CppHeaderClass(String name, Boolean isActive) {
        this.name = name;
        this.relationshipManager = new RelationshipManager(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getAbstruct() {
        return isAbstruct;
    }

    public void setAbstruct(Boolean abstruct) {
        isAbstruct = abstruct;
    }

    public Boolean getInterface() {
        return isInterface;
    }

    public void setInterface(Boolean abstruct) {
        isInterface = abstruct;
    }

    public void setEnum(Boolean isEnum) {
        this.isEnum = isEnum;
    }

    public Boolean getEnum() {
        return this.isEnum;
    }

    public List<Attribute> getAttributeList() {
        return Collections.unmodifiableList(attributeList);
    }

    public List<Operation> getOperationList() {
        return Collections.unmodifiableList(operationList);
    }

    public void addAttribute(Attribute attribute) {
        attributeList.add(attribute);
    }

    public void addOperation(Operation operation) {
        operationList.add(operation);
    }

    public void removeAttribute(Attribute attribute) {
        attributeList.remove(attribute);
    }

    public Optional<Operation> findOperation(String operationId) {
        for (Operation operation : operationList) {
            if (operation.toString().equals(operationId)) {
                return Optional.of(operation);
            }
        }
        return Optional.empty();
    }

    public String toString() {
        return this.name;
    }
}