package io.github.morichan.retuss.translator.cpp.header.analyzers.base;

import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.translator.cpp.header.util.*;

import java.util.*;

public class AnalyzerContext {
    private CppHeaderClass currentHeaderClass;
    private CppImplClass currentImplClass;
    private String currentVisibility = "private";
    private final Map<String, Set<String>> relationships = new HashMap<>();
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;
    private final boolean isHeaderFile;

    public AnalyzerContext(boolean isHeaderFile) {
        this.isHeaderFile = isHeaderFile;
        this.typeMapper = new CppTypeMapper();
        this.visibilityMapper = new CppVisibilityMapper();
    }

    public CppHeaderClass getCurrentHeaderClass() {
        return currentHeaderClass;
    }

    public CppHeaderClass getCurrentimplClass() {
        return currentHeaderClass;
    }

    public void setCurrentHeaderClass(CppHeaderClass currentHeaderClass) {
        this.currentHeaderClass = currentHeaderClass;
    }

    public void setCurrentImplClass(CppImplClass currentImplClass) {
        this.currentImplClass = currentImplClass;
    }

    public String getCurrentVisibility() {
        return currentVisibility;
    }

    public void setCurrentVisibility(String visibility) {
        this.currentVisibility = visibility;
    }

    public CppTypeMapper getTypeMapper() {
        return typeMapper;
    }

    public CppVisibilityMapper getVisibilityMapper() {
        return visibilityMapper;
    }

    public boolean isHeaderFile() {
        return isHeaderFile;
    }

    public void addRelationship(String source, String target) {
        relationships.computeIfAbsent(source, k -> new HashSet<>()).add(target);
    }
}