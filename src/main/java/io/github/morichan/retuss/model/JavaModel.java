package io.github.morichan.retuss.model;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.common.AbstractJavaModel;
import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.*;
import io.github.morichan.retuss.translator.JavaTranslator;

import java.util.*;

/**
 * <p>
 * Java言語用のモデルクラス
 * </p>
 * <p>
 * Singletonパターンを使用
 * </p>
 */
public class JavaModel extends AbstractJavaModel {
    private static JavaModel model = new JavaModel();
    private List<CodeFile> codeFileList = new ArrayList<>();
    private JavaTranslator translator = new JavaTranslator();

    private JavaModel() {
    }

    public static JavaModel getInstance() {
        return model;
    }

    @Override
    protected List<? extends ICodeFile> getCodeFileList() {
        return Collections.unmodifiableList(codeFileList);
    }

    @Override
    public void addNewCodeFile(String fileName) {
        CodeFile newCodeFile = new CodeFile(fileName);
        codeFileList.add(newCodeFile);
        codeController.updateCodeTab(newCodeFile);
        umlController.updateDiagram(newCodeFile);
    }

    @Override
    public void addNewUmlClass(Class umlClass) {
        CodeFile codeFile = new CodeFile(String.format("%s.java", umlClass.getName()));
        codeFileList.add(codeFile);
        codeFile.addUmlClass(umlClass);
        umlController.updateDiagram(codeFile);
        codeController.updateCodeTab(codeFile);
    }

    @Override
    public void updateCodeFile(ICodeFile changedCodeFile, String code) {
        try {
            ((CodeFile) changedCodeFile).updateCode(code);
            umlController.updateDiagram((CodeFile) changedCodeFile);
        } catch (Exception e) {
            return;
        }
    }

    @Override
    public void addAttribute(String className, Attribute attribute) {
        Optional<Class> targetClass = findClass(className);
        Optional<CodeFile> targetCodeFile = findCodeFile(className + ".java");
        if (targetClass.isEmpty() || targetCodeFile.isEmpty()) {
            return;
        }

        try {
            FieldDeclaration fieldDeclaration = translator.translateAttribute(attribute);
            NodeList<BodyDeclaration<?>> members = targetCodeFile.get().getCompilationUnit().getClassByName(className)
                    .get().getMembers();
            if (members.size() == 0) {
                members.addFirst(fieldDeclaration);
            } else {
                for (int i = 0; i < members.size(); i++) {
                    if (members.get(i).isMethodDeclaration()) {
                        members.addBefore(fieldDeclaration, members.get(i));
                        break;
                    } else if (i == members.size() - 1) {
                        members.addLast(fieldDeclaration);
                        break;
                    }
                }
            }
            targetClass.get().addAttribute(attribute);
            umlController.updateDiagram(targetCodeFile.get());
            codeController.updateCodeTab(targetCodeFile.get());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void addOperation(String className, Operation operation) {
        Optional<Class> targetClass = findClass(className);
        Optional<CodeFile> targetCodeFile = findCodeFile(className + ".java");
        if (targetClass.isEmpty() || targetCodeFile.isEmpty()) {
            return;
        }

        try {
            targetCodeFile.get().getCompilationUnit().getClassByName(className).get()
                    .addMember(translator.translateOperation(operation));
            targetClass.get().addOperation(operation);
            umlController.updateDiagram(targetCodeFile.get());
            codeController.updateCodeTab(targetCodeFile.get());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void addComposition(String haveClassName, String compositedClassName) {
        Optional<CodeFile> haveCodefileOptional = findCodeFile(haveClassName + ".java");
        Optional<CodeFile> compositedCodefileOptional = findCodeFile(compositedClassName + ".java");
        Optional<Class> haveClassOptional = findClass(haveClassName);
        Optional<Class> compositedClassOptional = findClass(compositedClassName);

        if (haveCodefileOptional.isEmpty() || compositedCodefileOptional.isEmpty() || haveClassOptional.isEmpty()
                || compositedClassOptional.isEmpty()) {
            return;
        }

        try {
            Name name = new Name(compositedClassName.toLowerCase());
            Attribute attribute = new Attribute(name);
            attribute.setVisibility(Visibility.Private);
            attribute.setType(new Type(compositedClassName));
            addAttribute(haveClassName, attribute);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void addGeneralization(String generalizedClassName, String superClassName) {
        Optional<CodeFile> generalizedCodefileOptional = findCodeFile(generalizedClassName + ".java");
        Optional<CodeFile> superCodefileOptional = findCodeFile(superClassName + ".java");
        Optional<Class> generalizedClassOptional = findClass(generalizedClassName);
        Optional<Class> superClassOptional = findClass(superClassName);

        if (generalizedCodefileOptional.isEmpty() || superCodefileOptional.isEmpty()
                || generalizedClassOptional.isEmpty() || superClassOptional.isEmpty()) {
            return;
        }

        try {
            CodeFile generalizedCodeFile = generalizedCodefileOptional.get();
            NodeList<ClassOrInterfaceType> extendedTypes = new NodeList<>();
            ClassOrInterfaceType extendedType = new ClassOrInterfaceType();
            extendedType.setName(superClassName);
            extendedTypes.add(extendedType);
            generalizedCodeFile.getCompilationUnit().getClassByName(generalizedClassName).get()
                    .setExtendedTypes(extendedTypes);
            generalizedClassOptional.get().setSuperClass(superClassOptional.get());
            umlController.updateDiagram(generalizedCodefileOptional.get());
            codeController.updateCodeTab(generalizedCodefileOptional.get());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(String className) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if (classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }

        codeFileOptional.get().removeClass(classOptional.get());
        umlController.updateDiagram(codeFileOptional.get());
        codeController.updateCodeTab(codeFileOptional.get());
    }

    @Override
    public void delete(String className, Attribute attribute) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if (classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }

        classOptional.get().removeAttribute(attribute);
        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = codeFileOptional.get()
                .getCompilationUnit().getClassByName(className);
        Optional<FieldDeclaration> fieldOptional = classOrInterfaceDeclarationOptional.get()
                .getFieldByName(attribute.getName().getNameText());
        classOrInterfaceDeclarationOptional.get().remove(fieldOptional.get());

        umlController.updateDiagram(codeFileOptional.get());
        codeController.updateCodeTab(codeFileOptional.get());
    }

    @Override
    public void delete(String className, Operation operation) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if (classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }

        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = codeFileOptional.get()
                .getCompilationUnit().getClassByName(className);
        List<MethodDeclaration> methodList = classOrInterfaceDeclarationOptional.get()
                .getMethodsByName(operation.getName().getNameText());
        if (methodList.size() <= 0) {
            return;
        }

        Optional<MethodDeclaration> targetMethodOptional = findMethodDeclaration(methodList, operation);
        if (targetMethodOptional.isEmpty()) {
            return;
        }

        classOrInterfaceDeclarationOptional.get().remove(targetMethodOptional.get());
        classOptional.get().removeOperation(operation);
        umlController.updateDiagram(codeFileOptional.get());
        codeController.updateCodeTab(codeFileOptional.get());
    }

    @Override
    public void delete(String className, Operation operation, InteractionFragment interactionFragment) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if (classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }
        Class targetClass = classOptional.get();
        CodeFile targetFile = codeFileOptional.get();

        Interaction targetInteraction = null;
        for (Interaction interaction : classOptional.get().getInteractionList()) {
            if (interaction.getOperation().equals(operation)) {
                targetInteraction = interaction;
                break;
            }
        }
        if (Objects.isNull(targetInteraction)) {
            return;
        }

        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = codeFileOptional.get()
                .getCompilationUnit().getClassByName(className);
        List<MethodDeclaration> methodList = classOrInterfaceDeclarationOptional.get()
                .getMethodsByName(operation.getName().getNameText());
        if (methodList.size() <= 0) {
            return;
        }

        Optional<MethodDeclaration> targetMethodOptional = findMethodDeclaration(methodList, operation);
        if (targetMethodOptional.isEmpty() || targetMethodOptional.get().getBody().isEmpty()) {
            return;
        }

        Optional<Statement> removeTargetOptional = Optional.empty();
        if (interactionFragment instanceof OccurenceSpecification) {
            removeTargetOptional = ((OccurenceSpecification) interactionFragment).getStatement();
        } else if (interactionFragment instanceof CombinedFragment) {
            removeTargetOptional = ((CombinedFragment) interactionFragment).getStatement();
        } else {
            return;
        }

        if (removeTargetOptional.isEmpty()) {
            return;
        }

        if (!removeTargetOptional.get().remove()) {
            return;
        }
        targetInteraction.deleteInteractionFragment(interactionFragment);
        umlController.updateDiagram(codeFileOptional.get());
        codeController.updateCodeTab(codeFileOptional.get());
    }

    @Override
    public void deleteSuperClass(String className) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if (classOptional.isEmpty() || codeFileOptional.isEmpty() || classOptional.get().getSuperClass().isEmpty()) {
            return;
        }

        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = codeFileOptional.get()
                .getCompilationUnit().getClassByName(className);
        classOrInterfaceDeclarationOptional.get().getExtendedTypes().remove(0);
        classOptional.get().setSuperClass(null);

        umlController.updateDiagram(codeFileOptional.get());
        codeController.updateCodeTab(codeFileOptional.get());
    }

    @Override
    public void addMessage(String className, Operation operation, Message message) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if (classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }
        Class targetClass = classOptional.get();
        CodeFile targetFile = codeFileOptional.get();

        Interaction targetInteraction = null;
        for (Interaction interaction : classOptional.get().getInteractionList()) {
            if (interaction.getOperation().equals(operation)) {
                targetInteraction = interaction;
                break;
            }
        }
        if (Objects.isNull(targetInteraction)) {
            return;
        }

        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = codeFileOptional.get()
                .getCompilationUnit().getClassByName(className);
        List<MethodDeclaration> methodDeclarationList = classOrInterfaceDeclarationOptional.get()
                .getMethodsByName(operation.getName().getNameText());
        Optional<MethodDeclaration> methodDeclarationOptional = findMethodDeclaration(methodDeclarationList, operation);
        if (methodDeclarationOptional.isEmpty()) {
            return;
        }
        MethodDeclaration targetMethodDeclaration = methodDeclarationOptional.get();

        BlockStmt body = targetMethodDeclaration.getBody().orElse(new BlockStmt());

        OccurenceSpecification occurenceSpecification = new OccurenceSpecification(
                new Lifeline("", targetClass.getName()));
        occurenceSpecification.setMessage(message);

        ExpressionStmt expressionStmt = translator.occurenceSpeccificationToExpressionStmt(occurenceSpecification);

        targetInteraction.getInteractionFragmentList().add(occurenceSpecification);
        body.addStatement(expressionStmt);

        umlController.updateDiagram(codeFileOptional.get());
        codeController.updateCodeTab(codeFileOptional.get());
    }

    public Optional<CodeFile> findCodeFile(String fileName) {
        for (CodeFile codeFile : codeFileList) {
            if (codeFile.getFileName().equals(fileName)) {
                return Optional.of(codeFile);
            }
        }
        return Optional.empty();
    }

    @Override
    public void addCombinedFragment(String className, Operation operation, CombinedFragment combinedFragment) {
        Optional<Class> classOptional = findClass(className);
        Optional<CodeFile> codeFileOptional = findCodeFile(className + ".java");
        if (classOptional.isEmpty() || codeFileOptional.isEmpty()) {
            return;
        }
        Class targetClass = classOptional.get();
        CodeFile targetFile = codeFileOptional.get();

        // Interactionの探索
        Interaction targetInteraction = null;
        for (Interaction interaction : classOptional.get().getInteractionList()) {
            if (interaction.getOperation().equals(operation)) {
                targetInteraction = interaction;
                break;
            }
        }
        if (Objects.isNull(targetInteraction)) {
            return;
        }

        // operationに対応するmethodDeclarationを取得
        Optional<ClassOrInterfaceDeclaration> classOrInterfaceDeclarationOptional = codeFileOptional.get()
                .getCompilationUnit().getClassByName(className);
        List<MethodDeclaration> methodDeclarationList = classOrInterfaceDeclarationOptional.get()
                .getMethodsByName(operation.getName().getNameText());
        Optional<MethodDeclaration> methodDeclarationOptional = findMethodDeclaration(methodDeclarationList, operation);
        if (methodDeclarationOptional.isEmpty()) {
            return;
        }
        MethodDeclaration targetMethodDeclaration = methodDeclarationOptional.get();

        // methodDeclarationのBodyを取得
        BlockStmt body = targetMethodDeclaration.getBody().orElse(new BlockStmt());

        // CombinedFragmnetをStatementに変換
        Statement statement = translator.translateCombinedFragment(combinedFragment);
        combinedFragment.setStatement(statement);

        // UMLモデルとコードモデルに追加
        targetInteraction.getInteractionFragmentList().add(combinedFragment);
        body.addStatement(statement);

        // 再描画
        umlController.updateDiagram(codeFileOptional.get());
        codeController.updateCodeTab(codeFileOptional.get());
    }

    /**
     * methodDeclarationListから、Operationと一致するmethodDeclarationを探索する
     *
     * @param methodDeclarationList Javaのメソッド宣言リスト
     * @param operation             UMLの操作
     * @return 一致するメソッド宣言（存在しない場合はEmpty）
     */
    protected Optional<MethodDeclaration> findMethodDeclaration(
            List<MethodDeclaration> methodDeclarationList,
            Operation operation) {
        MethodDeclaration targetMethod = null;

        int operationParameterSize = 0;
        try {
            operationParameterSize = operation.getParameters().size();
        } catch (Exception e) {
        }

        for (MethodDeclaration methodDeclaration : methodDeclarationList) {
            if (methodDeclaration.getParameters().size() != operationParameterSize) {
                // 引数の数が異なる場合
                continue;
            }

            if (operationParameterSize == 0) {
                // 引数の数が同じ、かつ、引数が0の場合
                targetMethod = methodDeclaration;
                break;
            }

            // 引数の数が同じ、かつ、引数が複数ある場合
            int cntSameParameters = 0;
            for (int i = 0; i < methodDeclaration.getParameters().size(); i++) {
                if (!methodDeclaration.getParameters().get(i).getTypeAsString()
                        .equals(operation.getParameters().get(i).getType().toString())) {
                    break;
                }
                cntSameParameters++;
            }
            if (cntSameParameters == methodDeclaration.getParameters().size()) {
                targetMethod = methodDeclaration;
                break;
            }
        }

        return Optional.ofNullable(targetMethod);
    }
}