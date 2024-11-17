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
import io.github.morichan.retuss.model.uml.CppClass.RelationshipInfo;
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
                if (ctx.memberDeclaratorList() != null) {
                    for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList()
                            .memberDeclarator()) {
                        if (isMethodDeclaration(memberDec)) {
                            // メソッドの場合はパラメータも含めて関係を解析
                            handleMethod(memberDec.declarator(), type);
                            analyzeMethodRelationships(memberDec.declarator());
                        } else {
                            handleAttribute(memberDec.declarator(), type);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in enterMemberdeclaration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void analyzeMethodRelationships(CPP14Parser.DeclaratorContext declarator) {
        if (declarator.parametersAndQualifiers() != null &&
                declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {

            for (CPP14Parser.ParameterDeclarationContext paramCtx : declarator.parametersAndQualifiers()
                    .parameterDeclarationClause()
                    .parameterDeclarationList().parameterDeclaration()) {

                String paramType = paramCtx.declSpecifierSeq().getText();
                String paramDeclText = paramCtx.declarator() != null ? paramCtx.declarator().getText() : "";

                analyzeTypeForRelationship(paramType, paramDeclText);
            }
        }
    }

    @Override
    public void enterSimpleTypeSpecifier(CPP14Parser.SimpleTypeSpecifierContext ctx) {
        String type = ctx.getText();
        String cleanType = cleanTypeName(type);
        if (isUserDefinedType(cleanType)) {
            ((CppClass) currentClass).addDependency(cleanType);
            System.out.println("DEBUG: Added dependency from type specifier: " + cleanType);
        }
    }

    private void handleMethod(CPP14Parser.DeclaratorContext declarator, String type) {
        try {
            String methodName = extractMethodName(declarator.getText()).replaceAll("[*&]", "").trim();
            boolean isDestructor = methodName.startsWith("~");

            if (currentClass.getOperationList().stream()
                    .anyMatch(op -> op.getName().getNameText().equals(methodName))) {
                return;
            }

            // 修飾子の収集
            Set<CppClass.Modifier> modifiers = new LinkedHashSet<>();
            String processedType = type;

            // virtual修飾子
            if (type.contains("virtual") || isDestructor) {
                modifiers.add(CppClass.Modifier.VIRTUAL);
                processedType = processedType.replaceAll("virtual", "").trim();
            }
            // static修飾子
            if (type.contains("static")) {
                modifiers.add(CppClass.Modifier.STATIC);
                processedType = processedType.replaceAll("static", "").trim();
            }
            // const修飾子
            if (type.contains("const")) {
                modifiers.add(CppClass.Modifier.CONST);
                processedType = processedType.replaceAll("const", "").trim();
            }
            // override修飾子
            if (type.contains("override")) {
                modifiers.add(CppClass.Modifier.OVERRIDE);
                processedType = processedType.replaceAll("override", "").trim();
            }

            Operation operation = new Operation(new Name(methodName));
            operation.setReturnType(new Type(isDestructor ? "void" : processedType));
            operation.setVisibility(convertVisibility(currentVisibility));

            processParameters(declarator, operation);

            currentClass.addOperation(operation);
            for (CppClass.Modifier modifier : modifiers) {
                currentClass.addMemberModifier(methodName, modifier);
            }

            System.out.println("DEBUG: Added method: " + methodName
                    + " type: " + processedType
                    + " modifiers: " + modifiers);
        } catch (Exception e) {
            System.err.println("Error in handleMethod: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void analyzeTypeForRelationship(String type, String declaratorText) {
        String cleanType = cleanTypeName(type);
        if (!isUserDefinedType(cleanType)) {
            return;
        }

        // ポインタ/参照による依存関係
        if (type.contains("*") || type.contains("&") ||
                declaratorText.contains("*") || declaratorText.contains("&")) {
            ((CppClass) currentClass).addDependency(cleanType);
            System.out.println("DEBUG: Added dependency: " + cleanType + " (from parameter)");
            return;
        }

        // コンテナ型の処理
        if (isCollectionType(type)) {
            String elementType = extractElementType(type);
            if (isUserDefinedType(elementType)) {
                ((CppClass) currentClass).addComposition(elementType);
                ((CppClass) currentClass).setMultiplicity(elementType, "*");
                System.out.println("DEBUG: Added collection relationship: " + elementType);
            }
            return;
        }

        // 配列の処理
        if (declaratorText.matches(".*\\[\\d+\\]")) {
            String size = declaratorText.replaceAll(".*\\[(\\d+)\\].*", "$1");
            ((CppClass) currentClass).addComposition(cleanType);
            ((CppClass) currentClass).setMultiplicity(cleanType, size);
            System.out.println("DEBUG: Added array relationship: " + cleanType + "[" + size + "]");
            return;
        }

        // 通常の型
        ((CppClass) currentClass).addComposition(cleanType);
        ((CppClass) currentClass).setMultiplicity(cleanType, "1");
        System.out.println("DEBUG: Added composition: " + cleanType);
    }

    private void processParameters(CPP14Parser.DeclaratorContext declarator, Operation operation) {
        if (declarator.parametersAndQualifiers() != null &&
                declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {
            for (CPP14Parser.ParameterDeclarationContext paramCtx : declarator.parametersAndQualifiers()
                    .parameterDeclarationClause()
                    .parameterDeclarationList().parameterDeclaration()) {
                String paramType = paramCtx.declSpecifierSeq().getText();
                String paramDeclText = paramCtx.declarator() != null ? paramCtx.declarator().getText() : "";

                // パラメータ型からの関係を抽出
                analyzeTypeRelationship(paramType, paramDeclText);
                analyzeParameterType(paramType, paramCtx.declarator());

                // パラメータを型のみで追加
                Parameter param = new Parameter(new Name(""));
                param.setType(new Type(processParamType(paramType, paramCtx.declarator())));
                operation.addParameter(param);
            }
        }
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

        String declaratorText = declarator.getText();
        if (declaratorText.contains("*") || type.contains("*") ||
                declaratorText.contains("&") || type.contains("&")) {
            ((CppClass) currentClass).addDependency(cleanType);
            System.out.println("DEBUG: Added parameter dependency: " + cleanType);
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

    private void handleAttribute(CPP14Parser.DeclaratorContext declarator, String type) {
        try {
            String attributeName = extractAttributeName(declarator).replaceAll("[*&]", "").trim();
            if (currentClass.getAttributeList().stream()
                    .anyMatch(attr -> attr.getName().getNameText().equals(attributeName))) {
                return;
            }

            // 修飾子の収集
            Set<CppClass.Modifier> modifiers = new LinkedHashSet<>();
            String processedType = type;

            // static修飾子
            if (type.contains("static")) {
                modifiers.add(CppClass.Modifier.STATIC);
                processedType = processedType.replaceAll("static", "").trim();
            }
            // const修飾子
            if (type.contains("const")) {
                modifiers.add(CppClass.Modifier.CONST);
                processedType = processedType.replaceAll("const", "").trim();
            }
            // mutable修飾子
            if (type.contains("mutable")) {
                modifiers.add(CppClass.Modifier.MUTABLE);
                processedType = processedType.replaceAll("mutable", "").trim();
            }

            // 型の処理
            processedType = processAttributeType(processedType, declarator);

            Attribute attribute = new Attribute(new Name(attributeName));
            attribute.setType(new Type(processedType));
            attribute.setVisibility(convertVisibility(currentVisibility));

            currentClass.addAttribute(attribute);
            for (CppClass.Modifier modifier : modifiers) {
                currentClass.addMemberModifier(attributeName, modifier);
            }

            // 関係の解析
            analyzeAttributeRelationships(type, declarator);

            System.out.println("DEBUG: Added attribute: " + attributeName
                    + " type: " + processedType
                    + " modifiers: " + modifiers);
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
        // コレクション型の処理
        if (isCollectionType(type)) {
            String elementType = extractElementType(type);
            if (isUserDefinedType(cleanTypeName(elementType))) {
                currentClass.addRelationship(cleanTypeName(elementType),
                        RelationshipInfo.RelationType.COMPOSITION, "*");
                System.out.println("DEBUG: Added collection relationship: " + elementType);
            }
        }

        String cleanType = cleanTypeName(type);
        if (!isUserDefinedType(cleanType)) {
            return;
        }

        // 配列の処理
        String declaratorText = declarator.getText();
        if (declaratorText.matches(".*\\[\\d+\\]")) {
            String size = declaratorText.replaceAll(".*\\[(\\d+)\\].*", "$1");
            currentClass.addRelationship(cleanType,
                    RelationshipInfo.RelationType.COMPOSITION, size);
            System.out.println("DEBUG: Added array relationship: " + cleanType);
            return;
        }

        // ポインタ/参照の処理
        if (type.contains("*") || type.contains("&") ||
                declaratorText.contains("*") || declaratorText.contains("&")) {
            currentClass.addRelationship(cleanType,
                    RelationshipInfo.RelationType.DEPENDENCY, "1");
            System.out.println("DEBUG: Added dependency: " + cleanType);
            return;
        }

        // 通常のインスタンス
        currentClass.addRelationship(cleanType,
                RelationshipInfo.RelationType.COMPOSITION, "1");
        System.out.println("DEBUG: Added composition: " + cleanType);
    }

    private String processAttributeType(String type, CPP14Parser.DeclaratorContext declarator) {
        StringBuilder processedType = new StringBuilder();
        Set<String> modifiers = new LinkedHashSet<>();

        // 修飾子の処理
        if (type.contains("static")) {
            modifiers.add("static");
        }
        if (type.contains("const")) {
            modifiers.add("const");
        }
        if (type.contains("mutable")) {
            modifiers.add("mutable");
        }

        // 基本型（修飾子を除去）
        String baseType = type;
        for (String modifier : modifiers) {
            baseType = baseType.replaceAll(modifier, "").trim();
        }
        baseType = baseType.replaceAll("std::", "").trim();

        // 修飾子を型の前に追加
        if (!modifiers.isEmpty()) {
            processedType.append(String.join(" ", modifiers)).append(" ");
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

        return processedType.toString();
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
}