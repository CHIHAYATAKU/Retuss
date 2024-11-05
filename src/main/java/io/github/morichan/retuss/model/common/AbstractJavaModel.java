package io.github.morichan.retuss.model.common;

import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.CombinedFragment;
import io.github.morichan.retuss.model.uml.Message;
import io.github.morichan.retuss.model.uml.InteractionFragment;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;

import java.util.*;

public abstract class AbstractJavaModel {
    protected UmlController umlController;
    protected CodeController codeController;

    public void setUmlController(UmlController umlController) {
        this.umlController = umlController;
    }

    public void setCodeController(CodeController codeController) {
        this.codeController = codeController;
    }

    public Optional<Class> findClass(String className) {
        for (ICodeFile codeFile : getCodeFileList()) {
            for (Class umlClass : codeFile.getUmlClassList()) {
                if (umlClass.getName().equals(className)) {
                    return Optional.of(umlClass);
                }
            }
        }
        return Optional.empty();
    }

    public List<Class> getUmlClassList() {
        List<Class> umlClassList = new ArrayList<>();
        for (ICodeFile codeFile : getCodeFileList()) {
            umlClassList.addAll(codeFile.getUmlClassList());
        }
        return Collections.unmodifiableList(umlClassList);
    }

    // 言語固有の実装が必要なメソッド
    protected abstract List<? extends ICodeFile> getCodeFileList();

    public abstract void addNewCodeFile(String fileName);

    public abstract void addNewUmlClass(Class umlClass);

    public abstract void addCombinedFragment(String className, Operation operation, CombinedFragment combinedFragment);

    public abstract void updateCodeFile(ICodeFile changedCodeFile, String code);

    public abstract void addAttribute(String className, Attribute attribute);

    public abstract void addOperation(String className, Operation operation);

    public abstract void addComposition(String haveClassName, String compositedClassName);

    public abstract void addGeneralization(String generalizedClassName, String superClassName);

    public abstract void delete(String className);

    public abstract void delete(String className, Attribute attribute);

    public abstract void delete(String className, Operation operation);

    public abstract void delete(String className, Operation operation, InteractionFragment interactionFragment);

    public abstract void deleteSuperClass(String className);

    public abstract void addMessage(String className, Operation operation, Message message);
}