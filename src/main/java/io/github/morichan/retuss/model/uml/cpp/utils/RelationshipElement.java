package io.github.morichan.retuss.model.uml.cpp.utils;

import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;

public class RelationshipElement {
    private final String name; // 要素の名前
    private String multiplicity; // 多重度
    private final Visibility visibility; // 可視性

    public RelationshipElement(
            String name,
            String multiplicity,
            Visibility visibility) {
        this.name = name;
        this.multiplicity = multiplicity;
        this.visibility = visibility;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getMultiplicity() {
        return multiplicity;
    }

    public void setMultiplicity(String multiplicity) {
        this.multiplicity = multiplicity;
    }

    public Visibility getVisibility() {
        return visibility;
    }
}