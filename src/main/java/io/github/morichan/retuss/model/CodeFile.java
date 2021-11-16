package io.github.morichan.retuss.model;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.translator.Translator;

import java.util.*;

public class CodeFile {
    final private UUID ID = UUID.randomUUID();
    private String fileName = "";
    private CompilationUnit compilationUnit;
    private List<Class> umlClassList = new ArrayList<>();
    private Translator translator = new Translator();

    public UUID getID() {
        return ID;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public void setCompilationUnit(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
    }

    public List<Class> getUmlClassList() {
        return Collections.unmodifiableList(umlClassList);
    }

    public String getCode() {
        if(Objects.isNull(compilationUnit)) {
            return "";
        }
        return compilationUnit.toString();
    }

    void updateCode(String code) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(code);
            this.compilationUnit = compilationUnit;
            // この置き換え方法だと、汎化関係の別クラスへの参照が切れてしまい、一時的に変更が反映されないことが起きる
            this.umlClassList = translator.translateCodeToUml(compilationUnit);
            System.out.println(code);
        } catch (Exception e) {
            System.out.println("Cannot parse.");
            throw e;
        }
    }
}
