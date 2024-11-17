package io.github.morichan.retuss.translator.cpp.analyzers.member;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.analyzers.base.AbstractAnalyzer;
import org.antlr.v4.runtime.ParserRuleContext;
import java.util.*;

public class OperationAnalyzer extends AbstractAnalyzer {
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        if (!(context instanceof CPP14Parser.MemberdeclarationContext)) {
            return false;
        }
        CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
        return ctx.declSpecifierSeq() != null &&
                isMethodDeclaration(ctx.memberDeclaratorList());
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
        CppClass currentClass = this.context.getCurrentClass();

        if (currentClass == null || ctx.declSpecifierSeq() == null) {
            return;
        }

        try {
            // 型と修飾子の解析
            String rawType = ctx.declSpecifierSeq().getText();
            Set<CppClass.Modifier> modifiers = extractModifiers(rawType);
            String processedType = cleanType(rawType);

            if (ctx.memberDeclaratorList() != null) {
                for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                    handleOperation(memberDec, processedType, modifiers);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in operation analysis: " + e.getMessage());
        }
    }

    private void handleOperation(
            CPP14Parser.MemberDeclaratorContext memberDec,
            String returnType,
            Set<CppClass.Modifier> modifiers) {

        CppClass currentClass = context.getCurrentClass();
        String methodName = extractMethodName(memberDec.declarator());

        if (currentClass.getOperationList().stream()
                .anyMatch(op -> op.getName().getNameText().equals(methodName))) {
            return;
        }

        Operation operation = new Operation(new Name(methodName));
        operation.setReturnType(new Type(returnType));
        operation.setVisibility(convertVisibility(context.getCurrentVisibility()));

        // パラメータの処理
        processParameters(memberDec.declarator(), operation);

        currentClass.addOperation(operation);
        for (CppClass.Modifier modifier : modifiers) {
            currentClass.addMemberModifier(methodName, modifier);
        }

        // 戻り値の型の関係解析
        if (!returnType.equals("void")) {
            analyzeTypeRelationship(returnType);
        }
    }

    private Set<CppClass.Modifier> extractModifiers(String type) {
        Set<CppClass.Modifier> modifiers = EnumSet.noneOf(CppClass.Modifier.class);

        if (type.contains("virtual"))
            modifiers.add(CppClass.Modifier.VIRTUAL);
        if (type.contains("static"))
            modifiers.add(CppClass.Modifier.STATIC);
        if (type.contains("const"))
            modifiers.add(CppClass.Modifier.CONST);
        if (type.contains("override"))
            modifiers.add(CppClass.Modifier.OVERRIDE);

        return modifiers;
    }

    private void processParameters(
            CPP14Parser.DeclaratorContext declarator,
            Operation operation) {

        if (declarator.parametersAndQualifiers() != null &&
                declarator.parametersAndQualifiers().parameterDeclarationClause() != null) {

            for (CPP14Parser.ParameterDeclarationContext paramCtx : declarator.parametersAndQualifiers()
                    .parameterDeclarationClause()
                    .parameterDeclarationList()
                    .parameterDeclaration()) {

                String paramType = paramCtx.declSpecifierSeq().getText();
                String paramName = paramCtx.declarator() != null ? paramCtx.declarator().getText() : "";

                Parameter param = new Parameter(new Name(paramName));
                param.setType(new Type(processParamType(paramType, paramCtx.declarator())));
                operation.addParameter(param);

                // パラメータ型の関係解析
                analyzeTypeRelationship(paramType);
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

    private boolean isMethodDeclaration(CPP14Parser.MemberDeclaratorListContext memberDecList) {
        if (memberDecList == null)
            return false;

        for (CPP14Parser.MemberDeclaratorContext memberDec : memberDecList.memberDeclarator()) {
            if (memberDec.declarator() == null)
                continue;

            // 1. 基本的なメソッド判定
            if (memberDec.declarator().parametersAndQualifiers() != null) {
                return true;
            }

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
        }
        return false;
    }

    private String extractMethodName(CPP14Parser.DeclaratorContext declarator) {
        String fullText = declarator.getText();
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

    private String cleanType(String type) {
        return type.replaceAll("(virtual|static|const|override)", "").trim();
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

    private void analyzeTypeRelationship(String type) {
        String cleanType = cleanTypeName(type);
        if (isUserDefinedType(cleanType)) {
            context.getCurrentClass().addDependency(cleanType);
        }
    }

    private String cleanTypeName(String typeName) {
        return typeName.replaceAll("[*&]", "")
                .replaceAll("\\s+", "")
                .replaceAll("const", "")
                .replaceAll("std::", "")
                .trim();
    }

    private boolean isUserDefinedType(String type) {
        Set<String> basicTypes = Set.of(
                "void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed");
        return !basicTypes.contains(type) &&
                !type.startsWith("std::") &&
                Character.isUpperCase(type.charAt(0));
    }
}