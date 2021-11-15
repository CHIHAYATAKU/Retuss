package io.github.morichan.retuss.model.uml;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;

import java.util.ArrayList;
import java.util.List;

public class Class {
    private String name = "";
    private Boolean isAbstruct = false;
    // true=宣言されたクラス、false=宣言されていない仮のクラス
    private Boolean isActive;
    private Class superClass;
    private List<Attribute> attributeList = new ArrayList<>();
    private List<Operation> operationList = new ArrayList<>();

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

    public Class getSuperClass() {
        return superClass;
    }

    public void setSuperClass(Class superClass) {
        this.superClass = superClass;
    }

    public void addAttribute(Attribute attribute) {
        attributeList.add(attribute);
    }

    public void addOperation(Operation operation) {
        operationList.add(operation);
    }
}
