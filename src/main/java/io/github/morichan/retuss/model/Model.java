package io.github.morichan.retuss.model;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.translator.Translator;

import java.util.*;

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

    /**
     * UMLのクラスを探索する
     * @param className
     * @return
     */
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

    /**
     * コードファイルを探索する
     * @param fileName
     * @return
     */
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

    /**
     * 属性を追加し、コードにフィールドとして反映する
     * @param className
     * @param attribute
     */
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

    /**
     * 操作を追加し、コードにもメソッド宣言として反映する
     * @param className
     * @param operation
     */
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

    /**
     * コンポジション関係を追加し、コードに属性として反映する
     * ただし、追加した属性は初期化しない
     * @param haveClassName
     * @param compositedClassName
     */
    public void addComposition(String haveClassName, String compositedClassName) {
        Optional<CodeFile> haveCodefileOptional = findCodeFile(haveClassName + ".java");
        Optional<CodeFile> compositedCodefileOptional = findCodeFile(compositedClassName + ".java");
        Optional<Class> haveClassOptional = findClass(haveClassName);
        Optional<Class> compositedClassOptional = findClass(compositedClassName);

        if(haveCodefileOptional.isEmpty() || compositedCodefileOptional.isEmpty() || haveClassOptional.isEmpty() || compositedClassOptional.isEmpty()) {
            return;
        }

        try {
            Name name = new Name(compositedClassName.toLowerCase());
            Attribute attribute = new Attribute(name);
            attribute.setVisibility(Visibility.Private);
            attribute.setType(new Type(compositedClassName));
            addAttribute(haveClassName, attribute);
            // addAttribute()でWindowの更新はされる
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * 汎化関係を追加し、コードにextendsとして反映する
     * 既に汎化関係を持っている場合は、上書きする
     * @param generalizedClassName
     * @param superClassName
     */
    public void addGeneralization(String generalizedClassName, String superClassName) {
        Optional<CodeFile> generalizedCodefileOptional = findCodeFile(generalizedClassName + ".java");
        Optional<CodeFile> superCodefileOptional = findCodeFile(superClassName + ".java");
        Optional<Class> generalizedClassOptional = findClass(generalizedClassName);
        Optional<Class> superClassOptional = findClass(superClassName);

        if(generalizedCodefileOptional.isEmpty() || superCodefileOptional.isEmpty() || generalizedClassOptional.isEmpty() || superClassOptional.isEmpty()) {
            return;
        }

        try {
            CodeFile generalizedCodeFile = generalizedCodefileOptional.get();
            NodeList<ClassOrInterfaceType> extendedTypes = new NodeList<>();
            ClassOrInterfaceType extendedType = new ClassOrInterfaceType();
            extendedType.setName(superClassName);
            extendedTypes.add(extendedType);
            generalizedCodeFile.getCompilationUnit().getClassByName(generalizedClassName).get().setExtendedTypes(extendedTypes);
            // UML更新
            generalizedClassOptional.get().setSuperClass(superClassOptional.get());
            umlController.updateDiagram();
            codeController.updateCodeTab(generalizedCodefileOptional.get());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     *
     * @param className
     */
    public void delete(String className) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if(classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }

        codeFileOptional.get().removeClass(classOptional.get());
        umlController.updateDiagram();
        codeController.updateCodeTab(codeFileOptional.get());
    }

    /**
     *
     * @param className
     */
    public void delete(String className, Attribute attribute) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if(classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }

        classOptional.get().removeAttribute(attribute);
        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = codeFileOptional.get().getCompilationUnit().getClassByName(className);
        Optional<FieldDeclaration> fieldOptional = classOrInterfaceDeclarationOptional.get().getFieldByName(attribute.getName().getNameText());
        classOrInterfaceDeclarationOptional.get().remove(fieldOptional.get());

        umlController.updateDiagram();
        codeController.updateCodeTab(codeFileOptional.get());
    }

    public void delete(String className, Operation operation) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if(classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }

        // メソッド名と引数の型が一致するメソッド宣言を探索
        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = codeFileOptional.get().getCompilationUnit().getClassByName(className);
        List<MethodDeclaration> methodList = classOrInterfaceDeclarationOptional.get().getMethodsByName(operation.getName().getNameText());
        if(methodList.size() <= 0) {
            return;
        }

        int operationParameterSize = 0;
        try {
            operationParameterSize = operation.getParameters().size();
        } catch (Exception e) {

        }

        MethodDeclaration targetMethod = null;
        for(MethodDeclaration methodDeclaration : methodList) {
            if(methodDeclaration.getParameters().size() != operationParameterSize) {
                // 引数の数が異なる場合
                continue;
            }

            if(operationParameterSize == 0) {
                // 引数の数が同じ、かつ、引数の数が0の場合
                targetMethod = methodDeclaration;
                break;
            }

            // 引数の数が同じ、かつ、引数の数が複数ある場合
            int cntSameParameters = 0;
            for (int i = 0; i < methodDeclaration.getParameters().size(); i++) {
                if (!methodDeclaration.getParameters().get(i).getTypeAsString().equals(operation.getParameters().get(i).getType().toString())) {
                    break;
                }
                cntSameParameters++;
            }
            if (cntSameParameters == methodDeclaration.getParameters().size()) {
                targetMethod = methodDeclaration;
                break;
            }
        }
        // 削除対象メソッドが見つからない場合
        if(Objects.isNull(targetMethod)) {
            return;
        }

        classOrInterfaceDeclarationOptional.get().remove(targetMethod);
        classOptional.get().removeOperation(operation);
        umlController.updateDiagram();
        codeController.updateCodeTab(codeFileOptional.get());
    }
}
