package io.github.morichan.retuss.translator.cpp.header.analyzers.base;

import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.translator.cpp.header.util.*;

import java.util.*;

public class AnalyzerContext {
    private CppHeaderClass outerClass;
    private CppHeaderClass currentHeaderClass;
    // private String currentNamespace = "";
    private String currentVisibility = "private";
    private final boolean isHeaderFile;

    public AnalyzerContext(boolean isHeaderFile) {
        this.isHeaderFile = isHeaderFile;
    }

    // public void setCurrentNamespace(String namespace) {
    // this.currentNamespace = namespace;
    // if (currentHeaderClass != null) {
    // currentHeaderClass.setNamespace(namespace);
    // }
    // }

    public void setOuterClass(CppHeaderClass cls) {
        outerClass = cls;
    }

    public CppHeaderClass getOuterClass() {
        return outerClass;
    }

    // public String getCurrentNamespace() {
    // return currentNamespace;
    // }

    public CppHeaderClass getCurrentHeaderClass() {
        return currentHeaderClass;
    }

    public CppHeaderClass getCurrentimplClass() {
        return currentHeaderClass;
    }

    public void setCurrentHeaderClass(CppHeaderClass currentHeaderClass) {
        this.currentHeaderClass = currentHeaderClass;
    }

    public String getCurrentVisibility() {
        return currentVisibility;
    }

    public void setCurrentVisibility(String visibility) {
        this.currentVisibility = visibility;
    }

    public boolean isHeaderFile() {
        return isHeaderFile;
    }
}