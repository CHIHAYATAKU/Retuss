package io.github.morichan.retuss.translator.cpp.header.analyzers.member;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.AbstractAnalyzer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;

public class OperationAnalyzer extends AbstractAnalyzer {
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        if (!(context instanceof CPP14Parser.MemberdeclarationContext)) {
            return false;
        }
        CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;

        return (ctx.memberDeclaratorList() != null && isMethodDeclaration(ctx.memberDeclaratorList()));
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        try {
            CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
            CppHeaderClass currentHeaderClass = this.context.getCurrentHeaderClass();

            if (currentHeaderClass == null || ctx.declSpecifierSeq() == null) {
                return;
            }

            // 修飾子の抽出
            Set<Modifier> modifiers = extractModifiers(ctx.declSpecifierSeq());
            System.err.println("DEBUG: Starting with modifiers: " + modifiers);

            String operationName = null;

            for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                if (memberDec.declarator() == null) {
                    continue;
                }

                if (memberDec.virtualSpecifierSeq() != null) {
                    for (CPP14Parser.VirtualSpecifierContext virtualSpec : memberDec.virtualSpecifierSeq()
                            .virtualSpecifier()) {
                        if ("override".equals(virtualSpec.getText())) {
                            modifiers.add(Modifier.OVERRIDE);
                        }
                    }
                }

                // pure virtualの処理
                if (memberDec.pureSpecifier() != null) {
                    modifiers.add(Modifier.PURE_VIRTUAL);
                    currentHeaderClass.setAbstruct(true);
                }

                if (memberDec.declarator().pointerDeclarator() == null) {
                    continue;
                }

                var noPtrDec = memberDec.declarator().pointerDeclarator().noPointerDeclarator();

                if (noPtrDec == null) {
                    continue;
                }

                System.err.println("DEBUG: Processing declaration: " + noPtrDec.getText());

                System.err.println("DEBUG: NoPointerDeclarator structure:");
                System.err.println(" - Child count: " + noPtrDec.getChildCount());
                for (int i = 0; i < noPtrDec.getChildCount(); i++) {
                    ParseTree child = noPtrDec.getChild(i);
                    System.err.println(
                            " - Child " + i + ": [" + child.getClass().getSimpleName() + "] " + child.getText());
                }

                // 操作名の取得
                if (noPtrDec.declaratorid() != null &&
                        noPtrDec.declaratorid().idExpression() != null &&
                        noPtrDec.declaratorid().idExpression().unqualifiedId() != null) {

                    operationName = noPtrDec.declaratorid()
                            .idExpression().unqualifiedId().getText();
                    System.err.println("DEBUG: Found operation name directly: " + operationName);
                } else if (noPtrDec.getChildCount() > 0 &&
                        noPtrDec.getChild(0) instanceof CPP14Parser.NoPointerDeclaratorContext) {
                    // 子ノードから取得を試みる
                    var firstChild = (CPP14Parser.NoPointerDeclaratorContext) noPtrDec.getChild(0);
                    if (firstChild.declaratorid() != null &&
                            firstChild.declaratorid().idExpression() != null &&
                            firstChild.declaratorid().idExpression().unqualifiedId() != null) {

                        operationName = firstChild.declaratorid()
                                .idExpression().unqualifiedId().getText();
                        System.err.println("DEBUG: Found operation name from child: " + operationName);
                    }
                }

                // nullチェック
                if (operationName == null) {
                    System.err.println("DEBUG: Could not extract operation name");
                    return;
                }

                System.err.println("DEBUG: opName:" + operationName);

                Operation operation = new Operation(new Name(operationName));
                operation.setVisibility(convertVisibility(this.context.getCurrentVisibility()));

                // 戻り値の型を取得
                String operationType = null;
                for (CPP14Parser.DeclSpecifierContext declSpec : ctx.declSpecifierSeq().declSpecifier()) {
                    // cvQualifierの場合はスキップ（これは修飾子として別途処理）
                    if (declSpec.typeSpecifier() != null &&
                            declSpec.typeSpecifier().trailingTypeSpecifier() != null &&
                            declSpec.typeSpecifier().trailingTypeSpecifier().cvQualifier() != null) {
                        continue;
                    }

                    String returnType = extractReturnType(declSpec, operation, currentHeaderClass);
                    if (returnType != null) {
                        operationType = returnType;
                    }
                }

                // 戻り値の型が取得できた場合のみ設定
                if (operationType != null) {
                    operation.setReturnType(new Type(operationType));
                    System.err.println("DEBUG: Found return type: " + operationType);
                }

                currentHeaderClass.addOperation(operation);

                // パラメータの処理
                if (noPtrDec.parametersAndQualifiers() != null &&
                        noPtrDec.parametersAndQualifiers().parameterDeclarationClause() != null &&
                        noPtrDec.parametersAndQualifiers().parameterDeclarationClause()
                                .parameterDeclarationList() != null) {

                    CPP14Parser.ParameterDeclarationListContext paramDecList = noPtrDec.parametersAndQualifiers()
                            .parameterDeclarationClause()
                            .parameterDeclarationList();

                    // パラメータごとの処理
                    for (CPP14Parser.ParameterDeclarationContext paramDec : paramDecList.parameterDeclaration()) {
                        if (paramDec.declSpecifierSeq() == null ||
                                paramDec.declSpecifierSeq().declSpecifier() == null) {
                            continue;
                        }

                        Boolean isPointer = false;
                        Boolean isRef = false;
                        String paramType = "";

                        // パラメータの型を取得
                        for (CPP14Parser.DeclSpecifierContext declSpec : paramDec.declSpecifierSeq().declSpecifier()) {
                            if (declSpec.typeSpecifier() != null &&
                                    declSpec.typeSpecifier().trailingTypeSpecifier() != null) {

                                CPP14Parser.SimpleTypeSpecifierContext simple = declSpec.typeSpecifier()
                                        .trailingTypeSpecifier().simpleTypeSpecifier();

                                // ユーザ定義型（std含む）
                                if (simple.theTypeName() != null) {
                                    if (simple.theTypeName().className() != null) {
                                        paramType = simple.theTypeName().className().getText();
                                    } else {
                                        paramType = simple.theTypeName().getText();
                                    }

                                    // ユーザ定義型であれば依存関係を追加
                                    if (isUserDefinedType(paramType)) {
                                        RelationshipInfo relation = new RelationshipInfo(
                                                paramType,
                                                RelationType.DEPENDENCY_PARAMETER);
                                        relation.setElement(
                                                operation.getName().getNameText(),
                                                ElementType.OPERATION,
                                                "", // パラメータなので多重度なし
                                                null);
                                        currentHeaderClass.addRelationship(relation);
                                    }
                                } else {
                                    // 基本型
                                    paramType = simple.getText();
                                }
                            }
                        }

                        // パラメータの宣言子を処理
                        if (paramDec.declarator() != null &&
                                paramDec.declarator().pointerDeclarator() != null) {

                            var ptrDec = paramDec.declarator().pointerDeclarator();
                            // ポインタ演算子の処理
                            if (ptrDec.pointerOperator() != null) {
                                for (var op : ptrDec.pointerOperator()) {
                                    String opText = op.getText().trim();
                                    if (opText.equals("*")) {
                                        isPointer = true;
                                        break;
                                    } else if (opText.equals("&")) {
                                        isRef = true;
                                        break;
                                    }
                                }
                            }

                            // パラメータ名の取得
                            if (ptrDec.noPointerDeclarator() != null &&
                                    ptrDec.noPointerDeclarator().declaratorid() != null) {

                                String paramName = ptrDec.noPointerDeclarator()
                                        .declaratorid().getText();

                                // パラメータオブジェクトの作成
                                Parameter parameter = new Parameter(new Name(paramName));
                                if (isPointer)
                                    paramType += "*";
                                if (isRef)
                                    paramType += "&";
                                parameter.setType(new Type(paramType));
                                operation.addParameter(parameter);
                            }
                        }
                    }
                }
            }

            // 修飾子の追加
            for (Modifier modifier : modifiers) {
                currentHeaderClass.addMemberModifier(operationName, modifier);
            }
            // String processedType = cleanType(rawType);
            // System.err.println("DEBUG: " + processedType);

            // if (ctx.memberDeclaratorList() != null) {
            // for (CPP14Parser.MemberDeclaratorContext memberDec :
            // ctx.memberDeclaratorList().memberDeclarator()) {
            // if (memberDec != null && memberDec.declarator() != null) {
            // handleOperation(memberDec, processedType, modifiers);
            // }
            // }
            // }
        } catch (Exception e) {
            System.err.println("Error in operation analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractReturnType(CPP14Parser.DeclSpecifierContext declSpec,
            Operation operation,
            CppHeaderClass currentClass) {
        if (declSpec.typeSpecifier() == null ||
                declSpec.typeSpecifier().trailingTypeSpecifier() == null) {
            return null;
        }

        var trailing = declSpec.typeSpecifier().trailingTypeSpecifier();
        // cvQualifierの場合はスキップ
        if (trailing.cvQualifier() != null) {
            return null;
        }

        var simple = trailing.simpleTypeSpecifier();
        if (simple == null) {
            return null;
        }

        System.err.println("DEBUG: Processing return type from: " + simple.getText());

        String returnType;
        if (simple.theTypeName() != null) {
            if (simple.theTypeName().className() != null) {
                returnType = simple.theTypeName().className().getText();
            } else {
                returnType = simple.theTypeName().getText();
            }

            // ユーザ定義型であれば依存関係を追加
            if (isUserDefinedType(returnType)) {
                RelationshipInfo relation = new RelationshipInfo(
                        returnType,
                        RelationType.DEPENDENCY_USE);
                relation.setElement(
                        operation.getName().getNameText(),
                        ElementType.OPERATION,
                        "",
                        null);
                currentClass.addRelationship(relation);
            }
        } else {
            returnType = simple.getText();
        }

        System.err.println("DEBUG: Extracted return type: " + returnType);
        return returnType;
    }

    private Set<Modifier> extractModifiers(CPP14Parser.DeclSpecifierSeqContext ctx) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        if (ctx == null || ctx.declSpecifier() == null)
            return modifiers;

        for (CPP14Parser.DeclSpecifierContext spec : ctx.declSpecifier()) {
            System.err.println("DEBUG: Processing modifier: " + spec.getText());

            // cv修飾子の処理（const等）
            if (spec.typeSpecifier() != null &&
                    spec.typeSpecifier().trailingTypeSpecifier() != null) {

                var trailing = spec.typeSpecifier().trailingTypeSpecifier();
                if (trailing.cvQualifier() != null) {
                    String cvQualifier = trailing.cvQualifier().getText();
                    System.err.println("DEBUG: Found cv qualifier: " + cvQualifier);
                    if ("const".equals(cvQualifier)) {
                        modifiers.add(Modifier.READONLY);
                    }
                }
            }

            // 関数指定子の処理
            if (spec.functionSpecifier() != null) {
                String function = spec.functionSpecifier().getText();
                System.err.println("DEBUG: Found function specifier: " + function);
                if ("virtual".equals(function)) {
                    modifiers.add(Modifier.VIRTUAL);
                }
            }

            // ストレージクラス指定子の処理
            if (spec.storageClassSpecifier() != null) {
                String storage = spec.storageClassSpecifier().getText();
                System.err.println("DEBUG: Found storage class: " + storage);
                if ("static".equals(storage)) {
                    modifiers.add(Modifier.STATIC);
                }
            }
        }

        System.err.println("DEBUG: Final modifiers: " + modifiers);
        return modifiers;
    }

    private boolean isUserDefinedType(String typeName) {

        // stdライブラリの型のセット
        Set<String> stdTypes = Set.of(
                "string", "vector", "list", "map", "set",
                "queue", "deque", "array", "stack",
                "shared_ptr", "unique_ptr", "weak_ptr",
                "pair", "tuple", "function");

        return !stdTypes.contains(typeName) && !typeName.contains("<");
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

    public void appendOperationModifiers(StringBuilder pumlBuilder, Operation op, Set<Modifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty())
            return;

        List<String> modifierStrings = new ArrayList<>();

        // PURE_VIRTUALの場合は{abstract}のみを表示
        if (modifiers.contains(Modifier.PURE_VIRTUAL)) {
            modifierStrings.add("{abstract}");
        } else {
            // それ以外の修飾子を処理
            for (Modifier modifier : modifiers) {
                if (modifier.isApplicableTo(ElementType.OPERATION) &&
                        modifier != Modifier.PURE_VIRTUAL) {
                    modifierStrings.add(modifier.getPlantUmlText(false));
                }
            }
        }

        if (!modifierStrings.isEmpty()) {
            pumlBuilder.append(String.join(" ", modifierStrings))
                    .append(" ");
        }
    }

    private boolean isMethodDeclaration(CPP14Parser.MemberDeclaratorListContext memberDecList) {
        if (memberDecList == null)
            return false;

        for (CPP14Parser.MemberDeclaratorContext memberDec : memberDecList.memberDeclarator()) {
            if (memberDec.declarator() == null)
                continue;

            CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
            if (declarator.pointerDeclarator() != null) {
                var noPointerDec = declarator.pointerDeclarator().noPointerDeclarator();
                if (noPointerDec != null) {
                    // パラメータリストの存在をチェック
                    if (noPointerDec.parametersAndQualifiers() != null) {
                        return true;
                    }

                    // コンストラクタ/デストラクタのチェック
                    if (noPointerDec.declaratorid() != null &&
                            noPointerDec.declaratorid().idExpression() != null &&
                            noPointerDec.declaratorid().idExpression().unqualifiedId() != null) {

                        String name = noPointerDec.declaratorid().idExpression().unqualifiedId().getText();
                        // デストラクタチェック (~で始まる)
                        if (name.startsWith("~")) {
                            return true;
                        }

                        // コンストラクタチェック (クラス名と同じ)
                        if (this.context.getCurrentHeaderClass() != null &&
                                name.equals(this.context.getCurrentHeaderClass().getName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}