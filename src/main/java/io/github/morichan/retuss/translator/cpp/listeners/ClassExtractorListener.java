package io.github.morichan.retuss.translator.cpp.listeners;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.parser.cpp.CPP14ParserBaseListener;
import io.github.morichan.retuss.translator.cpp.util.CppTypeMapper;
import io.github.morichan.retuss.translator.cpp.util.CppVisibilityMapper;

import java.util.*;

public class ClassExtractorListener extends CPP14ParserBaseListener {
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;
    private List<Class> extractedClasses = new ArrayList<>();
    private Class currentClass;
    private String currentVisibility = "private";

    public ClassExtractorListener(CppTypeMapper typeMapper, CppVisibilityMapper visibilityMapper) {
        this.typeMapper = typeMapper;
        this.visibilityMapper = visibilityMapper;
    }

    // ここが肝
    @Override
    public void enterClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
        if (ctx.classHead() != null &&
                ctx.classHead().classHeadName() != null &&
                ctx.classHead().classHeadName().className() != null) {

            String className = ctx.classHead().classHeadName().className().getText();
            System.out.println("DEBUG: Found class: " + className);
            currentClass = new Class(className);

            // 継承関係の解析
            if (ctx.classHead().baseClause() != null) {
                for (CPP14Parser.BaseSpecifierContext baseSpec : ctx.classHead().baseClause().baseSpecifierList()
                        .baseSpecifier()) {
                    String baseClassName = baseSpec.baseTypeSpecifier().getText();
                    String accessSpecifier = baseSpec.accessSpecifier() != null
                            ? baseSpec.accessSpecifier().getText().toLowerCase()
                            : "private";

                    System.out.println("DEBUG: Found base class: " + baseClassName +
                            " with access: " + accessSpecifier);

                    Class superClass = new Class(baseClassName, false);
                    currentClass.setSuperClass(superClass);
                }
            }
            // クラスをリストに追加
            extractedClasses.add(currentClass);
        }
    }

    @Override
    public void exitClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
        if (currentClass != null) {
            dumpClassInfo(currentClass);
            currentClass = null;
            currentVisibility = "private"; // リセット
        }
    }

    @Override
    public void enterMemberdeclaration(CPP14Parser.MemberdeclarationContext ctx) {
        if (currentClass == null || ctx.declSpecifierSeq() == null)
            return;

        String type = ctx.declSpecifierSeq().getText();
        System.out.println("DEBUG: Processing member - Type: " + type +
                ", Current visibility: " + currentVisibility);

        if (ctx.memberDeclaratorList() != null) {
            for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                handleMemberDeclarator(memberDec, type);
            }
        }
    }

    private void handleMemberDeclarator(CPP14Parser.MemberDeclaratorContext memberDec, String type) {
        if (memberDec.declarator() == null)
            return;

        CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
        boolean isMethod = declarator.parametersAndQualifiers() != null;

        if (isMethod) {
            handleMethod(declarator, type);
        } else {
            handleAttribute(declarator, type);
        }
    }

    private void handleMethod(CPP14Parser.DeclaratorContext declarator, String type) {
        String methodName = extractMethodName(declarator.getText());
        Operation operation = new Operation(new Name(methodName));
        operation.setReturnType(new Type(type));
        operation.setVisibility(convertVisibility(currentVisibility));

        // パラメータの処理
        if (declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {
            for (CPP14Parser.ParameterDeclarationContext param : declarator.parametersAndQualifiers()
                    .parameterDeclarationClause()
                    .parameterDeclarationList()
                    .parameterDeclaration()) {

                Parameter parameter = new Parameter(
                        new Name(param.declarator().getText()));
                parameter.setType(new Type(param.declSpecifierSeq().getText()));
                operation.addParameter(parameter);
            }
        }

        currentClass.addOperation(operation);
        System.out.println("DEBUG: Added method: " + methodName +
                " with visibility: " + currentVisibility);
    }

    private Visibility convertVisibility(String visibility) {
        if (visibility == null)
            return Visibility.Private;

        switch (visibility.toLowerCase()) {
            case "public":
                return Visibility.Public;
            case "protected":
                return Visibility.Protected;
            case "private":
                return Visibility.Private;
            default:
                return Visibility.Private;
        }
    }

    private void handleAttribute(CPP14Parser.DeclaratorContext declarator, String type) {
        String attributeName = declarator.getText();
        Attribute attribute = new Attribute(new Name(attributeName));
        attribute.setType(new Type(type));
        attribute.setVisibility(convertVisibility(currentVisibility));

        currentClass.addAttribute(attribute);
        System.out.println("DEBUG: Added attribute: " + attributeName +
                " with visibility: " + currentVisibility);
    }

    private boolean isMethodDeclaration(CPP14Parser.DeclaratorContext declarator) {
        // パラメータリストの存在チェック
        if (declarator.parametersAndQualifiers() != null) {
            return true;
        }

        // ポインタ宣言のチェック
        if (declarator.pointerDeclarator() != null &&
                declarator.pointerDeclarator().noPointerDeclarator() != null &&
                declarator.pointerDeclarator().noPointerDeclarator().parametersAndQualifiers() != null) {
            return true;
        }

        return false;
    }

    private void handleMethodDeclaration(String declaratorText, String returnType) {
        String methodName = declaratorText.substring(0, declaratorText.indexOf('('));
        Operation operation = new Operation(new Name(methodName)); // コンストラクタに Name を渡す
        operation.setReturnType(new Type(returnType));
        operation.setVisibility(convertVisibility(currentVisibility));

        currentClass.addOperation(operation);
        System.out.println("DEBUG: Added method - " + methodName +
                " with visibility " + currentVisibility);
    }

    private void handleFieldDeclaration(String declaratorText, String type) {
        // Name インスタンスを作成して Attribute のコンストラクタに渡す
        Attribute attribute = new Attribute(new Name(declaratorText));

        // 型の設定
        attribute.setType(new Type(type));

        // 可視性の設定
        attribute.setVisibility(convertVisibility(currentVisibility));

        // クラスに属性を追加
        currentClass.addAttribute(attribute);

        System.out.println("DEBUG: Added field - " + declaratorText +
                " with type - " + type +
                " and visibility - " + currentVisibility);
    }

    private void handleFieldDeclaration(CPP14Parser.MemberDeclaratorContext memberDec, String type) {
        if (memberDec.declarator() == null)
            return;

        // 宣言子からフィールド名を抽出
        String fieldName = extractFieldName(memberDec.declarator());

        // デフォルト値の抽出（存在する場合）
        Optional<String> defaultValue = extractDefaultValue(memberDec);

        // Attribute インスタンスの作成
        Attribute attribute = new Attribute(new Name(fieldName));
        attribute.setType(new Type(type));
        attribute.setVisibility(convertVisibility(currentVisibility));

        // デフォルト値がある場合は設定
        defaultValue.ifPresent(value -> attribute.setDefaultValue(new DefaultValue(new OneIdentifier(value))));

        // 定数かどうかの判定
        if (isConstField(memberDec)) {
            // TODO: 定数フィールドの処理
        }

        // クラスに属性を追加
        currentClass.addAttribute(attribute);

        System.out.println("DEBUG: Added field - " + fieldName +
                "\n  Type: " + type +
                "\n  Visibility: " + currentVisibility +
                "\n  Default Value: " + defaultValue.orElse("none"));
    }

    private String extractFieldName(CPP14Parser.DeclaratorContext declarator) {
        // ポインタ宣言子の処理
        if (declarator.pointerDeclarator() != null) {
            return declarator.pointerDeclarator().noPointerDeclarator().getText();
        }
        // 配列宣言子の処理
        if (declarator.noPointerDeclarator() != null &&
                declarator.noPointerDeclarator().constantExpression() != null) {
            String name = declarator.noPointerDeclarator().getText();
            return name.substring(0, name.indexOf('['));
        }
        // 通常の宣言子
        return declarator.getText();
    }

    private Optional<String> extractDefaultValue(CPP14Parser.MemberDeclaratorContext memberDec) {
        if (memberDec.braceOrEqualInitializer() != null) {
            return Optional.of(memberDec.braceOrEqualInitializer().getText().substring(1)); // '=' を除去
        }
        return Optional.empty();
    }

    private boolean isConstField(CPP14Parser.MemberDeclaratorContext memberDec) {
        // const 修飾子の確認
        if (memberDec.getParent() instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberDeclaration = (CPP14Parser.MemberdeclarationContext) memberDec
                    .getParent();
            if (memberDeclaration.declSpecifierSeq() != null) {
                return memberDeclaration.declSpecifierSeq().getText().contains("const");
            }
        }
        return false;
    }

    private void dumpClassInfo(Class cls) {
        System.out.println("\nDEBUG: Class Info ----");
        System.out.println("Class name: " + cls.getName());

        if (cls.getSuperClass().isPresent()) {
            System.out.println("Superclass: " + cls.getSuperClass().get().getName());
        }

        System.out.println("Attributes:");
        for (Attribute attr : cls.getAttributeList()) {
            System.out.println("  - Name: " + attr.getName() +
                    ", Type: " + attr.getType() +
                    ", Visibility: " + visibilitySymbol(attr.getVisibility()));
        }

        System.out.println("Operations:");
        for (Operation op : cls.getOperationList()) {
            System.out.println("  - Name: " + op.getName() +
                    ", ReturnType: " + op.getReturnType() +
                    ", Visibility: " + visibilitySymbol(op.getVisibility()));
        }
        System.out.println("------------------");
    }

    private String visibilitySymbol(Visibility visibility) {
        switch (visibility) {
            case Public:
                return "+";
            case Protected:
                return "#";
            case Private:
                return "-";
            default:
                return "~";
        }
    }

    private void logClassInfo(Class cls) {
        System.out.println("\nDEBUG: Class Info ----");
        System.out.println("Class name: " + cls.getName());
        System.out.println("Attributes:");
        for (Attribute attr : cls.getAttributeList()) {
            System.out.println("  - Name: " + attr.getName() +
                    ", Type: " + attr.getType() +
                    ", Visibility: " + attr.getVisibility());
        }
        System.out.println("Operations:");
        for (Operation op : cls.getOperationList()) {
            System.out.println("  - Name: " + op.getName() +
                    ", ReturnType: " + op.getReturnType() +
                    ", Visibility: " + op.getVisibility());
        }
        System.out.println("------------------\n");
    }

    public List<Class> getExtractedClasses() {
        return extractedClasses;
    }

    private String extractMethodName(String fullText) {
        int parenIndex = fullText.indexOf('(');
        if (parenIndex > 0) {
            String nameWithScope = fullText.substring(0, parenIndex);
            int scopeIndex = nameWithScope.lastIndexOf("::");
            if (scopeIndex > 0) {
                return nameWithScope.substring(scopeIndex + 2);
            }
            return nameWithScope;
        }
        return fullText;
    }

    @Override
    public void enterAccessSpecifier(CPP14Parser.AccessSpecifierContext ctx) {
        currentVisibility = ctx.getText().toLowerCase();
        System.out.println("DEBUG: Access specifier changed to: " + currentVisibility);
    }

    private boolean isVirtualMethod(CPP14Parser.MemberDeclaratorContext ctx) {
        if (ctx.getParent() instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberDeclaration = (CPP14Parser.MemberdeclarationContext) ctx
                    .getParent();
            if (memberDeclaration.declSpecifierSeq() != null) {
                return memberDeclaration.declSpecifierSeq().getText().contains("virtual");
            }
        }
        return false;
    }

}