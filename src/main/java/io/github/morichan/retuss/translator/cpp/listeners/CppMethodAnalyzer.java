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
        System.out.println("Processing member with type: " + type);

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
            handleAttribute(declarator, type, memberDec);
        }
    }

    private void handleMethod(CPP14Parser.DeclaratorContext declarator, String type) {
        String methodName = extractMethodName(declarator.getText());
        Operation operation = new Operation(new Name(methodName));
        operation.setReturnType(new Type(type));
        operation.setVisibility(convertVisibility(currentVisibility));

        // パラメータの解析
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
        System.out.println("Added method: " + methodName);
    }

    private void handleAttribute(
            CPP14Parser.DeclaratorContext declarator,
            String type,
            CPP14Parser.MemberDeclaratorContext memberDec) {
        String attributeName = declarator.getText();
        Attribute attribute = new Attribute(new Name(attributeName));
        attribute.setType(new Type(type));
        attribute.setVisibility(convertVisibility(currentVisibility));

        // 初期値の設定
        if (memberDec.braceOrEqualInitializer() != null) {
            String initValue = memberDec.braceOrEqualInitializer().getText().substring(1);
            attribute.setDefaultValue(new DefaultValue(
                    new OneIdentifier(initValue)));
        }

        currentClass.addAttribute(attribute);
        System.out.println("Added attribute: " + attributeName);
    }

    @Override
    public void enterFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
        String methodName = extractMethodName(ctx.declarator().getText());
        currentMethod = findMethodInClass(methodName);
        nestingLevel = 0;
        System.out.println("Analyzing method: " + methodName);
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

        String callerName = currentClass.getName();
        String calleeName = extractCalleeName(ctx);
        String methodName = extractMethodName(ctx);
        List<String> arguments = extractArguments(ctx);

        OccurenceSpecification occurence = createMethodCallOccurence(
                callerName, calleeName, methodName, arguments);

        if (!operandStack.isEmpty()) {
            operandStack.peek().getInteractionFragmentList().add(occurence);
        } else {
            currentInteraction.getInteractionFragmentList().add(occurence);
        }
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
        if (expr.contains("->")) {
            return expr.split("->")[1];
        } else if (expr.contains(".")) {
            return expr.split("\\.")[1];
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

    private boolean isVirtualMethod(CPP14Parser.MemberDeclaratorContext ctx) {
        // virtual指定子のチェック
        if (ctx.getParent() instanceof CPP14Parser.MemberdeclarationContext) {
            CPP14Parser.MemberdeclarationContext memberDeclaration = (CPP14Parser.MemberdeclarationContext) ctx
                    .getParent();
            if (memberDeclaration.declSpecifierSeq() != null) {
                return memberDeclaration.declSpecifierSeq().getText().contains("virtual");
            }
        }
        return false;
    }

    public List<MethodCall> getMethodCalls() {
        return methodCalls;
    }

    public Set<StandardLifeline> getUsedLifelines() {
        return usedLifelines;
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
        if (ctx.expressionList() != null) {
            ctx.expressionList().initializerList().initializerClause()
                    .forEach(arg -> args.add(arg.getText()));
        }
        return args;
    }

    private OccurenceSpecification createMethodCallOccurence(
            String caller,
            String callee,
            String methodName,
            List<String> args) {
        Lifeline callerLifeline = new Lifeline("", caller);
        Lifeline calleeLifeline = new Lifeline("", callee);

        OccurenceSpecification messageStart = new OccurenceSpecification(callerLifeline);
        OccurenceSpecification messageEnd = new OccurenceSpecification(calleeLifeline);

        Message message = new Message(methodName, messageEnd);
        message.setMessageSort(MessageSort.synchCall);

        for (String arg : args) {
            Parameter param = new Parameter(new Name(arg));
            message.getParameterList().add(param);
        }

        messageStart.setMessage(message);
        return messageStart;
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