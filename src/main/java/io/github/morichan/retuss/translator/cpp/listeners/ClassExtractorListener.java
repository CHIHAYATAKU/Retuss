package io.github.morichan.retuss.translator.cpp.listeners;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.parser.cpp.CPP14ParserBaseListener;
import io.github.morichan.retuss.translator.cpp.util.CppTypeMapper;
import io.github.morichan.retuss.translator.cpp.util.CppVisibilityMapper;

import java.util.*;

public class ClassExtractorListener extends CPP14ParserBaseListener {
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;
    private List<CppClass> extractedClasses = new ArrayList<>();
    private CppClass currentClass;
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
            currentClass = new CppClass(className);

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

                    CppClass superClass = new CppClass(baseClassName, false);
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
                if (isMethodDeclaration(memberDec)) {
                    handleMethod(memberDec.declarator(), type);
                } else {
                    handleAttribute(memberDec.declarator(), type);
                }
            }
        }
    }

    private void handleMethod(CPP14Parser.DeclaratorContext declarator, String type) {
        try {
            String methodName = extractMethodName(declarator.getText());
            Operation operation = new Operation(new Name(methodName));
            operation.setReturnType(new Type(cleanTypeSpecifiers(type)));
            operation.setVisibility(convertVisibility(currentVisibility));

            // 修飾子の収集
            List<String> modifiers = new ArrayList<>();
            if (isVirtualMethod(declarator)) {
                modifiers.add("virtual");
            }
            if (isConstMethod(declarator)) {
                modifiers.add("const");
            }
            if (isStaticMethod(declarator)) {
                modifiers.add("static");
            }

            // CppClassに修飾子情報を追加
            currentClass.addMemberModifiers(methodName, modifiers);

            // 操作の追加
            currentClass.addOperation(operation);
        } catch (Exception e) {
            System.err.println("Error in handleMethod: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 型指定子からvirtualなどの修飾子を除去
    private String cleanTypeSpecifiers(String type) {
        // 修飾子のリスト
        List<String> modifiers = Arrays.asList(
                "virtual", "static", "const", "volatile", "mutable");

        String cleanType = type.trim();
        for (String modifier : modifiers) {
            cleanType = cleanType.replace(modifier, "").trim();
        }

        // 空の場合はvoidを返す
        return cleanType.isEmpty() ? "void" : cleanType;
    }

    private void handleParameters(CPP14Parser.DeclaratorContext declarator,
            Operation operation) {
        if (declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {
            for (CPP14Parser.ParameterDeclarationContext param : declarator.parametersAndQualifiers()
                    .parameterDeclarationClause()
                    .parameterDeclarationList()
                    .parameterDeclaration()) {

                String paramName = param.declarator().getText();
                // パラメータの型も同様にクリーンアップ
                String paramType = cleanTypeSpecifiers(
                        param.declSpecifierSeq().getText());

                Parameter parameter = new Parameter(new Name(paramName));
                parameter.setType(new Type(paramType));
                operation.addParameter(parameter);
            }
        }
    }

    private void handleAttribute(CPP14Parser.DeclaratorContext declarator, String type) {
        String attributeName = declarator.getText();

        // 基本のAttribute作成
        Attribute attribute = new Attribute(new Name(attributeName));
        attribute.setType(new Type(cleanTypeSpecifiers(type)));
        attribute.setVisibility(convertVisibility(currentVisibility));

        // 修飾子の情報をNameに含める（一時的な対応）
        List<String> modifiers = new ArrayList<>();
        if (isConstField(declarator))
            modifiers.add("const");
        if (isStaticField(declarator))
            modifiers.add("static");
        if (isMutableField(declarator))
            modifiers.add("mutable");

        if (!modifiers.isEmpty()) {
            String modifiedName = "≪" + String.join(", ", modifiers) + "≫ " + attributeName;
            attribute.setName(new Name(modifiedName));
        }

        // CppClassに修飾子情報を追加
        currentClass.addMemberModifiers(attributeName, modifiers);
        // 属性の追加
        currentClass.addAttribute(attribute);
    }

    // 修飾子チェック用の補助メソッド
    private boolean isConstMethod(CPP14Parser.DeclaratorContext declarator) {
        if (declarator.parametersAndQualifiers() != null) {
            return declarator.parametersAndQualifiers().getText().contains("const");
        }
        return false;
    }

    private boolean isStaticMethod(CPP14Parser.DeclaratorContext declarator) {
        if (declarator.getParent() instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberDeclaration = (CPP14Parser.MemberdeclarationContext) declarator
                    .getParent();
            if (memberDeclaration.declSpecifierSeq() != null) {
                return memberDeclaration.declSpecifierSeq().getText().contains("static");
            }
        }
        return false;
    }

    private boolean isStaticField(CPP14Parser.DeclaratorContext declarator) {
        // staticフィールドの判定
        if (declarator.getParent() instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberDeclaration = (CPP14Parser.MemberdeclarationContext) declarator
                    .getParent();
            if (memberDeclaration.declSpecifierSeq() != null) {
                return memberDeclaration.declSpecifierSeq().getText().contains("static");
            }
        }
        return false;
    }

    private boolean isConstField(CPP14Parser.DeclaratorContext declarator) {
        // const 修飾子の確認
        if (declarator.getParent() instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberDeclaration = (CPP14Parser.MemberdeclarationContext) declarator
                    .getParent();
            if (memberDeclaration.declSpecifierSeq() != null) {
                return memberDeclaration.declSpecifierSeq().getText().contains("const");
            }
        }
        return false;
    }

    private boolean isMutableField(CPP14Parser.DeclaratorContext declarator) {
        // mutableフィールドの判定
        if (declarator.getParent() instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberDeclaration = (CPP14Parser.MemberdeclarationContext) declarator
                    .getParent();
            if (memberDeclaration.declSpecifierSeq() != null) {
                return memberDeclaration.declSpecifierSeq().getText().contains("mutable");
            }
        }
        return false;
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

    private boolean isMethodDeclaration(CPP14Parser.MemberDeclaratorContext memberDec) {
        // 1. 基本的なメソッド判定
        if (memberDec.declarator().parametersAndQualifiers() != null)
            return true;

        // 2. ポインタメソッドの判定
        CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
        if (declarator.pointerDeclarator() != null) {
            var noPointerDec = declarator.pointerDeclarator().noPointerDeclarator();
            if (noPointerDec != null && noPointerDec.parametersAndQualifiers() != null) {
                return true;
            }
        }

        // 3. 関数ポインタの判定
        if (declarator.pointerDeclarator() != null &&
                declarator.getText().contains("(*)")) {
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

    private void dumpClassInfo(CppClass cls) {
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

    private void logClassInfo(CppClass cls) {
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
        // CppClassはClassのサブクラスなので、このリストはList<Class>として扱える
        return new ArrayList<>(extractedClasses);
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

    private boolean isVirtualMethod(CPP14Parser.DeclaratorContext declarator) {
        // 親のMemberDeclarationContextを取得する必要がある
        if (declarator.getParent() instanceof CPP14Parser.MemberDeclaratorContext) {
            CPP14Parser.MemberDeclaratorContext memberDec = (CPP14Parser.MemberDeclaratorContext) declarator
                    .getParent();

            if (memberDec.getParent() instanceof CPP14Parser.MemberdeclarationContext) {
                CPP14Parser.MemberdeclarationContext memberDeclaration = (CPP14Parser.MemberdeclarationContext) memberDec
                        .getParent();

                if (memberDeclaration.declSpecifierSeq() != null) {
                    return memberDeclaration.declSpecifierSeq()
                            .getText().contains("virtual");
                }
            }
        }
        return false;
    }

}