package io.github.morichan.retuss.translator.cpp.listeners;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
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

    @Override
    public void enterClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
        if (ctx.classHead() != null &&
                ctx.classHead().classHeadName() != null &&
                ctx.classHead().classHeadName().className() != null) {

            String className = ctx.classHead().classHeadName().className().getText();
            System.out.println("DEBUG: Found class: " + className);
            currentClass = new Class(className);
        }
    }

    @Override
    public void exitClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
        if (currentClass != null) {
            extractedClasses.add(currentClass);
            System.out.println("DEBUG: Finished processing class: " + currentClass.getName());
            logClassInfo(currentClass);
            currentClass = null;
            currentVisibility = "private"; // リセット
        }
    }

    @Override
    public void enterMemberdeclaration(CPP14Parser.MemberdeclarationContext ctx) {
        if (currentClass == null)
            return;

        try {
            if (ctx.declSpecifierSeq() != null) {
                String type = ctx.declSpecifierSeq().getText();
                System.out.println("DEBUG: Processing member - Type: " + type +
                        ", Current visibility: " + currentVisibility);

                if (ctx.memberDeclaratorList() != null) {
                    for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList()
                            .memberDeclarator()) {
                        handleMemberDeclarator(memberDec, type);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in enterMemberdeclaration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void enterAccessSpecifier(CPP14Parser.AccessSpecifierContext ctx) {
        currentVisibility = ctx.getText().toLowerCase();
        System.out.println("DEBUG: Access specifier changed to: " + currentVisibility);
    }

    private void handleMemberDeclarator(CPP14Parser.MemberDeclaratorContext memberDec, String type) {
        if (memberDec.declarator() == null)
            return;

        CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
        String declaratorText = declarator.getText();

        // パラメータリストの存在を確認してメソッドかを判定
        boolean isMethod = declarator.parametersAndQualifiers() != null;

        System.out.println("DEBUG: Processing declarator: " + declaratorText +
                ", isMethod: " + isMethod +
                ", visibility: " + currentVisibility);

        if (isMethod) {
            handleMethodDeclaration(declaratorText, type);
        } else {
            handleFieldDeclaration(declaratorText, type);
        }
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

    private Visibility convertVisibility(String visibility) {
        if (visibility == null)
            return Visibility.Private;

        switch (visibility.toUpperCase()) {
            case "PUBLIC":
                return Visibility.Public;
            case "PROTECTED":
                return Visibility.Protected;
            case "PRIVATE":
                return Visibility.Private;
            default:
                return Visibility.Private;
        }
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
}