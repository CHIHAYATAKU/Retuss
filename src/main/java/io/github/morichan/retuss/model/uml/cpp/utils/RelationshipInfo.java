package io.github.morichan.retuss.model.uml.cpp.utils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import io.github.morichan.fescue.feature.visibility.Visibility;

public class RelationshipInfo {
    private final String targetClass;
    private RelationType type;
    private RelationshipElement element;

    public RelationshipInfo(String targetClass, RelationType type) {
        this.targetClass = targetClass;
        this.type = type;
    }

    // 完全版
    public void setElement(
            String name,
            ElementType elemType,
            String multiplicity,
            Visibility visibility) {
        this.element = new RelationshipElement(name, elemType, multiplicity, visibility);
    }

    // ゲッターメソッド
    public String getTargetClass() {
        return targetClass;
    }

    public void setType(RelationType type) {
        this.type = type;
    }

    public RelationType getType() {
        return type;
    }

    public RelationshipElement getElement() {
        return element;
    }
}