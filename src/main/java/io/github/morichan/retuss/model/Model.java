package io.github.morichan.retuss.model;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.translator.Translator;

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
    private Translator translator = new Translator();
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

    public List<Class> getUmlClassList() {
        List<Class> umlClassList = new ArrayList<>();
        for(CodeFile codeFile : codeFileList) {
            umlClassList.addAll(codeFile.getUmlClassList());
        }

        return Collections.unmodifiableList(umlClassList);
    }

    /**
     * 新規コードファイルを追加する
     * @param fileName
     */

    public void addNewCodeFile(String fileName) {
        CodeFile newCodeFile = new CodeFile(fileName);
        codeFileList.add(newCodeFile);
        codeController.updateCodeTab(newCodeFile);
        umlController.updateDiagram();
    }

    /**
     * 新規のUMLクラスを追加する
     * @param umlClass
     */
    public void addNewUmlClass(Class umlClass) {
        CodeFile codeFile = new CodeFile(String.format("%s.java", umlClass.getName()));
        codeFile.addUmlClass(umlClass);
        codeFileList.add(codeFile);
        umlController.updateDiagram();
        codeController.updateCodeTab(codeFile);
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

    public Optional<CodeFile> findCodeFile(String fileName) {
        for(CodeFile codeFile : codeFileList) {
            if(codeFile.getFileName().equals(fileName)) {
                return Optional.of(codeFile);
            }
        }
        return Optional.empty();
    }

    /**
     * <p>ソースコードを構文解析し、構文木をCodeFileにセットする。</p>
     * @param changedCodeFile 変更対象ファイル
     * @param code 変更後のソースコード
     */
    public void updateCodeFile(CodeFile changedCodeFile, String code) {
        try {
            changedCodeFile.updateCode(code);
            umlController.updateDiagram();
        } catch (Exception e) {
            return;
        }
    }

    public void addAttribute(String className, Attribute attribute) {
        Optional<Class> targetClass = findClass(className);
        Optional<CodeFile> targetCodeFile = findCodeFile(className + ".java");
        if(targetClass.isEmpty() || targetCodeFile.isEmpty()){
            return;
        }

        try {
            FieldDeclaration fieldDeclaration = translator.translateAttribute(attribute);
            NodeList<BodyDeclaration<?>> members = targetCodeFile.get().getCompilationUnit().getClassByName(className).get().getMembers();
            if(members.size() == 0) {
                // メンバーがない場合は、最初の位置に追加
                members.addFirst(fieldDeclaration);
            } else {
                for(int i=0; i<members.size(); i++) {
                    if(members.get(i).isMethodDeclaration()) {
                        // メソッド宣言の前に新しいフィールド宣言を追加する
                        members.addBefore(fieldDeclaration, members.get(i));
                        break;
                    } else if(i == members.size() - 1) {
                        // メソッド宣言がない場合は、最後の位置に追加
                        members.addLast(fieldDeclaration);
                        break;
                    }
                }
            }
            targetClass.get().addAttribute(attribute);
            umlController.updateDiagram();
            codeController.updateCodeTab(targetCodeFile.get());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void addOperation(String className, Operation operation) {
        Optional<Class> targetClass = findClass(className);
        Optional<CodeFile> targetCodeFile = findCodeFile(className + ".java");
        if(targetClass.isEmpty() || targetCodeFile.isEmpty()){
            return;
        }

        try {
            targetCodeFile.get().getCompilationUnit().getClassByName(className).get().addMember(translator.translateOperation(operation));
            targetClass.get().addOperation(operation);
            umlController.updateDiagram();
            codeController.updateCodeTab(targetCodeFile.get());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }



}
