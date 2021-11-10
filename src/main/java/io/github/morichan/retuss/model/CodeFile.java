package io.github.morichan.retuss.model;

import com.github.javaparser.ast.CompilationUnit;

import java.util.Objects;
import java.util.UUID;

public class CodeFile {
    final private UUID ID = UUID.randomUUID();
    private String fileName = "";
    private CompilationUnit compilationUnit;

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

    public String getCode() {
        if(Objects.isNull(compilationUnit)) {
            return "";
        }
        return compilationUnit.toString();
    }
}
