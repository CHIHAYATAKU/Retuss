package io.github.morichan.retuss.translator.cpp.analyzers.base;

import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.translator.cpp.util.CppTypeMapper;
import io.github.morichan.retuss.translator.cpp.util.CppVisibilityMapper;
import java.util.*;

public class AnalyzerContext {
    private CppClass currentClass;
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

    public CppClass getCurrentClass() {
        return currentClass;
    }

    public void setCurrentClass(CppClass currentClass) {
        this.currentClass = currentClass;
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

    public Map<String, Set<String>> getRelationships() {
        return Collections.unmodifiableMap(relationships);
    }
}