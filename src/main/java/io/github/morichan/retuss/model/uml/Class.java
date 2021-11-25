package io.github.morichan.retuss.model.uml;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;

import java.util.*;

public class Class {
    private String name = "";
    private Boolean isAbstruct = false;
    private Boolean isActive;   // 宣言されてたかどうか
    private Class superClass;
    private List<Attribute> attributeList = new ArrayList<>();
    private List<Operation> operationList = new ArrayList<>();
    private List<Interaction> interactionList = new ArrayList();

    public Class(String name) {
        this.name = name;
        this.isActive = true;
    }

    public Class(String name, Boolean isActive) {
        this.name = name;
        this.isActive = isActive;
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

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public Optional<Class> getSuperClass() {
        return Optional.ofNullable(superClass);
    }

    public void setSuperClass(Class superClass) {
        this.superClass = superClass;
    }

    public List<Attribute> getAttributeList() {
        return Collections.unmodifiableList(attributeList);
    }

    public List<Operation> getOperationList() {
        return Collections.unmodifiableList(operationList);
    }

    public List<Interaction> getInteractionList() { return Collections.unmodifiableList(interactionList); }

    public void addAttribute(Attribute attribute) {
        attributeList.add(attribute);
    }

    public void addOperation(Operation operation) {
        operationList.add(operation);
        addInteraction(new Interaction(operation, operation.toString()));
    }

    public void addInteraction(Interaction interaction) {
        interactionList.add(interaction);
    }

    public void removeAttribute(Attribute attribute) {
        attributeList.remove(attribute);
    }

    public void removeOperation(Operation operation) {
        operationList.remove(operation);
        for(Interaction interaction : interactionList) {
            if(operation.equals(interaction.getOperation())) {
                removeInteraction(interaction);
                return;
            }
        }
    }

    public void removeInteraction(Interaction interaction) { interactionList.remove(interaction); }
}
