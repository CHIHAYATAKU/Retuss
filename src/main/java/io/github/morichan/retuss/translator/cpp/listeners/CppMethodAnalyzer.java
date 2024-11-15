package io.github.morichan.retuss.translator.cpp.listeners;

import io.github.morichan.retuss.model.uml.*;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.parser.cpp.CPP14ParserBaseListener;
import io.github.morichan.retuss.translator.model.MethodCall;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;

public class CppMethodAnalyzer extends CPP14ParserBaseListener {
    public enum StandardLifeline {
        COUT("std::cout", "ConsoleOutput"),
        CIN("std::cin", "ConsoleInput"),
        PRINTF("printf", "COutput");

        private final String identifier;
        private final String displayName;

        StandardLifeline(String identifier, String displayName) {
            this.identifier = identifier;
            this.displayName = displayName;
        }

        public String getIdentifier() {
            return identifier;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static StandardLifeline fromCode(String code) {
            if (code.contains("cout"))
                return COUT;
            if (code.contains("cin"))
                return CIN;
            if (code.contains("printf"))
                return PRINTF;
            return null;
        }
    }

    private final io.github.morichan.retuss.model.uml.Class currentClass; // 明示的に指定
    private Operation currentMethod;
    private List<MethodCall> methodCalls = new ArrayList<>();
    private Set<StandardLifeline> usedLifelines = new HashSet<>();
    private final Set<String> analyzedTypes = new HashSet<>();
    private String currentVisibility = "private";
    private Interaction currentInteraction;
    private Stack<InteractionOperand> operandStack = new Stack<>();
    private int nestingLevel = 0;

    public CppMethodAnalyzer(io.github.morichan.retuss.model.uml.Class currentClass) { // 明示的に指定
        this.currentClass = currentClass;
    }

    @Override
    public void enterClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
        System.out.println("Entering class: " + ctx.classHead().classHeadName().getText());
    }

    @Override
    public void enterMemberdeclaration(CPP14Parser.MemberdeclarationContext ctx) {
        if (ctx.declSpecifierSeq() == null)
            return;

        String type = ctx.declSpecifierSeq().getText();
        System.out.println("DEBUG: Analyzing member type: " + type);

        if (ctx.memberDeclaratorList() != null) {
            for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                if (memberDec.declarator() != null) {
                    handleMemberDeclarator(memberDec, type);
                }
            }
        }
    }

    private void analyzeTypeRelationship(String type, boolean isPointerOrRef) {
        if (!isUserDefinedType(cleanTypeName(type))) {
            return;
        }

        CppClass cppClass = (CppClass) currentClass;
        String cleanType = cleanTypeName(type);
        System.out.println("DEBUG: Analyzing relationship for type: " + type +
                " (cleaned: " + cleanType + ", isPointerOrRef: " + isPointerOrRef + ")");

        if (isPointerOrRef) {
            System.out.println("DEBUG: Adding dependency: " + cleanType);
            cppClass.addDependency(cleanType);
        } else if (isCollectionType(type)) {
            System.out.println("DEBUG: Adding collection composition: " + cleanType);
            String elementType = extractElementType(type);
            if (isUserDefinedType(elementType)) {
                cppClass.addComposition(elementType);
                cppClass.setMultiplicity(elementType, "*");
            }
        } else if (isArray(type)) {
            System.out.println("DEBUG: Adding array composition: " + cleanType);
            String baseType = extractArrayBaseType(type);
            String size = extractArraySize(type);
            if (isUserDefinedType(baseType)) {
                cppClass.addComposition(baseType);
                cppClass.setMultiplicity(baseType, size);
            }
        } else {
            System.out.println("DEBUG: Adding normal composition: " + cleanType);
            cppClass.addComposition(cleanType);
            cppClass.setMultiplicity(cleanType, "1");
        }
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

    private void analyzeAttributeRelationships(Attribute attr) {
        String type = attr.getType().toString();
        String cleanType = cleanTypeName(type);

        if (!isUserDefinedType(cleanType)) {
            return;
        }

        System.out.println("DEBUG: Analyzing attribute relationship: " + attr.getName() + " : " + type);

        // ポインタ/参照による依存関係
        if (type.contains("*") || type.contains("&")) {
            ((CppClass) currentClass).addDependency(cleanType);
            System.out.println("  Adding dependency to " + cleanType);
            return;
        }

        // コレクション型の関係
        if (type.contains("vector<") || type.contains("list<")) {
            String elementType = extractElementType(type);
            if (isUserDefinedType(elementType)) {
                ((CppClass) currentClass).addComposition(elementType);
                ((CppClass) currentClass).setMultiplicity(elementType, "*");
                System.out.println("  Adding collection composition to " + elementType);
            }
            return;
        }

        // 配列の関係
        if (type.matches(".*\\[\\d+\\]")) {
            String baseType = type.replaceAll("\\[\\d+\\]", "");
            String size = type.replaceAll(".*\\[(\\d+)\\].*", "$1");
            if (isUserDefinedType(baseType)) {
                ((CppClass) currentClass).addComposition(baseType);
                ((CppClass) currentClass).setMultiplicity(baseType, size);
                System.out.println("  Adding array composition to " + baseType + " with size " + size);
            }
            return;
        }

        // 通常のインスタンスによるコンポジション関係
        ((CppClass) currentClass).addComposition(cleanType);
        ((CppClass) currentClass).setMultiplicity(cleanType, "1");
        System.out.println("  Adding normal composition to " + cleanType);
    }

    // 型解析用のヘルパーメソッド群（新規追加）
    private boolean isPointerOrReference(String type) {
        return type.contains("*") || type.contains("&");
    }

    private boolean isCollectionType(String type) {
        return type.contains("vector<") ||
                type.contains("array<") ||
                type.contains("list<") ||
                type.contains("set<");
    }

    private boolean isArray(String type) {
        return type.matches(".*\\[\\d*\\]");
    }

    private String cleanTypeName(String type) {
        return type.replaceAll("[*&\\[\\]]", "")
                .replaceAll("const", "")
                .replaceAll("volatile", "")
                .replaceAll("std::", "")
                .replaceAll("<.*>", "")
                .trim();
    }

    private String extractElementType(String type) {
        if (type.contains("<")) {
            return cleanTypeName(type.replaceAll(".*<(.+)>.*", "$1"));
        }
        return type;
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

    private void handleMemberDeclarator(CPP14Parser.MemberDeclaratorContext memberDec, String type) {
        if (memberDec.declarator() == null)
            return;

        CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
        boolean isMethod = declarator.parametersAndQualifiers() != null;

        if (isMethod) {
            handleMethod(declarator, type);
        } else {
            handleAttribute(declarator, type, memberDec);
        }
    }

    private void handleMethod(CPP14Parser.DeclaratorContext declarator, String type) {
        String methodName = extractMethodName(declarator.getText());
        Operation operation = new Operation(new Name(methodName));
        operation.setReturnType(new Type(type));
        operation.setVisibility(convertVisibility(currentVisibility));

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
        System.out.println("DEBUG: Added method: " + methodName + " with return type: " + type);
    }

    private void handleAttribute(CPP14Parser.DeclaratorContext declarator, String type,
            CPP14Parser.MemberDeclaratorContext memberDec) {
        String attributeName = declarator.getText();
        Attribute attribute = new Attribute(new Name(attributeName));
        attribute.setType(new Type(type));
        attribute.setVisibility(convertVisibility(currentVisibility));

        // 初期値の設定
        if (memberDec.braceOrEqualInitializer() != null) {
            String initValue = memberDec.braceOrEqualInitializer().getText().substring(1);
            attribute.setDefaultValue(new DefaultValue(new OneIdentifier(initValue)));
        }

        // 関係の抽出（属性追加前に行う）
        analyzeAttributeType(type, declarator.getText());

        currentClass.addAttribute(attribute);
        System.out.println("Added attribute: " + attributeName + " with type: " + type);
    }

    private void analyzeAttributeType(String type, String declarator) {
        CppClass cppClass = (CppClass) currentClass;
        String cleanType = cleanTypeName(type);

        if (!isUserDefinedType(cleanType)) {
            return;
        }

        System.out.println("DEBUG: Analyzing type relationship for: " + type);

        // ポインタまたは参照による依存関係
        if (type.contains("*") || type.contains("&") || declarator.contains("*") || declarator.contains("&")) {
            System.out.println("DEBUG: Adding dependency: " + cleanType);
            cppClass.addDependency(cleanType);
            return;
        }

        // コレクション型の処理
        if (type.contains("vector<") || type.contains("list<")) {
            String elementType = extractElementType(type);
            if (isUserDefinedType(elementType)) {
                System.out.println("DEBUG: Adding collection composition: " + elementType);
                cppClass.addComposition(elementType);
                cppClass.setMultiplicity(elementType, "*");
            }
            return;
        }

        // 配列の処理
        if (declarator.matches(".*\\[\\d+\\]")) {
            String size = declarator.replaceAll(".*\\[(\\d+)\\].*", "$1");
            System.out.println("DEBUG: Adding array composition: " + cleanType + " with size " + size);
            cppClass.addComposition(cleanType);
            cppClass.setMultiplicity(cleanType, size);
            return;
        }

        // 通常のインスタンスメンバ（コンポジション）
        System.out.println("DEBUG: Adding composition: " + cleanType);
        cppClass.addComposition(cleanType);
        cppClass.setMultiplicity(cleanType, "1");
    }

    @Override
    public void enterFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
        String methodName = extractMethodName(ctx.declarator().getText());
        currentMethod = findMethodInClass(methodName);
        if (currentMethod != null) {
            System.out.println("Analyzing method: " + methodName);
            nestingLevel = 0;
        }
    }

    @Override
    public void exitFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
        if (currentMethod != null && currentInteraction != null) {
            currentClass.addOperation(currentMethod, currentInteraction);
            System.out.println("Completed method analysis: " + currentMethod.getName());
        }
        currentMethod = null;
        currentInteraction = null;
    }

    @Override
    public void enterCompoundStatement(CPP14Parser.CompoundStatementContext ctx) {
        nestingLevel++;
    }

    @Override
    public void exitCompoundStatement(CPP14Parser.CompoundStatementContext ctx) {
        nestingLevel--;
    }

    @Override
    public void enterSelectionStatement(CPP14Parser.SelectionStatementContext ctx) {
        if (currentInteraction == null)
            return;

        String condition = ctx.condition().getText();
        Lifeline lifeline = new Lifeline("", currentClass.getName());

        CombinedFragment fragment;
        if (ctx.Else() != null) {
            fragment = new CombinedFragment(lifeline, InteractionOperandKind.alt);
        } else {
            fragment = new CombinedFragment(lifeline, InteractionOperandKind.opt);
        }

        InteractionOperand operand = new InteractionOperand(lifeline, condition);
        operandStack.push(operand);
        fragment.getInteractionOperandList().add(operand);
        currentInteraction.getInteractionFragmentList().add(fragment);
    }

    @Override
    public void enterStatement(CPP14Parser.StatementContext ctx) {
        if (currentMethod == null)
            return;

        String statementText = ctx.getText();
        StandardLifeline lifeline = StandardLifeline.fromCode(statementText);

        if (lifeline != null) {
            usedLifelines.add(lifeline);
            handleStandardOperation(ctx, lifeline);
        }
    }

    @Override
    public void enterIterationStatement(CPP14Parser.IterationStatementContext ctx) {
        if (currentInteraction == null)
            return;

        Lifeline lifeline = new Lifeline("", currentClass.getName());
        CombinedFragment fragment = new CombinedFragment(lifeline, InteractionOperandKind.loop);

        String condition;
        if (ctx.For() != null && ctx.condition() != null) {
            condition = ctx.condition().getText();
        } else if (ctx.While() != null && ctx.condition() != null) {
            condition = ctx.condition().getText();
        } else {
            condition = "loop";
        }

        InteractionOperand operand = new InteractionOperand(lifeline, condition);
        operandStack.push(operand);
        fragment.getInteractionOperandList().add(operand);
        currentInteraction.getInteractionFragmentList().add(fragment);
    }

    @Override
    public void exitIterationStatement(CPP14Parser.IterationStatementContext ctx) {
        if (!operandStack.isEmpty()) {
            operandStack.pop();
        }
    }

    @Override
    public void enterPostfixExpression(CPP14Parser.PostfixExpressionContext ctx) {
        if (currentInteraction == null || !isMethodCall(ctx))
            return;

        String callerName = extractCallerName(ctx);
        String calleeName = extractCalleeName(ctx);
        String methodName = extractMethodName(ctx);
        List<String> arguments = extractArguments(ctx);

        System.out.println("Found method call: " + methodName);
        System.out.println("  Caller: " + callerName);
        System.out.println("  Arguments: " + String.join(", ", arguments));

        // MethodCallオブジェクトを作成
        MethodCall call = new MethodCall();
        call.setCaller(callerName);
        call.setCallee(calleeName);
        call.setMethodName(methodName);
        call.getArguments().addAll(arguments);
        call.setNestingLevel(nestingLevel);

        methodCalls.add(call);
    }

    private void handleStandardOperation(CPP14Parser.StatementContext ctx, StandardLifeline lifeline) {
        String caller = currentClass.getName();
        String callee = lifeline.getDisplayName();
        String methodName;
        List<String> arguments;

        switch (lifeline) {
            case COUT:
                methodName = "output";
                arguments = List.of(extractStreamContent(ctx));
                break;
            case CIN:
                methodName = "input";
                arguments = List.of(extractCinTarget(ctx));
                break;
            case PRINTF:
                methodName = "printf";
                arguments = extractPrintfArguments(ctx);
                break;
            default:
                throw new IllegalArgumentException("Unsupported lifeline type: " + lifeline);
        }

        // MethodCall オブジェクトを生成
        MethodCall call = new MethodCall(caller, callee, methodName, arguments, nestingLevel);
        methodCalls.add(call);
    }

    private String extractStreamContent(CPP14Parser.StatementContext ctx) {
        String text = ctx.getText();
        StringBuilder content = new StringBuilder();
        String[] parts = text.split("<<");

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.equals("endl;") && !part.equals("endl")) {
                if (part.startsWith("\"") && part.endsWith("\"")) {
                    content.append(part.substring(1, part.length() - 1));
                } else {
                    content.append(part);
                }
                content.append(" ");
            }
        }

        return content.toString().trim();
    }

    private String extractCinTarget(CPP14Parser.StatementContext ctx) {
        String text = ctx.getText();
        String[] parts = text.split(">>");
        if (parts.length > 1) {
            return parts[1].trim().replace(";", "");
        }
        return "";
    }

    private List<String> extractPrintfArguments(CPP14Parser.StatementContext ctx) {
        List<String> args = new ArrayList<>();
        // printf の引数解析（必要に応じて実装）
        return args;
    }

    private Operation findMethodInClass(String methodName) {
        return currentClass.getOperationList().stream()
                .filter(op -> op.getName().getNameText().equals(methodName))
                .findFirst()
                .orElse(null);
    }

    private boolean isMethodCall(CPP14Parser.PostfixExpressionContext ctx) {
        return ctx.LeftParen() != null;
    }

    private String extractMethodName(CPP14Parser.PostfixExpressionContext ctx) {
        String expr = ctx.postfixExpression().getText();
        if (expr.contains("::")) {
            return expr.substring(expr.lastIndexOf("::") + 2);
        }
        return expr;
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

    public List<MethodCall> getMethodCalls() {
        return methodCalls;
    }

    public Set<StandardLifeline> getUsedLifelines() {
        return usedLifelines;
    }

    private String extractCallerName(CPP14Parser.PostfixExpressionContext ctx) {
        String expr = ctx.postfixExpression().getText();
        if (expr.contains("::")) {
            return expr.substring(0, expr.lastIndexOf("::"));
        }
        return currentClass.getName(); // デフォルトは現在のクラス
    }

    private String extractCalleeName(CPP14Parser.PostfixExpressionContext ctx) {
        String expr = ctx.postfixExpression().getText();
        if (expr.contains("->")) {
            return expr.split("->")[0];
        } else if (expr.contains(".")) {
            return expr.split("\\.")[0];
        }
        return currentClass.getName();
    }

    private List<String> extractArguments(CPP14Parser.PostfixExpressionContext ctx) {
        List<String> args = new ArrayList<>();
        if (ctx.expressionList() != null &&
                ctx.expressionList().initializerList() != null) {
            for (CPP14Parser.InitializerClauseContext arg : ctx.expressionList().initializerList()
                    .initializerClause()) {
                args.add(arg.getText());
            }
        }
        return args;
    }

    private Visibility convertVisibility(String visibility) {
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

    @Override
    public void enterAccessSpecifier(CPP14Parser.AccessSpecifierContext ctx) {
        currentVisibility = ctx.getText().toLowerCase();
        System.out.println("Visibility changed to: " + currentVisibility);
    }
}