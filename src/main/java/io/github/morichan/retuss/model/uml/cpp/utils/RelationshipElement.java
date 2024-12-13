package io.github.morichan.retuss.model.uml.cpp.utils;

import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;

public class RelationshipElement {
    private final String name; // 要素の名前
    private final ElementType elemType; // 要素の種類
    private final String multiplicity; // 多重度
    private final Visibility visibility; // 可視性

    public RelationshipElement(
            String name,
            ElementType elemType,
            String multiplicity,
            Visibility visibility) {
        this.name = name;
        this.elemType = elemType;
        this.multiplicity = multiplicity;
        this.visibility = visibility;
    }

    // Getters
    public String getName() {
        return name;
    }

    public ElementType getElemType() {
        return elemType;
    }

    public String getMultiplicity() {
        return multiplicity;
    }

    public Visibility getVisibility() {
        return visibility;
    }
}