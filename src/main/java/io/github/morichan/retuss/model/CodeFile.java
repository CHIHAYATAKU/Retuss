package io.github.morichan.retuss.model;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.translator.Translator;

import java.util.*;

public class CodeFile {
    final private UUID ID = UUID.randomUUID();
    private String fileName = "";
    private CompilationUnit compilationUnit;
    private List<Class> umlClassList = new ArrayList<>();
    private Translator translator = new Translator();

    public CodeFile(String fileName) {
        final String extension = ".java";
        this.fileName = fileName;
        this.compilationUnit = new CompilationUnit();
        this.compilationUnit.addClass(fileName.substring(0, this.fileName.length() - extension.length()));
        this.umlClassList = translator.translateCodeToUml(this.compilationUnit);
    }

    public UUID getID() {
        return ID;
    }

    public String getFileName() {
        return fileName;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
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
            System.out.println(e.getMessage());
            throw e;
        }
    }

    void addUmlClass(Class umlClass) {
        // 1ファイル1クラスが前提
        this.umlClassList.clear();
        this.umlClassList.add(umlClass);
        this.compilationUnit = translator.translateUmlToCode(umlClassList);
    }

    void removeClass(Class umlClass) {
        this.umlClassList.remove(umlClass);
        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = this.compilationUnit.getClassByName(umlClass.getName());
        this.compilationUnit.remove(classOrInterfaceDeclarationOptional.get());
    }
}
