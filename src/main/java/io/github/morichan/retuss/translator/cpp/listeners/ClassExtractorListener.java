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

import org.antlr.v4.runtime.ParserRuleContext;

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

        try {
            // 型と修飾子の分離
            String rawType = ctx.declSpecifierSeq().getText();
            System.out.println("DEBUG: Raw type declaration: " + rawType);

            String type = ctx.declSpecifierSeq().getText();
            System.out.println("DEBUG: Cleaned type: " + type);

            if (ctx.memberDeclaratorList() != null) {
                for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                    try {
                        if (isMethodDeclaration(memberDec)) {
                            handleMethod(memberDec.declarator(), rawType);
                        } else {
                            handleAttribute(memberDec.declarator(), rawType);
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing member declarator: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in enterMemberdeclaration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Set<CppClass.Modifier> detectModifiers(CPP14Parser.DeclaratorContext declarator, String type) {
        Set<CppClass.Modifier> modifiers = EnumSet.noneOf(CppClass.Modifier.class);
        String fullText = type + " " + declarator.getText();

        if (fullText.contains("virtual")) {
            modifiers.add(CppClass.Modifier.VIRTUAL);
        }
        if (fullText.contains("static")) {
            modifiers.add(CppClass.Modifier.STATIC);
        }
        if (fullText.contains("const ")) {
            modifiers.add(CppClass.Modifier.CONST);
        }
        if (fullText.contains("override")) {
            modifiers.add(CppClass.Modifier.OVERRIDE);
        }
        if (fullText.contains("= 0")) {
            modifiers.add(CppClass.Modifier.ABSTRACT);
        }
        if (fullText.contains("mutable")) {
            modifiers.add(CppClass.Modifier.MUTABLE);
        }
        return modifiers;
    }

    private void handleMethod(CPP14Parser.DeclaratorContext declarator, String type) {
        try {
            String methodName = extractMethodName(declarator.getText());
            System.out.println("DEBUG: Processing method: " + methodName);

            Operation operation = new Operation(new Name(methodName));
            operation.setReturnType(new Type(cleanTypeSpecifiers(type)));
            operation.setVisibility(convertVisibility(currentVisibility));

            // パラメータの処理（引数の型のみ表示）
            if (declarator.parametersAndQualifiers() != null &&
                    declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {
                for (CPP14Parser.ParameterDeclarationContext paramCtx : declarator.parametersAndQualifiers()
                        .parameterDeclarationClause()
                        .parameterDeclarationList().parameterDeclaration()) {
                    String paramType = paramCtx.declSpecifierSeq().getText();
                    Parameter param = new Parameter(new Name("")); // 引数名は空に
                    param.setType(new Type(paramType));
                    operation.addParameter(param);
                }
            }

            // 修飾子の検出
            Set<CppClass.Modifier> modifiers = detectModifiers(declarator, type);

            String fullText = type + " " + declarator.getText();
            if (fullText.contains("virtual")) {
                modifiers.add(CppClass.Modifier.VIRTUAL);
            }
            if (fullText.contains("override")) {
                modifiers.add(CppClass.Modifier.OVERRIDE);
            }
            if (isConstMethod(declarator)) {
                modifiers.add(CppClass.Modifier.CONST);
            }
            if (fullText.contains("= 0")) {
                modifiers.add(CppClass.Modifier.ABSTRACT);
            }
            if (declarator.parametersAndQualifiers() != null &&
                    declarator.parametersAndQualifiers().getText().contains("const")) {
                modifiers.add(CppClass.Modifier.CONST);
            }

            currentClass.addOperation(operation);
            if (!modifiers.isEmpty()) {
                currentClass.addMemberModifiers(methodName, modifiers);
                System.out.println("DEBUG: Added modifiers for method " + methodName + ": " + modifiers);
            }

            System.out.println("DEBUG: Added method: " + methodName);
        } catch (Exception e) {
            System.err.println("Error in handleMethod: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 型指定子からvirtualなどの修飾子を除去
    private String cleanTypeSpecifiers(String type) {
        List<String> modifiers = Arrays.asList(
                "virtual", "static", "const", "volatile", "mutable");

        String cleanType = type.trim();
        for (String modifier : modifiers) {
            cleanType = cleanType.replace(modifier, "").trim();
        }

        return cleanType.isEmpty() ? "void" : cleanType;
    }

    private boolean isUserDefinedType(String type) {
        Set<String> basicTypes = Set.of("void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed");
        String cleanType = cleanTypeName(type);
        return !basicTypes.contains(cleanType) &&
                !cleanType.startsWith("std::") &&
                Character.isUpperCase(cleanType.charAt(0));
    }

    private String cleanTypeName(String typeName) {
        return typeName.replaceAll("[*&<>]", "")
                .replaceAll("\\s+", "")
                .replaceAll("const", "")
                .replaceAll("std::", "")
                .trim();
    }

    private void handleParameters(CPP14Parser.ParametersAndQualifiersContext paramsCtx, Operation operation) {
        try {
            if (paramsCtx.parameterDeclarationClause() != null) {
                for (CPP14Parser.ParameterDeclarationContext paramCtx : paramsCtx.parameterDeclarationClause()
                        .parameterDeclarationList().parameterDeclaration()) {

                    String paramType = paramCtx.declSpecifierSeq().getText();
                    String paramName = paramCtx.declarator().getText();

                    Parameter param = new Parameter(new Name(paramName));
                    param.setType(new Type(cleanTypeSpecifiers(paramType)));
                    operation.addParameter(param);
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling parameters: " + e.getMessage());
        }
    }

    private void handleAttribute(CPP14Parser.DeclaratorContext declarator, String type) {
        try {
            // 変数名とポインタの分離
            String attributeText = declarator.getText();
            StringBuilder pointerSuffix = new StringBuilder();
            while (attributeText.contains("*") || attributeText.contains("&")) {
                int idx = attributeText.indexOf('*');
                if (idx == -1)
                    idx = attributeText.indexOf('&');
                pointerSuffix.append(attributeText.charAt(idx));
                attributeText = attributeText.substring(0, idx) + attributeText.substring(idx + 1);
            }
            String attributeName = attributeText.trim();

            // 型の構築（ポインタを型に付加）
            String fullType = type.trim() + pointerSuffix.toString();

            // 修飾子の検出
            Set<CppClass.Modifier> modifiers = EnumSet.noneOf(CppClass.Modifier.class);
            String fullContext = type + " " + declarator.getText();

            if (fullContext.contains("static")) {
                modifiers.add(CppClass.Modifier.STATIC);
            }
            if (fullContext.contains("const")) {
                modifiers.add(CppClass.Modifier.CONST);
            }
            if (fullContext.contains("mutable")) {
                modifiers.add(CppClass.Modifier.MUTABLE);
            }

            // Attribute作成と追加
            Attribute attribute = new Attribute(new Name(attributeName));
            attribute.setType(new Type(cleanTypeSpecifiers(fullType)));
            attribute.setVisibility(convertVisibility(currentVisibility));

            currentClass.addAttribute(attribute);
            if (!modifiers.isEmpty()) {
                currentClass.addMemberModifiers(attributeName, modifiers);
            }

            // ポインタ/参照型の場合は依存関係として記録
            if (pointerSuffix.length() > 0 && isUserDefinedType(cleanTypeName(type))) {
                ((CppClass) currentClass).addDependency(cleanTypeName(type));
            }

            System.out.println("DEBUG: Added attribute: " + attributeName +
                    " with type: " + fullType +
                    " and modifiers: " + modifiers);

        } catch (Exception e) {
            System.err.println("Error in handleAttribute: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addMembersWithModifiers(String memberName, String code) {
        Set<CppClass.Modifier> modifiers = EnumSet.noneOf(CppClass.Modifier.class);

        // コードから修飾子を検出
        if (code.contains("static"))
            modifiers.add(CppClass.Modifier.STATIC);
        if (code.contains("const"))
            modifiers.add(CppClass.Modifier.CONST);
        if (code.contains("virtual"))
            modifiers.add(CppClass.Modifier.VIRTUAL);
        if (code.contains("override"))
            modifiers.add(CppClass.Modifier.OVERRIDE);
        if (code.contains("= 0"))
            modifiers.add(CppClass.Modifier.ABSTRACT);

        currentClass.addMemberModifiers(memberName, modifiers);
    }

    // 修飾子チェック用の補助メソッド
    private boolean isConstMethod(CPP14Parser.DeclaratorContext declarator) {
        return declarator.parametersAndQualifiers() != null &&
                declarator.parametersAndQualifiers().getText().contains("const");
    }

    private boolean isStaticMethod(CPP14Parser.DeclaratorContext declarator) {
        // 親のコンテキストを正しく取得
        ParserRuleContext parent = declarator.getParent();
        while (parent != null && !(parent instanceof CPP14Parser.MemberdeclarationContext)) {
            parent = parent.getParent();
        }

        if (parent instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberCtx = (CPP14Parser.MemberdeclarationContext) parent;
            return memberCtx.declSpecifierSeq() != null &&
                    memberCtx.declSpecifierSeq().getText().contains("static");
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

    public List<Class> getExtractedClasses() {
        return new ArrayList<>(extractedClasses);
    }

    private boolean isVirtualMethod(CPP14Parser.DeclaratorContext declarator) {
        // 同様の修正
        ParserRuleContext parent = declarator.getParent();
        while (parent != null && !(parent instanceof CPP14Parser.MemberdeclarationContext)) {
            parent = parent.getParent();
        }

        if (parent instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberCtx = (CPP14Parser.MemberdeclarationContext) parent;
            return memberCtx.declSpecifierSeq() != null &&
                    memberCtx.declSpecifierSeq().getText().contains("virtual");
        }
        return false;
    }

}