package io.github.morichan.retuss.model;

import io.github.morichan.retuss.model.common.FileChangeListener;
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
    private final List<FileChangeListener> listeners = new ArrayList<>();

    public CodeFile(String fileName) {
        this.fileName = fileName;
        String className = fileName.replace(".java", "");
        this.compilationUnit = new CompilationUnit();
        this.compilationUnit.addClass(className);
        updateUmlClasses(this.compilationUnit);
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
            System.out.println("DEBUG: Updating Java code:\n" + code);

            // コードのパース
            CompilationUnit newUnit = StaticJavaParser.parse(code);
            Optional<ClassOrInterfaceDeclaration> mainClass = newUnit.findFirst(ClassOrInterfaceDeclaration.class);

            mainClass.ifPresent(cls -> {
                String expectedFileName = cls.getNameAsString() + ".java";
                if (!expectedFileName.equals(this.fileName)) {
                    String oldFileName = this.fileName;
                    this.fileName = expectedFileName;
                    notifyFileNameChanged(oldFileName, expectedFileName);
                }
            });

            this.compilationUnit = newUnit;
            updateUmlClasses(newUnit);
            System.out.println("DEBUG: Updated UML classes: " + umlClassList.size());
            for (Class cls : umlClassList) {
                System.out.println("DEBUG: Class: " + cls.getName());
                System.out.println("DEBUG: Attributes: " + cls.getAttributeList().size());
                System.out.println("DEBUG: Operations: " + cls.getOperationList().size());
            }
        } catch (Exception e) {
            System.err.println("Error updating Java code: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void updateUmlClasses(CompilationUnit unit) {
        List<Class> newClasses = translator.translateCodeToUml(unit.toString());
        umlClassList.clear();
        umlClassList.addAll(newClasses);
    }

    public interface FileNameChangeListener {
        void onFileNameChanged(String oldName, String newName);
    }

    public void addChangeListener(FileChangeListener listener) {
        listeners.add(listener);
    }

    private void notifyFileChanged() {
        for (FileChangeListener listener : listeners) {
            listener.onFileChanged(this);
        }
    }

    private void notifyFileNameChanged(String oldName, String newName) {
        for (FileChangeListener listener : listeners) {
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
