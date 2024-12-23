package io.github.morichan.retuss.translator.cpp.header.analyzers.member;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.AbstractAnalyzer;

import org.antlr.v4.runtime.ParserRuleContext;
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

            String rawType = "";
            Set<Modifier> modifiers = new HashSet<>();

            if (ctx.declSpecifierSeq() != null) {
                rawType = ctx.declSpecifierSeq().getText();
                System.err.println("DEBUG: " + rawType);
                modifiers = extractModifiers(rawType);
                if (extractIsOverride(ctx)) {
                    modifiers.add(Modifier.OVERRIDE);
                }
            }

            for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                if (memberDec.pureSpecifier() != null) { // pureSpecifierの存在で判定
                    currentHeaderClass.setAbstruct(true);
                    modifiers.add(Modifier.ABSTRACT);
                    System.err.println("DEBUG: " + currentHeaderClass.getName() + "is Abstract Class！！: ");
                    break;
                }
            }

            String processedType = cleanType(rawType);
            System.err.println("DEBUG: " + processedType);

            if (ctx.memberDeclaratorList() != null) {
                for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                    if (memberDec != null && memberDec.declarator() != null) {
                        handleOperation(memberDec, processedType, modifiers);
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
            Set<Modifier> modifiers) {

        CppHeaderClass currentHeaderClass = context.getCurrentHeaderClass();
        String methodName = extractMethodName(memberDec.declarator());
        System.err.println("DEBUG: Start handleOperaiton!!");
        System.err.println("==============================");
        System.out.println("DEBUG: Processing method: " + methodName);
        System.out.println("DEBUG: Return type: " + returnType);
        System.out.println("DEBUG: Modifiers: " + modifiers);

        // コンストラクタ/デストラクタの判定
        boolean isConstructor = methodName.equals(currentHeaderClass.getName());
        boolean isDestructor = methodName.startsWith("~") &&
                methodName.substring(1).equals(currentHeaderClass.getName());

        Operation operation = new Operation(new Name(methodName));
        if (isConstructor || isDestructor) {
            return;
        } else {
            // 通常のメソッドは戻り値型を処理
            String processedReturnType = processOperationType(returnType);
            if (processedReturnType == null || processedReturnType.trim().isEmpty()) {
                processedReturnType = "void";
            }
            // メソッド名から修飾子を抽出し、型に移動
            if (methodName.contains("*")) {
                methodName = methodName.replace("*", "");
                processedReturnType += "*";
            }
            if (methodName.contains("&")) {
                methodName = methodName.replace("&", "");
                processedReturnType += "&";
            }
            operation.setName(new Name(methodName));
            operation.setReturnType(new Type(processedReturnType));
        }

        operation.setVisibility(convertVisibility(context.getCurrentVisibility()));

        // パラメータ処理の既存コード
        CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
        System.out.println("DEBUG: Declarator: " + (declarator != null ? declarator.getText() : "null"));
        System.out
                .println("DEBUG: Declarator class: " + (declarator != null ? declarator.getClass().getName() : "null"));

        if (declarator != null) {
            CPP14Parser.PointerDeclaratorContext ptrDec = declarator.pointerDeclarator();
            System.out.println("DEBUG: PointerDeclarator: " + (ptrDec != null ? ptrDec.getText() : "null"));

            if (ptrDec != null) {
                CPP14Parser.NoPointerDeclaratorContext noPtrDec = ptrDec.noPointerDeclarator();
                System.out.println("DEBUG: NoPointerDeclarator: " + (noPtrDec != null ? noPtrDec.getText() : "null"));

                if (noPtrDec != null) {
                    System.out.println("DEBUG: NoPointerDeclarator methods: ");
                    for (java.lang.reflect.Method method : noPtrDec.getClass().getMethods()) {
                        System.out.println(" - " + method.getName());
                    }

                    for (int i = 0; i < noPtrDec.getChildCount(); i++) {
                        org.antlr.v4.runtime.tree.ParseTree child = noPtrDec.getChild(i);
                        System.out.println(
                                "DEBUG: Child " + i + ": " + child.getClass().getName() + " - " + child.getText());

                        if (child instanceof CPP14Parser.ParametersAndQualifiersContext) {
                            CPP14Parser.ParametersAndQualifiersContext params = (CPP14Parser.ParametersAndQualifiersContext) child;
                            System.out.println("DEBUG: Found ParametersAndQualifiers: " + params.getText());

                            if (params.parameterDeclarationClause() != null) {
                                System.out.println("DEBUG: Found ParameterDeclarationClause: "
                                        + params.parameterDeclarationClause().getText());
                                processParameters(params, operation);
                            }
                        }
                    }
                }
            }
        }

        currentHeaderClass.addOperation(operation);
        System.out.println("DEBUG: Operation added to class. Current operation count: " +
                currentHeaderClass.getOperationList().size());
        for (Modifier modifier : modifiers) {
            currentHeaderClass.addMemberModifier(methodName, modifier);
            System.out.println("DEBUG: Modifier" + modifier);
        }

        System.out.println("DEBUG: Operation completed: " + methodName);
        // 実現関係の処理
        if (modifiers.contains(Modifier.OVERRIDE)) {
            processRealization(currentHeaderClass, operation, methodName, returnType, modifiers);
        }

        System.out.println("DEBUG: Adding operation: " + methodName);
        System.out.println("DEBUG: Is constructor: " + isConstructor);
        System.out.println("DEBUG: Is destructor: " + isDestructor);

        // 依存関係の解析を追加
        analyzeOperationDependencies(
                methodName,
                returnType,
                operation,
                currentHeaderClass);
    }

    private void analyzeOperationDependencies(
            String methodName,
            String returnType,
            Operation operation,
            CppHeaderClass currentClass) {

        // コンストラクタ/デストラクタの場合は戻り値型の依存関係は不要
        if (!methodName.equals(currentClass.getName()) && !methodName.startsWith("~")) {
            analyzeReturnTypeDependency(returnType, currentClass);
        }
    }

    private String cleanTypeForRelationship(String type) {
        String cleanType = type;

        // コレクション型の場合は要素型を取得
        if (isCollectionType(cleanType)) {
            cleanType = extractElementType(cleanType);
        }

        // ネストされた型の場合は最後の部分を取得
        if (cleanType.contains("::")) {
            String[] parts = cleanType.split("::");
            cleanType = parts[parts.length - 1];
        }

        // ポインタや参照を除去
        cleanType = cleanType.replaceAll("[*&]", "").trim();

        return cleanType;
    }

    private void analyzeReturnTypeDependency(String returnType, CppHeaderClass currentClass) {
        // voidは依存関係を作成しない
        if (returnType.equals("void"))
            return;

        String cleanType = cleanTypeForRelationship(returnType);

        if (isUserDefinedType(cleanType)) {
            RelationshipInfo relation = new RelationshipInfo(
                    cleanType,
                    RelationType.DEPENDENCY_USE);
            currentClass.addRelationship(relation);
        }
    }

    private void analyzeParameterTypeDependency(String paramType, String paramName, CppHeaderClass currentClass) {
        String cleanType = cleanTypeForRelationship(paramType);

        if (isUserDefinedType(cleanType)) {
            RelationshipInfo relation = new RelationshipInfo(
                    cleanType,
                    RelationType.DEPENDENCY_PARAMETER);
            currentClass.addRelationship(relation);
        }
    }

    private boolean isUserDefinedType(String type) {
        Set<String> basicTypes = Set.of(
                "void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed",
                "string", "vector", "list", "map", "set",
                "array", "queue", "stack", "deque");

        return !basicTypes.contains(type) &&
                !type.startsWith("std::") &&
                Character.isUpperCase(type.charAt(0));
    }

    private boolean isCollectionType(String type) {
        return type.matches(".*(?:vector|list|set|map|array|queue|stack|deque)<.*>");
    }

    private String extractElementType(String type) {
        if (type.contains("<") && type.contains(">")) {
            return type.replaceAll(".*<(.+)>.*", "$1")
                    .replaceAll("std::", "")
                    .trim();
        }
        return type;
    }

    private String processOperationType(String type) {
        Map<String, String> standardTypes = Map.of(
                "string", "String",
                "vector", "Vector",
                "list", "List",
                "map", "Map",
                "set", "Set",
                "array", "Array");

        // std:: の除去
        String processedType = type.replaceAll("std::", "");

        // 標準型の変換
        for (Map.Entry<String, String> entry : standardTypes.entrySet()) {
            processedType = processedType.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
        }

        return processedType.trim();
    }

    private static class ParameterInfo {
        String baseType; // 基本型名
        String parameterName;
        boolean isConst; // const修飾子の有無
        boolean isPointer; // ポインタの有無
        boolean isReference; // 参照の有無

        RelationType determineRelationType() {
            // const参照/ポインタの場合は依存関係
            if (isConst && (isReference || isPointer)) {
                return RelationType.DEPENDENCY_PARAMETER;
            }
            // 参照またはポインタの場合は関連関係
            if (isReference || isPointer) {
                return RelationType.ASSOCIATION;
            }
            // それ以外は依存関係
            return RelationType.DEPENDENCY_PARAMETER;
        }
    }

    private ParameterInfo extractParameterInfo(CPP14Parser.ParameterDeclarationContext paramCtx) {
        ParameterInfo info = new ParameterInfo();

        // 型指定子からの情報抽出
        if (paramCtx.declSpecifierSeq() != null) {
            for (CPP14Parser.DeclSpecifierContext spec : paramCtx.declSpecifierSeq().declSpecifier()) {
                // typeSpecifierの処理
                if (spec.typeSpecifier() != null) {
                    var typeSpec = spec.typeSpecifier();
                    if (typeSpec.trailingTypeSpecifier() != null) {
                        var trailing = typeSpec.trailingTypeSpecifier();

                        // const check
                        if (trailing.cvQualifier() != null &&
                                trailing.cvQualifier().getText().equals("const")) {
                            info.isConst = true;
                        }
                        // 型名の取得
                        else if (trailing.simpleTypeSpecifier() != null) {
                            info.baseType = trailing.simpleTypeSpecifier().getText()
                                    .replaceAll("std::", "");
                        }
                    }
                }
                // 直接のconst指定子の検出
                else if (spec.getText().equals("const")) {
                    info.isConst = true;
                }
            }
        }

        // declaratorからのポインタ/参照情報の抽出
        if (paramCtx.declarator() != null) {
            var declarator = paramCtx.declarator();
            if (declarator.pointerDeclarator() != null) {
                // ポインタ演算子の検出
                for (CPP14Parser.PointerOperatorContext op : declarator.pointerDeclarator().pointerOperator()) {
                    String opText = op.getText();
                    if (opText.contains("*")) {
                        info.isPointer = true;
                    }
                    if (opText.contains("&")) {
                        info.isReference = true;
                    }
                }
                // パラメータ名の取得
                if (declarator.pointerDeclarator().noPointerDeclarator() != null &&
                        declarator.pointerDeclarator().noPointerDeclarator().declaratorid() != null) {
                    info.parameterName = declarator.pointerDeclarator()
                            .noPointerDeclarator().declaratorid().getText();
                }
            }
        }
        return info;
    }

    private void processParameters(CPP14Parser.ParametersAndQualifiersContext params,
            Operation operation) {

        CppHeaderClass currentClass = context.getCurrentHeaderClass();

        Map<String, RelationshipInfo> relationships = new HashMap<>();

        if (params.parameterDeclarationClause() == null)
            return;
        var paramList = params.parameterDeclarationClause().parameterDeclarationList();
        if (paramList == null)
            return;

        for (CPP14Parser.ParameterDeclarationContext paramCtx : paramList.parameterDeclaration()) {
            ParameterInfo paramInfo = extractParameterInfo(paramCtx);
            System.out.println(paramCtx.getText());

            // 修飾子を含む完全な型名を構築
            StringBuilder fullType = new StringBuilder();
            if (paramInfo.isConst) {
                fullType.append("const ");
            }
            fullType.append(paramInfo.baseType);
            if (paramInfo.isPointer) {
                fullType.append("*");
            }
            if (paramInfo.isReference) {
                fullType.append("&");
            }

            Parameter param = new Parameter(new Name(paramInfo.parameterName));
            param.setType(new Type(buildParameterType(paramInfo)));
            operation.addParameter(param);

            // ユーザー定義型の場合のみ関係を追加
            if (isUserDefinedType(paramInfo.baseType)) {
                addRelationship(currentClass, paramInfo, operation.getName().getNameText(), relationships);
            }
        }

        for (RelationshipInfo relation : relationships.values()) {
            currentClass.addRelationship(relation);
        }
    }

    // パラメータの型名を構築（constを除外）
    private String buildParameterType(ParameterInfo info) {
        StringBuilder type = new StringBuilder();
        type.append(info.baseType);
        if (info.isPointer) {
            type.append("*");
        }
        if (info.isReference) {
            type.append("&");
        }
        return type.toString();
    }

    private void addRelationship(CppHeaderClass currentClass, ParameterInfo paramInfo,
            String operationName, Map<String, RelationshipInfo> relationships) {

        if (isUserDefinedType(paramInfo.baseType)) {
            // ポインタまたは参照の場合は関連関係「のみ」
            if (paramInfo.isPointer || paramInfo.isReference) {
                RelationshipInfo relation = relationships.get(paramInfo.baseType);
                if (relation == null) {
                    relation = new RelationshipInfo(
                            paramInfo.baseType,
                            RelationType.ASSOCIATION);
                    relationships.put(paramInfo.baseType, relation);
                }

                relation.addElement(
                        operationName,
                        ElementType.OPERATION,
                        determineMultiplicity(paramInfo),
                        Visibility.Public);
                return; // 関連を追加したら終了（依存は作らない）
            }
            // 値型の場合のみ依存関係を作成
            else {
                RelationshipInfo relation = new RelationshipInfo(
                        paramInfo.baseType,
                        RelationType.DEPENDENCY_PARAMETER);
                relationships.put(paramInfo.baseType, relation);
            }
        }
    }

    private void addAssociation(CppHeaderClass currentClass, ParameterInfo paramInfo) {
        RelationshipInfo relation = new RelationshipInfo(
                paramInfo.baseType,
                RelationType.ASSOCIATION);

        // 多重度の設定
        String multiplicity = determineMultiplicity(paramInfo);
        relation.addElement(
                paramInfo.parameterName,
                ElementType.PARAMETER,
                multiplicity,
                Visibility.Public // パラメータの場合は常にPublic
        );

        currentClass.addRelationship(relation);
    }

    private void addDependency(CppHeaderClass currentClass, ParameterInfo paramInfo) {
        RelationshipInfo relation = new RelationshipInfo(
                paramInfo.baseType,
                RelationType.DEPENDENCY_PARAMETER);
        currentClass.addRelationship(relation);
    }

    private String determineMultiplicity(ParameterInfo paramInfo) {
        if (paramInfo.isPointer) {
            return "0..1"; // ポインタの場合はnullableなので0..1
        }
        if (paramInfo.isReference) {
            return "1"; // 参照の場合は必ず存在するので1
        }
        return "1"; // デフォルトは1
    }

    private Boolean extractIsOverride(CPP14Parser.MemberdeclarationContext ctx) {
        String fullText = ctx.getText();
        int parenIndex = fullText.indexOf(')');
        if (parenIndex > 0) {
            String nameWithScope = fullText.substring(parenIndex + 1, fullText.length());
            System.out.println("nameWithScope！！ " + nameWithScope);
            if (!nameWithScope.contains("override"))
                return false;
        }
        return true;
    }

    private void processRealization(CppHeaderClass currentClass, Operation operation, String methodName,
            String returnType, Set<Modifier> modifiers) {
        // コンストラクタ/デストラクタの場合はスキップ
        if (methodName.equals(currentClass.getName()) || methodName.startsWith("~")) {
            return;
        }

        for (RelationshipInfo relation : currentClass.getRelationships()) {
            if (relation.getType() == RelationType.INHERITANCE) {
                CppModel.getInstance().findClass(relation.getTargetClass())
                        .ifPresent(targetClass -> {
                            // インターフェースの条件チェック
                            if (targetClass.getInterface() && targetClass.getAttributeList().isEmpty()) {
                                // インターフェースを継承している時点で実現関係に変更
                                relation.setType(RelationType.REALIZATION);
                            } else {
                                targetClass.setInterface(false);
                            }
                        });
            }
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

    private String cleanName(String name) {
        return name.replaceAll("std::", "").replaceAll("[*&]", "").trim();
    }

    private String extractMethodName(CPP14Parser.DeclaratorContext declarator) {
        if (declarator == null) {
            System.out.println("DEBUG: Declarator is null");
            return null;
        }
        String fullText = declarator.getText();
        System.out.println("DEBUG: Full declarator text: " + fullText);
        // パラメータリストの前で切り取り
        int parenIndex = fullText.indexOf('(');
        if (parenIndex > 0) {
            String nameWithScope = fullText.substring(0, parenIndex);
            // スコープ解決演算子があれば除去
            int scopeIndex = nameWithScope.lastIndexOf("::");
            if (scopeIndex >= 0) {
                return nameWithScope.substring(scopeIndex + 2);
            }
            return nameWithScope;
        }
        return fullText;
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

    private Set<Modifier> extractModifiers(String type) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);

        // virtualをまず検出
        if (type.contains(Modifier.VIRTUAL.getCppText(false)))
            modifiers.add(Modifier.VIRTUAL);
        if (type.contains(Modifier.STATIC.getCppText(false)))
            modifiers.add(Modifier.STATIC);
        if (type.contains(Modifier.READONLY.getCppText(false)))
            modifiers.add(Modifier.READONLY);
        if (type.contains(Modifier.OVERRIDE.getCppText(false)))
            modifiers.add(Modifier.OVERRIDE);
        return modifiers;
    }

    private String cleanType(String type) {
        return type.replaceAll("(virtual|static|const|abstract|override)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}