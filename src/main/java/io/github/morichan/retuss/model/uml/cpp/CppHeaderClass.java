package io.github.morichan.retuss.model.uml.cpp;

import java.util.*;
import io.github.morichan.fescue.feature.*;
import io.github.morichan.retuss.model.uml.Interaction;
import io.github.morichan.retuss.model.uml.cpp.utils.*;

public class CppHeaderClass {
    // メンバー変数
    private String name = "";
    private Boolean isAbstruct = false;
    private Boolean isInterface = false;
    private Boolean isEnum = false;
    private Boolean isActive; // 宣言されてたかどうか
    private List<String> stereotypes = new ArrayList<>();
    private List<EnumValue> enumValues = new ArrayList<>();
    // private CppHeaderClass superClass;
    private List<CppHeaderClass> superClasses = new ArrayList<>();
    private List<Attribute> attributeList = new ArrayList<>();
    private List<Operation> operationList = new ArrayList<>();
    private List<Interaction> interactionList = new ArrayList<>();
    private final Map<String, Set<Modifier>> memberModifiers = new HashMap<>();
    private final CppRelationshipManager relationshipManager;

    public static class EnumValue {
        private final String name;
        private final String value;
        private final String type;

        public EnumValue(String name) {
            this(name, null, null);
        }

        public EnumValue(String name, String value) {
            this(name, value, null);
        }

        public EnumValue(String name, String value, String type) {
            this.name = name;
            this.value = value;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Optional<String> getValue() {
            return Optional.ofNullable(value);
        }

        public Optional<String> getType() {
            return Optional.ofNullable(type);
        }
    }

    public void addStereotype(String stereotype) {
        if (!stereotypes.contains(stereotype)) {
            stereotypes.add(stereotype);
        }
    }

    public List<String> getStereotypes() {
        return Collections.unmodifiableList(stereotypes);
    }

    public boolean hasStereotype(String stereotype) {
        return stereotypes.contains(stereotype);
    }

    public void addEnumValue(String name) {
        enumValues.add(new EnumValue(name));
    }

    public void addEnumValue(String name, String value) {
        enumValues.add(new EnumValue(name, value));
    }

    public void addEnumValue(String name, String value, String type) {
        enumValues.add(new EnumValue(name, value, type));
    }

    public List<EnumValue> getEnumValues() {
        return Collections.unmodifiableList(enumValues);
    }

    public void setAsEnum() {
        addStereotype("enumeration");
    }

    // enumかどうかの判定
    public boolean isEnum() {
        return hasStereotype("enumeration");
    }

    // メンバー修飾子の管理
    public void addMemberModifier(String memberName, Modifier modifier) {
        memberModifiers.computeIfAbsent(memberName, k -> EnumSet.noneOf(Modifier.class))
                .add(modifier);
    }

    public Set<Modifier> getModifiers(String memberName) {
        return Collections.unmodifiableSet(
                memberModifiers.getOrDefault(memberName, EnumSet.noneOf(Modifier.class)));
    }

    // 関係の管理
    public void addRelationship(RelationshipInfo relationship) {
        relationshipManager.addRelationship(relationship);
    }

    public Set<RelationshipInfo> getRelationships() {
        return relationshipManager.getAllRelationships();
    }

    public CppRelationshipManager getRelationshipManager() {
        return relationshipManager;
    }

    public CppHeaderClass(String name) {
        this.name = name;
        this.isActive = true;
        this.relationshipManager = new CppRelationshipManager(name);
    }

    public CppHeaderClass(String name, Boolean isActive) {
        this.name = name;
        this.isActive = isActive;
        this.relationshipManager = new CppRelationshipManager(name);
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

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public List<CppHeaderClass> getSuperClasses() {
        return Collections.unmodifiableList(superClasses);
    }

    public void setSuperClass(CppHeaderClass superClass) {
        if (!superClasses.contains(superClass)) {
            superClasses.add(superClass);
        }
        if (superClass != null) {
            relationshipManager.addInheritance(superClass.getName());
        }
    }

    public List<Attribute> getAttributeList() {
        return Collections.unmodifiableList(attributeList);
    }

    public List<Operation> getOperationList() {
        return Collections.unmodifiableList(operationList);
    }

    public List<Interaction> getInteractionList() {
        return Collections.unmodifiableList(interactionList);
    }

    public void addAttribute(Attribute attribute) {
        attributeList.add(attribute);
    }

    public void addOperation(Operation operation) {
        operationList.add(operation);
        addInteraction(new Interaction(operation, operation.toString()));
    }

    public void addOperation(Operation operation, Interaction interaction) {
        operationList.add(operation);
        addInteraction(interaction);
    }

    public void addInteraction(Interaction interaction) {
        interactionList.add(interaction);
    }

    public void removeAttribute(Attribute attribute) {
        attributeList.remove(attribute);
    }

    public void removeOperation(Operation operation) {
        operationList.remove(operation);
        for (Interaction interaction : interactionList) {
            if (operation.equals(interaction.getOperation())) {
                removeInteraction(interaction);
                return;
            }
        }
    }

    public void removeInteraction(Interaction interaction) {
        interactionList.remove(interaction);
    }

    public Optional<Operation> findOperation(String operationId) {
        for (Operation operation : operationList) {
            if (operation.toString().equals(operationId)) {
                return Optional.of(operation);
            }
        }
        return Optional.empty();
    }

    public Optional<Interaction> findInteraction(Operation operation) {
        for (Interaction interaction : interactionList) {
            if (interaction.getOperation().equals(operation)) {
                return Optional.of(interaction);
            }
        }
        return Optional.empty();
    }

    public Optional<Interaction> findInteraction(String operationName) {
        // 同じ名前のInteractionが複数あるとダメ
        for (Interaction interaction : interactionList) {
            if (interaction.getOperation().getName().getNameText().equals(operationName)) {
                return Optional.of(interaction);
            }
        }
        return Optional.empty();
    }

    public String toString() {
        return this.name;
    }
}