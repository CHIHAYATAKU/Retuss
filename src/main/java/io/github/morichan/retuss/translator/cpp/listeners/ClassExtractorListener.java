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
    private final Set<String> analyzedTypes = new HashSet<>();
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
                            handleMethod(memberDec.declarator(), type);
                        } else {
                            handleAttribute(memberDec.declarator(), type);
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

    private void handleMethod(CPP14Parser.DeclaratorContext declarator, String type) {
        try {
            String rawMethodName = extractMethodName(declarator.getText());
            String methodName = rawMethodName; // ポインタ記号を保持
            boolean isDestructor = methodName.startsWith("~");

            // 重複チェックの改善 - パラメータも含めて比較
            String signature = generateMethodSignature(methodName, declarator);
            if (currentClass.getOperationList().stream()
                    .anyMatch(op -> generateMethodSignature(op.getName().getNameText(), declarator)
                            .equals(signature))) {
                System.out.println("DEBUG: Skipping duplicate method: " + signature);
                return;
            }

            Operation operation = new Operation(new Name(methodName));

            // 戻り値の型の処理
            String processedType;
            if (isDestructor) {
                processedType = "void";
            } else {
                processedType = processReturnType(type);
            }
            operation.setReturnType(new Type(processedType));
            operation.setVisibility(convertVisibility(currentVisibility));

            // パラメータの処理
            processParameters(declarator, operation);

            // 修飾子の検出を改善
            Set<CppClass.Modifier> modifiers = detectModifiers(declarator, type);
            if (isDestructor || type.contains("virtual")) {
                modifiers.add(CppClass.Modifier.VIRTUAL);
            }

            // パラメータの解析
            if (declarator.parametersAndQualifiers() != null &&
                    declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {
                for (CPP14Parser.ParameterDeclarationContext param : declarator.parametersAndQualifiers()
                        .parameterDeclarationClause()
                        .parameterDeclarationList()
                        .parameterDeclaration()) {

                    String paramType = param.declSpecifierSeq().getText();
                    analyzeTypeRelationship(paramType); // パラメータの型も関係として解析

                    Parameter parameter = new Parameter(new Name(param.declarator().getText()));
                    parameter.setType(new Type(paramType));
                    operation.addParameter(parameter);
                }
            }

            currentClass.addOperation(operation);
            for (CppClass.Modifier modifier : modifiers) {
                currentClass.addMemberModifier(methodName, modifier);
            }

            System.out.println("DEBUG: Added method: " + methodName +
                    " with type: " + processedType +
                    " modifiers: " + modifiers);

        } catch (Exception e) {
            System.err.println("Error in handleMethod: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isPointerOrReference(String type) {
        return type.contains("*") || type.contains("&");
    }

    private boolean isArray(String type) {
        return type.matches(".*\\[\\d*\\]");
    }

    private void analyzeTypeRelationship(String type) {
        if (!isUserDefinedType(cleanTypeName(type)) || analyzedTypes.contains(type)) {
            return;
        }

        CppClass cppClass = (CppClass) currentClass;
        String cleanType = cleanTypeName(type);

        if (isPointerOrReference(type)) {
            cppClass.addDependency(cleanType);
        } else if (isCollectionType(type)) {
            String elementType = extractElementType(type);
            if (isUserDefinedType(elementType)) {
                cppClass.addComposition(elementType);
                cppClass.setMultiplicity(elementType, "*");
            }
        } else if (isArray(type)) {
            String baseType = extractArrayBaseType(type);
            if (isUserDefinedType(baseType)) {
                cppClass.addComposition(baseType);
                cppClass.setMultiplicity(baseType, extractArraySize(type));
            }
        } else {
            cppClass.addComposition(cleanType);
            cppClass.setMultiplicity(cleanType, "1");
        }

        analyzedTypes.add(type);
    }

    private String extractArrayBaseType(String type) {
        return type.replaceAll("\\[.*\\]", "").trim();
    }

    private String extractArraySize(String type) {
        if (type.matches(".*\\[\\d+\\]")) {
            return type.replaceAll(".*\\[(\\d+)\\].*", "$1");
        }
        return "*";
    }

    private void processParameters(CPP14Parser.DeclaratorContext declarator, Operation operation) {
        if (declarator.parametersAndQualifiers() != null &&
                declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {
            for (CPP14Parser.ParameterDeclarationContext paramCtx : declarator.parametersAndQualifiers()
                    .parameterDeclarationClause()
                    .parameterDeclarationList().parameterDeclaration()) {
                String paramType = paramCtx.declSpecifierSeq().getText();
                String paramName = "";
                if (paramCtx.declarator() != null) {
                    paramName = extractParameterName(paramCtx.declarator());
                    // パラメータ型からの関係を抽出
                    analyzeParameterType(paramType, paramCtx.declarator());
                }

                Parameter param = new Parameter(new Name(paramName));
                // パラメータの型をクリーンアップ
                String processedParamType = processParamType(paramType, paramCtx.declarator());
                param.setType(new Type(processedParamType));
                operation.addParameter(param);
            }
        }
    }

    private String processReturnType(String type) {
        // static を除去
        String processedType = type.replaceAll("static", "").trim();
        // virtual を除去
        processedType = processedType.replaceAll("virtual", "").trim();
        // const を保持
        return processedType.trim();
    }

    private String generateMethodSignature(String methodName, CPP14Parser.DeclaratorContext declarator) {
        StringBuilder signature = new StringBuilder(methodName);
        if (declarator.parametersAndQualifiers() != null &&
                declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {
            signature.append("(");
            List<String> paramTypes = new ArrayList<>();
            for (CPP14Parser.ParameterDeclarationContext paramCtx : declarator.parametersAndQualifiers()
                    .parameterDeclarationClause()
                    .parameterDeclarationList().parameterDeclaration()) {
                paramTypes.add(paramCtx.declSpecifierSeq().getText());
            }
            signature.append(String.join(",", paramTypes));
            signature.append(")");
        }
        return signature.toString();
    }

    private String processParamType(String type, CPP14Parser.DeclaratorContext declarator) {
        StringBuilder processedType = new StringBuilder();

        // constの処理
        if (type.contains("const")) {
            processedType.append("const ");
        }

        // 基本型
        String baseType = type.replaceAll("(const|volatile)", "")
                .replaceAll("std::", "")
                .trim();
        processedType.append(baseType);

        // ポインタ/参照の追加
        String declaratorText = declarator.getText();
        if (declaratorText.contains("*") || type.contains("*")) {
            processedType.append("*");
        }
        if (declaratorText.contains("&") || type.contains("&")) {
            processedType.append("&");
        }

        return processedType.toString();
    }

    private void analyzeParameterType(String type, CPP14Parser.DeclaratorContext declarator) {
        String cleanType = cleanTypeName(type);
        if (!isUserDefinedType(cleanType)) {
            return;
        }

        // パラメータのポインタ/参照は依存関係として扱う
        if (type.contains("*") || type.contains("&") ||
                declarator.getText().contains("*") || declarator.getText().contains("&")) {
            ((CppClass) currentClass).addDependency(cleanType);
            System.out.println("DEBUG: Added parameter dependency to " + cleanType);
        }
    }

    // 型解析と関係抽出のメソッド群
    private void analyzeTypeRelationship(String type, String declarator) {
        CppClass cppClass = (CppClass) currentClass;
        String cleanType = cleanTypeName(type);

        if (!isUserDefinedType(cleanType)) {
            return;
        }

        System.out.println("DEBUG: Analyzing type relationship for: " + type);

        // コンテナ型の処理
        if (isCollectionType(type)) {
            String elementType = extractElementType(type);
            if (isUserDefinedType(elementType)) {
                String cleanElementType = cleanTypeName(elementType);
                cppClass.addComposition(cleanElementType);
                cppClass.setMultiplicity(cleanElementType, "*");
                System.out.println("DEBUG: Added collection composition for " + cleanElementType);
                return;
            }
        }

        // 配列の処理
        if (declarator.matches(".*\\[\\d+\\]")) {
            String size = declarator.replaceAll(".*\\[(\\d+)\\].*", "$1");
            cppClass.addComposition(cleanType);
            cppClass.setMultiplicity(cleanType, size);
            System.out.println("DEBUG: Added array composition for " + cleanType + " with size " + size);
            return;
        }

        // ポインタ/参照による依存関係
        if (type.contains("*") || type.contains("&") ||
                declarator.contains("*") || declarator.contains("&")) {
            cppClass.addDependency(cleanType);
            System.out.println("DEBUG: Added dependency for " + cleanType);
            return;
        }

        // 通常のインスタンスメンバ
        cppClass.addComposition(cleanType);
        cppClass.setMultiplicity(cleanType, "1");
        System.out.println("DEBUG: Added composition for " + cleanType);
    }

    // ヘルパーメソッド群
    private boolean isCollectionType(String type) {
        return type.contains("vector<") ||
                type.contains("array<") ||
                type.contains("list<") ||
                type.contains("set<") ||
                type.contains("deque<") ||
                type.contains("queue<") ||
                type.contains("stack<") ||
                type.contains("map<");
    }

    private String extractElementType(String type) {
        if (type.contains("<")) {
            return cleanTypeName(type.replaceAll(".*<(.+)>.*", "$1"));
        }
        return type;
    }

    private String cleanTypeName(String type) {
        return type.replaceAll("[*&\\[\\]]", "") // ポインタ、参照、配列記号を除去
                .replaceAll("const", "") // constを除去
                .replaceAll("volatile", "") // volatileを除去
                .replaceAll("std::", "") // std::を除去
                .replaceAll("<.*>", "") // テンプレートパラメータを除去
                .trim();
    }

    private boolean isUserDefinedType(String type) {
        Set<String> basicTypes = Set.of(
                "void", "bool", "char", "signed", "unsigned",
                "short", "int", "long", "float", "double",
                "wchar_t", "auto", "string");
        String cleanType = cleanTypeName(type);
        return !basicTypes.contains(cleanType) &&
                !cleanType.startsWith("std::") &&
                Character.isUpperCase(cleanType.charAt(0));
    }

    private String extractParameterName(CPP14Parser.DeclaratorContext declarator) {
        String name = declarator.getText();
        // ポインタ/参照記号を除去
        return name.replaceAll("[*&]", "").trim();
    }

    // 型指定子からvirtualなどの修飾子を除去
    private String cleanTypeSpecifiers(String type) {
        List<String> modifiers = Arrays.asList(
                "virtual", "static", "const", "volatile", "mutable");

        String cleanType = type.trim();
        for (String modifier : modifiers) {
            cleanType = cleanType.replace(modifier, "").trim();
        }

        // std::プレフィックスの除去
        cleanType = cleanType.replaceAll("std::", "");

        return cleanType.isEmpty() ? "void" : cleanType;
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
            // ポインタ/参照記号を含む属性名を保持
            String attributeText = declarator.getText();
            String attributeName = attributeText; // ポインタ記号を保持したまま

            // 重複チェック - ポインタ記号を除去して比較
            String normalizedName = attributeName.replaceAll("[*&]", "").trim();
            if (currentClass.getAttributeList().stream()
                    .anyMatch(attr -> attr.getName().getNameText().replaceAll("[*&]", "").trim()
                            .equals(normalizedName))) {
                System.out.println("DEBUG: Skipping duplicate attribute: " + attributeName);
                return;
            }

            // 型の処理
            String processedType = processAttributeType(type, declarator);

            // 修飾子の検出
            Set<CppClass.Modifier> modifiers = detectModifiers(declarator, type);

            // static修飾子の処理
            if (type.contains("static")) {
                modifiers.add(CppClass.Modifier.STATIC);
                processedType = processedType.replaceAll("static", "").trim();
            }

            // 属性の作成と追加
            Attribute attribute = new Attribute(new Name(attributeName));
            attribute.setType(new Type(processedType));
            attribute.setVisibility(convertVisibility(currentVisibility));

            currentClass.addAttribute(attribute);
            for (CppClass.Modifier modifier : modifiers) {
                currentClass.addMemberModifier(attributeName, modifier);
            }

            // 関係の解析と追加
            analyzeAttributeRelationships(type, declarator);

            System.out.println("DEBUG: Added attribute: " + attributeName +
                    " with type: " + processedType +
                    " modifiers: " + modifiers);
        } catch (Exception e) {
            System.err.println("Error in handleAttribute: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractAttributeName(CPP14Parser.DeclaratorContext declarator) {
        String name = declarator.getText();
        // 配列表記を除去
        return name.replaceAll("\\[.*\\]", "").trim();
    }

    private void analyzeAttributeRelationships(String type, CPP14Parser.DeclaratorContext declarator) {
        String cleanType = cleanTypeName(type);
        if (!isUserDefinedType(cleanType)) {
            return;
        }

        String declaratorText = declarator.getText();
        CppClass cppClass = (CppClass) currentClass;

        // コンテナ型の処理
        if (isCollectionType(type)) {
            String elementType = extractElementType(type);
            if (isUserDefinedType(elementType)) {
                cppClass.addComposition(elementType);
                cppClass.setMultiplicity(elementType, "*");
                System.out.println("DEBUG: Added collection relationship to " + elementType);
            }
        }

        // 配列の処理
        if (declaratorText.matches(".*\\[\\d+\\]")) {
            String size = declaratorText.replaceAll(".*\\[(\\d+)\\].*", "$1");
            cppClass.addComposition(cleanType);
            cppClass.setMultiplicity(cleanType, size);
            System.out.println("DEBUG: Added array relationship to " + cleanType + "[" + size + "]");
        }

        // ポインタ/参照の処理
        if (type.contains("*") || type.contains("&") ||
                declaratorText.contains("*") || declaratorText.contains("&")) {
            cppClass.addDependency(cleanType);
            System.out.println("DEBUG: Added dependency to " + cleanType);
        }

        // 通常のインスタンス
        if (!type.contains("*") && !type.contains("&") &&
                !declaratorText.contains("*") && !declaratorText.contains("&") &&
                !isCollectionType(type) && !declaratorText.matches(".*\\[\\d+\\]")) {
            cppClass.addComposition(cleanType);
            cppClass.setMultiplicity(cleanType, "1");
            System.out.println("DEBUG: Added composition to " + cleanType);
        }
    }

    private String processAttributeType(String type, CPP14Parser.DeclaratorContext declarator) {
        StringBuilder processedType = new StringBuilder();

        // 基本型（staticを除去）
        String baseType = type.replaceAll("static", "")
                .replaceAll("std::", "")
                .trim();

        // const修飾子は保持
        if (baseType.contains("const")) {
            processedType.append("const ");
            baseType = baseType.replaceAll("const", "").trim();
        }

        processedType.append(baseType);

        // ポインタ/参照の処理
        String declaratorText = declarator.getText();
        if (declaratorText.contains("*") || type.contains("*")) {
            processedType.append("*");
        }
        if (declaratorText.contains("&") || type.contains("&")) {
            processedType.append("&");
        }

        // 配列の処理
        if (declaratorText.matches(".*\\[\\d+\\]")) {
            String size = declaratorText.replaceAll(".*\\[(\\d+)\\].*", "$1");
            processedType.append("[").append(size).append("]");
        }

        return processedType.toString();
    }

    private Set<CppClass.Modifier> detectModifiers(CPP14Parser.DeclaratorContext declarator, String type) {
        Set<CppClass.Modifier> modifiers = EnumSet.noneOf(CppClass.Modifier.class);
        String fullText = type + " " + declarator.getText();

        // 修飾子の検出
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
        if (memberDec.declarator() == null)
            return false;

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