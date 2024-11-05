package io.github.morichan.retuss.model.common;

import io.github.morichan.retuss.model.uml.Class;
import java.util.*;

public interface ICodeFile {
    UUID getID();

    String getFileName();

    String getCode();

    List<Class> getUmlClassList();

    void updateCode(String code);

    void addUmlClass(Class umlClass);

    void removeClass(Class umlClass);
}