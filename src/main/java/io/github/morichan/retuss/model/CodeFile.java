package io.github.morichan.retuss.model;

import io.github.morichan.retuss.model.common.ICodeFile;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.translator.Translator;

import java.util.*;

public class CodeFile implements ICodeFile {
    private final UUID ID = UUID.randomUUID();
    private String fileName = "";
    private CompilationUnit compilationUnit;
    private List<Class> umlClassList = new ArrayList<>();
    private Translator translator = new Translator();

    public CodeFile(String fileName) {
        final String extension = ".java";
        this.fileName = fileName;
        this.compilationUnit = new CompilationUnit();
        String className = fileName.substring(0, this.fileName.length() - extension.length());
        this.compilationUnit.addClass(className);
        this.umlClassList = translator.translateCodeToUml(this.compilationUnit);
    }

    @Override
    public UUID getID() {
        return ID;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    @Override
    public List<Class> getUmlClassList() {
        return Collections.unmodifiableList(umlClassList);
    }

    @Override
    public String getCode() {
        if (Objects.isNull(compilationUnit)) {
            return "";
        }
        return compilationUnit.toString();
    }

    @Override
    public void updateCode(String code) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(code);
            Optional<ClassOrInterfaceDeclaration> mainClass = compilationUnit
                    .findFirst(ClassOrInterfaceDeclaration.class);
            if (mainClass.isPresent()) {
                String expectedFileName = mainClass.get().getNameAsString() + ".java";
                if (!expectedFileName.equals(this.fileName)) {
                    String oldFileName = this.fileName; // 古いファイル名を保存
                    this.fileName = expectedFileName;
                    notifyFileNameChanged(oldFileName, this.fileName); // ファイル名変更を通知
                }
            }

            this.compilationUnit = compilationUnit;
            this.umlClassList = translator.translateCodeToUml(compilationUnit);
            System.out.println(code);
        } catch (Exception e) {
            System.out.println("Cannot parse.");
            System.out.println(e.getMessage());
            throw e;
        }
    }

    public interface FileNameChangeListener {
        void onFileNameChanged(String oldName, String newName);
    }

    private List<FileNameChangeListener> listeners = new ArrayList<>();

    public void addFileNameChangeListener(FileNameChangeListener listener) {
        listeners.add(listener);
    }

    private void notifyFileNameChanged(String oldName, String newName) {
        for (FileNameChangeListener listener : listeners) {
            listener.onFileNameChanged(oldName, newName);
        }
    }

    @Override
    public void addUmlClass(Class umlClass) {
        this.umlClassList.add(umlClass); // クリアせずに追加
        this.compilationUnit = translator.translateUmlToCode(this.umlClassList); // 更新されたクラスリストからコンパイルユニットを生成
    }

    @Override
    public void removeClass(Class umlClass) {
        this.umlClassList.remove(umlClass);
        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = this.compilationUnit
                .getClassByName(umlClass.getName());
        if (classOrInterfaceDeclarationOptional.isPresent()) {
            this.compilationUnit.remove(classOrInterfaceDeclarationOptional.get());
        }
    }
}
