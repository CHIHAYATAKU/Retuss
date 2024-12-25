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
import io.github.morichan.retuss.translator.cpp.header.util.CollectionTypeInfo;

import org.antlr.v4.runtime.ParserRuleContext;
import java.util.*;
import java.util.stream.Collectors;

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
            processReturnType(operation, returnType, currentHeaderClass);
        }

        operation.setVisibility(convertVisibility(context.getCurrentVisibility()));

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
                                // 各パラメータの型を処理
                                for (Parameter param : operation.getParameters()) {
                                    processParameterType(param, currentHeaderClass);
                                }
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
        if (!returnType.equals("void")) {
            analyzeTypeForDependency(returnType, currentClass, RelationType.DEPENDENCY_USE);
        }
    }

    private void analyzeTypeForDependency(
            String type,
            CppHeaderClass currentClass,
            RelationType relationType) {

        // テンプレート型の解析
        List<String> allTypes = new ArrayList<>();
        allTypes.add(type);

        if (isCollectionType(type)) {
            String params = extractTemplateParameters(type);
            allTypes.addAll(parseTemplateParameters(params));
        }

        // 全ての型について依存関係を検証
        for (String t : allTypes) {
            String cleanType = cleanTypeForRelationship(t);
            if (isUserDefinedType(cleanType)) {
                RelationshipInfo relation = new RelationshipInfo(
                        cleanType,
                        relationType);
                currentClass.addRelationship(relation);
            }
        }
    }

    private boolean isUserDefinedType(String type) {
        Set<String> basicTypes = Set.of(
                "void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed",
                // コレクション型は除外
                "vector", "list", "map", "unorderedmap", "array", "queue", "stack", "deque",
                // スマートポインタは除外
                "uniqueptr", "sharedptr", "weakptr",
                // その他標準型
                "string");

        type = type.replaceAll("std::", "");

        // テンプレートパラメータは除外して型名のみを見る
        if (type.contains("<")) {
            type = type.substring(0, type.indexOf("<"));
        }

        return !basicTypes.contains(type.toLowerCase());
    }

    private boolean isCollectionType(String type) {
        return type.matches(".*(?:vector|list|set|map|array|queue|stack|deque)<.*>");
    }

    private String parseTemplateType(String type) {
        // 1. 基本的なクリーンアップ
        type = type.replaceAll("std::", "")
                .replaceAll("(static|const|mutable|final)", "")
                .trim();

        // 2. 標準型の変換
        Map<String, String> standardTypes = Map.of(
                "string", "String",
                "vector", "Vector",
                "list", "List",
                "map", "Map",
                "set", "Set",
                "array", "Array",
                "shared_ptr", "sharedptr",
                "unique_ptr", "uniqueptr",
                "unordered_map", "unorderedmap",
                "weak_ptr", "weakptr");
        int templateStart = type.indexOf('<');
        if (templateStart == -1) {
            // テンプレートでない場合は標準型変換のみ
            return standardTypes.getOrDefault(type, type);
        }

        // 4. テンプレートの場合
        String baseName = type.substring(0, templateStart);
        baseName = standardTypes.getOrDefault(baseName, baseName);

        String params = extractTemplateParameters(type);
        List<String> paramTypes = parseTemplateParameters(params);

        // 5. パラメータも再帰的に同じ処理
        return baseName + "<" +
                paramTypes.stream()
                        .map(this::parseTemplateType) // 再帰的に処理
                        .collect(Collectors.joining(","))
                +
                ">";
    }

    private String extractTemplateParameters(String type) {
        int nestLevel = 0;
        int start = type.indexOf('<') + 1;
        int end = -1;

        for (int i = start; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<')
                nestLevel++;
            else if (c == '>') {
                if (nestLevel == 0) {
                    end = i;
                    break;
                }
                nestLevel--;
            }
        }
        return type.substring(start, end);
    }

    private List<String> parseTemplateParameters(String params) {
        List<String> result = new ArrayList<>();
        int nestLevel = 0;
        StringBuilder current = new StringBuilder();

        for (char c : params.toCharArray()) {
            if (c == '<')
                nestLevel++;
            else if (c == '>')
                nestLevel--;
            else if (c == ',' && nestLevel == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private void processReturnType(Operation operation, String returnType, CppHeaderClass currentClass) {
        String processedType = parseTemplateType(returnType);

        // void チェック
        if (processedType == null || processedType.trim().isEmpty()) {
            processedType = "void";
        }

        // メソッド名の修飾子を型に移動（既存の処理）
        String methodName = operation.getName().getNameText();
        if (methodName.contains("*")) {
            methodName = methodName.replace("*", "");
            processedType += "*";
        }
        if (methodName.contains("&")) {
            methodName = methodName.replace("&", "");
            processedType += "&";
        }

        operation.setName(new Name(methodName));
        operation.setReturnType(new Type(processedType));
    }

    private String processType(String type) {
        StringBuilder processedType = new StringBuilder();

        // テンプレート部分の分離
        int templateStart = type.indexOf('<');
        String templatePart = "";
        if (templateStart != -1) {
            int templateEnd = type.lastIndexOf('>');
            if (templateEnd != -1) {
                templatePart = type.substring(templateStart, templateEnd + 1);
                type = type.substring(0, templateStart);
            }
        }

        // 基本型の処理
        type = type.replaceAll("std::", "")
                .replaceAll("(static|const|mutable|final)", "")
                .trim();

        // 標準型の変換
        Map<String, String> standardTypes = Map.of(
                "string", "String",
                "vector", "Vector",
                "list", "List",
                "map", "Map",
                "set", "Set",
                "array", "Array",
                "shared_ptr", "sharedptr",
                "unique_ptr", "uniqueptr",
                "unordered_map", "unorderedmap",
                "weak_ptr", "weakptr");

        type = standardTypes.getOrDefault(type, type);
        processedType.append(type);

        // テンプレート部分の再帰的処理
        if (!templatePart.isEmpty()) {
            String innerTypes = templatePart.substring(1, templatePart.length() - 1);
            String[] types = innerTypes.split(",");
            processedType.append("<");
            for (int i = 0; i < types.length; i++) {
                String innerType = types[i].trim();
                innerType = processType(innerType).replaceAll("std::", "");
                processedType.append(innerType);
                if (i < types.length - 1) {
                    processedType.append(",");
                }
            }
            processedType.append(">");
        }

        return processedType.toString();
    }

    private void addDependencyUseRelation(CppHeaderClass currentClass, String targetType) {
        RelationshipInfo relation = new RelationshipInfo(
                targetType,
                RelationType.DEPENDENCY_USE);
        currentClass.addRelationship(relation);
    }

    private void processParameterType(Parameter param, CppHeaderClass currentClass) {
        String paramType = param.getType().toString();
        analyzeTypeForDependency(paramType, currentClass, RelationType.DEPENDENCY_PARAMETER);
    }

    private CollectionTypeInfo parseCollectionType(String type) {
        CollectionTypeInfo info = new CollectionTypeInfo();

        // スマートポインタかコレクション型か判定
        if (type.contains("_ptr<")) {
            info.setBaseType(type.substring(0, type.indexOf("_ptr<"))
                    .replaceAll("std::", "") + "ptr");
            info.getParameterTypes().add(extractInnerType(type));
        } else if (type.contains("<")) {
            info.setBaseType(type.substring(0, type.indexOf("<"))
                    .replaceAll("std::", ""));
            // 複数パラメータの処理
            String params = type.substring(type.indexOf("<") + 1, type.lastIndexOf(">"));
            Arrays.stream(params.split(","))
                    .map(param -> param.trim().replaceAll("std::", ""))
                    .forEach(info.getParameterTypes()::add);
        }

        info.determineMultiplicity();
        return info;
    }

    private String extractInnerType(String type) {
        int start = type.indexOf("<") + 1;
        int end = type.lastIndexOf(">");
        return type.substring(start, end).trim().replaceAll("std::", "");
    }

    private String extractElementType(String type) {
        if (type.contains("<") && type.contains(">")) {
            return type.replaceAll(".*<(.+)>.*", "$1")
                    .replaceAll("std::", "")
                    .trim();
        }
        return type;
    }

    private void handleCollectionRelationship(String type, String operationName,
            CppHeaderClass currentClass, Visibility visibility) {

        CollectionTypeInfo info = parseCollectionType(type);

        // コレクション型のパラメータで、ユーザー定義型のみ関係を生成
        for (String paramType : info.getParameterTypes()) {
            if (isUserDefinedType(paramType)) {
                String cleanType = extractBaseTypeName(paramType);

                // パラメータの場合は関連ではなく依存関係
                RelationshipInfo relation = new RelationshipInfo(
                        cleanType,
                        RelationType.DEPENDENCY_PARAMETER);
                currentClass.addRelationship(relation);
            }
        }
    }

    private String extractBaseTypeName(String typeName) {
        String baseType = typeName;

        // 名前空間の除去
        baseType = baseType.replace("std::", "");

        // 空白の除去
        baseType = baseType.replaceAll("\\s+", "");

        // 修飾子の除去
        String[] modifiers = {
                "static", "const", "mutable", "volatile", "virtual",
                "final", "explicit", "friend", "inline", "constexpr",
                "thread_local", "register", "extern", "enum"
        };

        for (String modifier : modifiers) {
            baseType = baseType.replace(modifier, "");
        }

        return baseType.trim();
    }

    private String processOperationType(String type) {
        Map<String, String> standardTypes = Map.of(
                "string", "String",
                "vector", "Vector",
                "list", "List",
                "map", "Map",
                "set", "Set",
                "array", "Array",
                "shared_ptr", "sharedptr",
                "unique_ptr", "uniqueptr",
                "unordered_map", "unorderedmap",
                "weak_ptr", "weakptr");

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

            String paramType = paramInfo.baseType;

            // コレクション型の処理
            if (isCollectionType(paramType)) {
                handleCollectionRelationship(
                        paramType,
                        operation.getName().getNameText(),
                        currentClass,
                        Visibility.Public);
            } else if (isUserDefinedType(paramInfo.baseType)) {
                addRelationship(currentClass, paramInfo, operation.getName().getNameText(), relationships);
            }
            Parameter param = new Parameter(new Name(paramInfo.parameterName));
            param.setType(new Type(buildParameterType(paramInfo)));
            operation.addParameter(param);
        }

        for (RelationshipInfo relation : relationships.values()) {
            currentClass.addRelationship(relation);
        }
    }

    // パラメータの型名を構築（constを除外）
    private String buildParameterType(ParameterInfo info) {
        String processedType = parseTemplateType(info.baseType);

        // ポインタ/参照の追加
        if (info.isPointer) {
            processedType += "*";
        }
        if (info.isReference) {
            processedType += "&";
        }
        return processedType;
    }

    private void addRelationship(CppHeaderClass currentClass, ParameterInfo paramInfo,
            String operationName, Map<String, RelationshipInfo> relationships) {

        if (isUserDefinedType(paramInfo.baseType)) {
            // 全てのパラメータを依存関係として扱う
            RelationshipInfo relation = new RelationshipInfo(
                    paramInfo.baseType,
                    RelationType.DEPENDENCY_PARAMETER);
            relationships.put(paramInfo.baseType, relation);
        }
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