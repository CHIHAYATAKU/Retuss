package io.github.morichan.retuss.translator.cpp.listeners;

import java.util.*;

import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.retuss.translator.model.MethodCall;

public class CppMethodAnalyzer extends CPP14ParserBaseListener {
    private final List<MethodCall> methodCalls = new ArrayList<>();
    private String currentClass;
    private String currentMethod;
    private int nestingLevel = 0;

    @Override
    public void enterFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
        // メソッド定義の開始を検出
        if (ctx.declarator() != null) {
            String methodSignature = ctx.declarator().getText();
            // クラス名とメソッド名を分離 (例: MyClass::myMethod)
            if (methodSignature.contains("::")) {
                String[] parts = methodSignature.split("::");
                currentClass = parts[0];
                currentMethod = parts[1].substring(0, parts[1].indexOf('('));
            }
            nestingLevel = 0;
        }
    }

    @Override
    public void exitFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
        currentClass = null;
        currentMethod = null;
    }

    @Override
    public void enterPostfixExpression(CPP14Parser.PostfixExpressionContext ctx) {
        // メソッド呼び出しを検出
        if (ctx.LeftParen() != null && currentMethod != null) {
            String caller = currentClass;
            String callee = "";
            String methodName = "";
            List<String> arguments = new ArrayList<>();

            // 呼び出し先の解析
            if (ctx.postfixExpression() != null) {
                String expression = ctx.postfixExpression().getText();
                if (expression.contains(".")) {
                    String[] parts = expression.split("\\.");
                    callee = parts[0];
                    methodName = parts[1];
                } else if (expression.contains("->")) {
                    String[] parts = expression.split("->");
                    callee = parts[0];
                    methodName = parts[1];
                } else {
                    // 同じクラス内のメソッド呼び出し
                    callee = currentClass;
                    methodName = expression;
                }
            }

            // 引数の解析
            if (ctx.expressionList() != null) {
                CPP14Parser.ExpressionListContext exprList = ctx.expressionList();
                // initializerListを使用
                if (exprList.initializerList() != null) {
                    for (CPP14Parser.InitializerClauseContext arg : exprList.initializerList().initializerClause()) {
                        arguments.add(arg.getText());
                    }
                }
            }

            methodCalls.add(new MethodCall(caller, callee, methodName, arguments, nestingLevel));
        }
    }

    @Override
    public void enterSelectionStatement(CPP14Parser.SelectionStatementContext ctx) {
        // if文の処理
        nestingLevel++;
    }

    @Override
    public void exitSelectionStatement(CPP14Parser.SelectionStatementContext ctx) {
        nestingLevel--;
    }

    @Override
    public void enterIterationStatement(CPP14Parser.IterationStatementContext ctx) {
        // for, whileループの処理
        nestingLevel++;
    }

    @Override
    public void exitIterationStatement(CPP14Parser.IterationStatementContext ctx) {
        nestingLevel--;
    }

    public List<MethodCall> getMethodCalls() {
        return methodCalls;
    }
}