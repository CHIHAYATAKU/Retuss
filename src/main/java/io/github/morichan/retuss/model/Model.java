package io.github.morichan.retuss.model;

import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.uml.Class;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * <p> RETUSS内のデータ構造を保持するクラス </p>
 * <p> Singletonデザインパターンにより、インスタンスが1つであることを保証する。 </p>
 */
public class Model {
    private static Model model = new Model();
    private UmlController umlController;
    private CodeController codeController;
    // ファイルのデータ構造
    private List<CodeFile> codeFileList = new ArrayList<>();


    private Model() { }

    public static Model getInstance() {
        return model;
    }

    public void setUmlController(UmlController umlController) {
        this.umlController = umlController;
    }

    public void setCodeController(CodeController codeController) {
        this.codeController = codeController;
    }

    public List<CodeFile> getCodeFileList() {
        return Collections.unmodifiableList(codeFileList);
    }

    public void addCodeFile() {
        CodeFile newCodeFile = new CodeFile();
        codeFileList.add(newCodeFile);
        codeController.updateCodeTab(newCodeFile);
    }

    public Optional<Class> findClass(String className) {
        for(CodeFile codeFile : codeFileList) {
            for(Class umlClass : codeFile.getUmlClassList()) {
                if(umlClass.getName().equals(className)) {
                    return Optional.of(umlClass);
                }
            }
        }
        return Optional.empty();
    }
}
