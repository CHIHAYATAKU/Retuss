package io.github.morichan.retuss.translator.cpp.analyzers.member;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.model.uml.CppClass.RelationshipInfo;
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
                (ctx.memberDeclaratorList() != null && isMethodDeclaration(ctx.memberDeclaratorList()));
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        try {
            CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
            CppClass currentClass = this.context.getCurrentClass();

            if (currentClass == null || ctx.declSpecifierSeq() == null) {
                return;
            }

            // メソッドの解析を実行
            String rawType = ctx.declSpecifierSeq().getText();
            Set<CppClass.Modifier> modifiers = extractModifiers(rawType);
            String processedType = cleanType(rawType);

            // メソッドの宣言を処理
            if (ctx.memberDeclaratorList() != null) {
                for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                    if (memberDec != null && memberDec.declarator() != null) {
                        handleOperation(memberDec, processedType, modifiers);
                        // デバッグ出力を追加
                        System.out.println("DEBUG: Found method: " +
                                extractMethodName(memberDec.declarator()) +
                                " with return type: " + processedType);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in operation analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleOperation(
            CPP14Parser.MemberDeclaratorContext memberDec,
            String returnType,
            Set<CppClass.Modifier> modifiers) {

        CppClass currentClass = context.getCurrentClass();
        String methodName = extractMethodName(memberDec.declarator());

        // コンストラクタ/デストラクタの処理
        if (methodName.equals(currentClass.getName()) || methodName.equals("~" + currentClass.getName())) {
            returnType = ""; // コンストラクタ/デストラクタは戻り値型を表示しない
        }

        Operation operation = new Operation(new Name(methodName));
        operation.setReturnType(new Type(returnType));
        operation.setVisibility(convertVisibility(context.getCurrentVisibility()));

        // パラメータの処理
        if (memberDec.declarator().parametersAndQualifiers() != null) {
            processParameters(
                    memberDec.declarator().parametersAndQualifiers(),
                    operation);
        }

        // 純粋仮想関数の判定
        if (memberDec.declarator().parametersAndQualifiers() != null &&
                memberDec.declarator().parametersAndQualifiers().getText().contains("= 0")) {
            modifiers.add(CppClass.Modifier.PURE_VIRTUAL);
        }

        // デストラクタは自動的にvirtual
        if (methodName.startsWith("~")) {
            modifiers.add(CppClass.Modifier.VIRTUAL);
        }

        currentClass.addOperation(operation);

        // 修飾子を追加
        for (CppClass.Modifier modifier : modifiers) {
            currentClass.addMemberModifier(methodName, modifier);
        }

        System.out.println("DEBUG: Added operation: " + methodName +
                " with modifiers: " + modifiers);
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

    private void processParameters(
            CPP14Parser.ParametersAndQualifiersContext params,
            Operation operation) {

        // パラメータが存在する場合
        if (params != null && params.parameterDeclarationClause() != null) {

            for (CPP14Parser.ParameterDeclarationContext paramCtx : params
                    .parameterDeclarationClause()
                    .parameterDeclarationList()
                    .parameterDeclaration()) {

                String rawParamType = paramCtx.declSpecifierSeq().getText();
                String paramName = paramCtx.declarator() != null ? cleanName(paramCtx.declarator().getText()) : "";

                // パラメータの型を解析
                String paramType = processParamType(rawParamType, paramCtx.declarator());

                // Parameterオブジェクトを作成
                Parameter param = new Parameter(new Name(paramName));
                param.setType(new Type(paramType));
                operation.addParameter(param);

                // ユーザー定義型の場合は依存関係を追加
                String baseType = cleanTypeName(rawParamType);
                if (isUserDefinedType(baseType)) {
                    RelationshipInfo relation = new RelationshipInfo(
                            baseType,
                            RelationshipInfo.RelationType.DEPENDENCY);
                    relation.addElement(
                            operation.getName().getNameText(),
                            RelationshipInfo.ElementType.PARAMETER,
                            "1",
                            Visibility.Public);
                    context.getCurrentClass().addRelationship(relation);
                }
            }
        }

        // 純粋仮想関数の処理
        if (params != null && params.getText().contains("= 0")) {
            context.getCurrentClass().addMemberModifier(
                    operation.getName().getNameText(),
                    CppClass.Modifier.PURE_VIRTUAL);
        }
    }

    private String cleanName(String name) {
        return name.replaceAll("[*&]", "").trim();
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
        if (declarator != null) {
            String declaratorText = declarator.getText();
            if (declaratorText.contains("*") || type.contains("*")) {
                processedType.append("*");
            }
            if (declaratorText.contains("&") || type.contains("&")) {
                processedType.append("&");
            }
        }

        return processedType.toString();
    }

    private boolean isMethodDeclaration(CPP14Parser.MemberDeclaratorListContext memberDecList) {
        if (memberDecList == null)
            return false;

        for (CPP14Parser.MemberDeclaratorContext memberDec : memberDecList.memberDeclarator()) {
            if (memberDec.declarator() == null)
                continue;

            // パラメータリストがある場合はメソッド
            if (memberDec.declarator().parametersAndQualifiers() != null) {
                return true;
            }

            // ポインタメソッドの判定
            CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
            if (declarator.pointerDeclarator() != null) {
                var noPointerDec = declarator.pointerDeclarator().noPointerDeclarator();
                if (noPointerDec != null && noPointerDec.parametersAndQualifiers() != null) {
                    return true;
                }
            }

            // 関数ポインタの判定
            if (declarator.pointerDeclarator() != null &&
                    declarator.getText().contains("(*)")) {
                return true;
            }
        }
        return false;
    }

    private Set<CppClass.Modifier> extractModifiers(String type) {
        Set<CppClass.Modifier> modifiers = EnumSet.noneOf(CppClass.Modifier.class);

        // virtualをまず検出
        if (type.contains("virtual")) {
            modifiers.add(CppClass.Modifier.VIRTUAL);
        }
        // その他の修飾子
        if (type.contains("static"))
            modifiers.add(CppClass.Modifier.STATIC);
        if (type.contains("const"))
            modifiers.add(CppClass.Modifier.CONST);

        return modifiers;
    }

    private String cleanType(String type) {
        // virtualを型から除去
        return type.replaceAll("(virtual|static|const|mutable|volatile)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanTypeName(String typeName) {
        return typeName.replaceAll("[*&]", "")
                .replaceAll("\\s+", "")
                .replaceAll("const", "")
                .replaceAll("static", "")
                .replaceAll("virtual", "")
                .replaceAll("std::", "")
                .trim();
    }

    private boolean isUserDefinedType(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }

        Set<String> basicTypes = Set.of(
                "void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed");

        String cleanType = cleanTypeName(type);
        return !cleanType.isEmpty() &&
                !basicTypes.contains(cleanType) &&
                !cleanType.startsWith("std::") &&
                Character.isUpperCase(cleanType.charAt(0));
    }
}