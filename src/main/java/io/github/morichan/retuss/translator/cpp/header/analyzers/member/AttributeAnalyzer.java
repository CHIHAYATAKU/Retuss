package io.github.morichan.retuss.translator.cpp.header.analyzers.member;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.AbstractAnalyzer;
import io.github.morichan.retuss.translator.cpp.header.util.CollectionTypeInfo;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AttributeAnalyzer extends AbstractAnalyzer {
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        if (!(context instanceof CPP14Parser.MemberdeclarationContext)) {
            return false;
        }
        CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
        return ctx.declSpecifierSeq() != null &&
                !isMethodDeclaration(ctx.memberDeclaratorList());
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
        CppHeaderClass currentHeaderClass = this.context.getCurrentHeaderClass();

        if (currentHeaderClass == null || ctx.declSpecifierSeq() == null) {
            return;
        }

        try {
            Set<Modifier> modifiers = extractModifiers(ctx.declSpecifierSeq());
            String typeName = extractTypeName(ctx.declSpecifierSeq());
            if (ctx.memberDeclaratorList() != null) {
                for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                    handleAttribute(memberDec, typeName, modifiers);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in attribute analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleAttribute(
            CPP14Parser.MemberDeclaratorContext memberDec,
            String type,
            Set<Modifier> modifiers) {

        CppHeaderClass currentHeaderClass = context.getCurrentHeaderClass();
        String attributeName = cleanName(extractAttributeName(memberDec.declarator()));
        String initialValueStr = extractInitialValue(memberDec);
        System.err.println("DEBUG: InitialValue of -> " + attributeName + " = " + initialValueStr);

        if (currentHeaderClass.getAttributeList().stream()
                .anyMatch(attr -> attr.getName().getNameText().equals(attributeName))) {
            return;
        }

        Attribute attribute = new Attribute(new Name(attributeName));
        attribute.setType(new Type(processAttributeType(type, memberDec.declarator())));
        attribute.setVisibility(convertVisibility(context.getCurrentVisibility()));
        // 初期値の設定
        if (initialValueStr != null) {
            // 数値の場合
            if (initialValueStr.matches("-?\\d+(\\.\\d+)?")) {
                attribute.setDefaultValue(new DefaultValue(new OneIdentifier(initialValueStr)));
            }
            // 文字列の場合
            else if (initialValueStr.startsWith("\"") && initialValueStr.endsWith("\"")) {
                attribute.setDefaultValue(new DefaultValue(new OneIdentifier(initialValueStr)));
            }
            // メソッド呼び出しや式の場合
            else if (initialValueStr.contains("(") || initialValueStr.contains("+") ||
                    initialValueStr.contains("-") || initialValueStr.contains("*") ||
                    initialValueStr.contains("/")) {
                // とりあえず単純な識別子として処理
                attribute.setDefaultValue(new DefaultValue(new OneIdentifier(initialValueStr)));
            }
            // その他の場合（変数名など）
            else {
                attribute.setDefaultValue(new DefaultValue(new OneIdentifier(initialValueStr)));
            }
            System.out.println("Debug - Attribute: " + attributeName);
            System.out.println("  - Type: " + attribute.getType().toString());
            System.out.println("  - Default Value: " + attribute.getDefaultValue());
            System.out.println("  - Default Value Class: " + attribute.getDefaultValue().getClass());
        }

        currentHeaderClass.addAttribute(attribute);
        for (Modifier modifier : modifiers) {
            currentHeaderClass.addMemberModifier(attributeName, modifier);
        }

        // 型の関係解析
        analyzeAttributeTypeRelationship(type, memberDec.declarator(), attributeName, memberDec);
    }

    private String cleanName(String name) {
        return name.replaceAll("[*&]", "").trim();
    }

    private String extractInitialValue(CPP14Parser.MemberDeclaratorContext memberDec) {
        try {
            if (memberDec.braceOrEqualInitializer() != null) {
                CPP14Parser.InitializerClauseContext initClause = memberDec.braceOrEqualInitializer()
                        .initializerClause();
                if (initClause != null) {
                    if (initClause.assignmentExpression() != null) {
                        // 単純な代入式の場合
                        return initClause.assignmentExpression().getText();
                    } else if (initClause.bracedInitList() != null) {
                        // 波括弧による初期化の場合
                        return "{" + initClause.bracedInitList().initializerList().getText() + "}";
                    }
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error extracting initial value: " + e.getMessage());
            return null;
        }
    }

    private void analyzeAttributeTypeRelationship(
            String type,
            CPP14Parser.DeclaratorContext declarator,
            String attributeName,
            CPP14Parser.MemberDeclaratorContext memberDec) {

        String cleanType = extractBaseTypeName(type);
        String declaratorText = declarator.getText();

        System.out.println("\nAnalyzing relationship for: " + attributeName);
        System.out.println("Original type: " + type);
        System.out.println("Cleaned type: " + cleanType);

        // アノテーションの取得
        String relationshipAnnotation = extractRelationshipAnnotation(memberDec);
        System.out.println("Relationship annotation: " + relationshipAnnotation);
        if (isSmartPointer(type)) {
            cleanType = extractInnerType(type); // Target を抽出
        }

        // ネストされたクラスの場合は最後の部分を取得
        if (cleanType.contains("::")) {
            String[] parts = cleanType.split("::");
            cleanType = parts[parts.length - 1];
        }

        // ユーザー定義型でない場合はスキップ
        if (!isUserDefinedType(cleanType)) {
            if (!isCollectionType(type)) {
                System.out.println("Not a user-defined type, skipping...");
                return;
            } else {
                // コレクションの場合は要素型を確認
                String elementType = extractElementType(type);
                if (!isUserDefinedType(elementType)) {
                    System.out.println("Collection of non-user-defined type, skipping...");
                    return;
                }
            }
        }

        CppHeaderClass currentClass = context.getCurrentHeaderClass();
        Visibility visibility = convertVisibility(context.getCurrentVisibility());
        Set<Modifier> mods = currentClass.getModifiers(attributeName);

        RelationType relationType = determineRelationshipType(type, declaratorText, relationshipAnnotation);

        // コレクション型の処理
        if (isCollectionType(type)) {
            handleCollectionRelationship(
                    type, attributeName, visibility, mods,
                    currentClass, relationType, declaratorText);
            return;
        }

        String multiplicity = determineMultiplicity(type, declaratorText);

        RelationshipInfo relation = new RelationshipInfo(cleanType, relationType);
        relation.addElement(
                attributeName,
                ElementType.ATTRIBUTE,
                multiplicity,
                visibility);
        currentClass.addRelationship(relation);
    }

    private String extractRelationshipAnnotation(CPP14Parser.MemberDeclaratorContext memberDec) {
        try {
            // 開始位置を取得
            org.antlr.v4.runtime.Token startToken = memberDec.getStart();
            if (startToken == null)
                return null;

            // 入力ストリームから内容を取得
            org.antlr.v4.runtime.CharStream input = startToken.getInputStream();
            if (input == null)
                return null;

            // 現在の行の開始位置を見つける
            int currentPos = startToken.getStartIndex();
            int lineStart = currentPos;
            while (lineStart > 0 && input.getText(new org.antlr.v4.runtime.misc.Interval(lineStart - 1, lineStart - 1))
                    .charAt(0) != '\n') {
                lineStart--;
            }

            // 前の行を探す（コメントがある可能性がある行）
            int commentEnd = lineStart - 1;
            int commentStart = commentEnd;
            while (commentStart > 0
                    && input.getText(new org.antlr.v4.runtime.misc.Interval(commentStart - 1, commentStart - 1))
                            .charAt(0) != '\n') {
                commentStart--;
            }

            // コメント行の取得
            if (commentStart < commentEnd) {
                String commentLine = input.getText(new org.antlr.v4.runtime.misc.Interval(commentStart, commentEnd));
                System.out.println("Found comment line: " + commentLine);

                // アノテーションの解析
                if (commentLine.contains("@relationship")) {
                    Pattern pattern = Pattern.compile("@relationship\\s+(\\w+)");
                    Matcher matcher = pattern.matcher(commentLine);
                    if (matcher.find()) {
                        String relationship = matcher.group(1).toLowerCase();
                        System.out.println("Found relationship annotation: " + relationship);
                        return relationship;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting relationship annotation: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private String determineCollectionMultiplicity(String type, String declaratorText) {
        // array<Type, N> の場合はサイズを抽出
        if (type.contains("array<")) {
            try {
                String size = type.replaceAll(".*,\\s*(\\d+)\\s*>.*", "$1");
                if (size.matches("\\d+")) {
                    return size;
                }
            } catch (Exception e) {
                System.err.println("Error extracting array size: " + e.getMessage());
            }
        }

        // それ以外のコレクション型は "*"
        return "*";
    }

    private RelationType determineRelationshipType(String type,
            String declaratorText,
            String relationshipAnnotation) {
        // アノテーションがある場合はそれを優先
        if (relationshipAnnotation != null) {
            switch (relationshipAnnotation) {
                case "aggregation":
                    return RelationType.AGGREGATION;
                case "composition":
                    return RelationType.COMPOSITION;
            }
        }

        // スマートポインタの判定
        if (type.contains("unique_ptr<")) {
            return RelationType.COMPOSITION; // unique_ptrはデフォルトでコンポジション
        }
        if (type.contains("shared_ptr<") || type.contains("weak_ptr<")) {
            return RelationType.ASSOCIATION; // shared_ptr/weak_ptrはデフォルトで関連
        }
        // コレクションの判定
        if (isCollectionType(type)) {
            String elementType = extractElementType(type);
            // ポインタ要素のチェックを厳密に
            if (elementType.contains("*") ||
                    type.contains("<") && type.contains("*>") ||
                    elementType.contains("&")) {
                return RelationType.ASSOCIATION;
            }
            return RelationType.COMPOSITION;
        }

        // ポインタ/参照の判定
        if (type.contains("*") || declaratorText.contains("*") ||
                type.contains("&") || declaratorText.contains("&")) {
            return RelationType.ASSOCIATION;
        }

        // それ以外（値型）はコンポジション
        return RelationType.COMPOSITION;
    }

    private String determineMultiplicity(String type, String declaratorText) {
        // 配列の場合
        if (declaratorText.matches(".*\\[\\d+\\]")) {
            return declaratorText.replaceAll(".*\\[(\\d+)\\].*", "$1");
        }

        // スマートポインタの場合
        if (type.contains("unique_ptr"))
            return "0..1";
        if (type.contains("shared_ptr"))
            return "0..*";
        if (type.contains("weak_ptr"))
            return "0..*";

        // ポインタの場合
        if (type.contains("*") || declaratorText.contains("*")) {
            return "0..1";
        }

        // 参照の場合
        if (type.contains("&") || declaratorText.contains("&")) {
            return "1";
        }

        // 値型の場合
        return "1";
    }

    private boolean isSmartPointer(String type) {
        return type.matches(".*(?:unique_ptr|shared_ptr|weak_ptr)<.*>");
    }

    private void handleCollectionRelationship(
            String type,
            String attributeName,
            Visibility visibility,
            Set<Modifier> mods,
            CppHeaderClass currentClass,
            RelationType relationType,
            String declaratorText) {

        CollectionTypeInfo info = parseCollectionType(type);

        // ユーザー定義型のパラメータのみ関係を作成
        for (String paramType : info.getParameterTypes()) {
            if (isUserDefinedType(paramType)) {
                RelationshipInfo relation = new RelationshipInfo(
                        paramType,
                        RelationType.COMPOSITION);
                relation.addElement(
                        attributeName,
                        ElementType.ATTRIBUTE,
                        info.getMultiplicity(),
                        visibility);
                currentClass.addRelationship(relation);
            }
        }
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

    private List<String> extractTemplateTypes(String type) {
        List<String> types = new ArrayList<>();
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');

        if (start != -1 && end != -1) {
            String inner = type.substring(start + 1, end);
            String[] params = inner.split(",");
            for (String param : params) {
                types.add(param.trim());
            }
        }
        return types;
    }

    private Set<Modifier> extractModifiers(CPP14Parser.DeclSpecifierSeqContext ctx) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);

        for (CPP14Parser.DeclSpecifierContext spec : ctx.declSpecifier()) {
            // ストレージクラス指定子の処理
            if (spec.storageClassSpecifier() != null) {
                String storage = spec.storageClassSpecifier().getText();
                processStorageClassSpecifier(storage, modifiers);
            }
            // 型指定子の処理
            else if (spec.typeSpecifier() != null) {
                processTypeSpecifier(spec.typeSpecifier(), modifiers);
            }
            // その他の指定子（constexpr等）
            else {
                processOtherSpecifier(spec.getText(), modifiers);
            }
        }
        return modifiers;
    }

    private void processStorageClassSpecifier(String specifier, Set<Modifier> modifiers) {
        switch (specifier) {
            case "static":
                modifiers.add(Modifier.STATIC);
                break;
            case "mutable":
                modifiers.add(Modifier.MUTABLE);
                break;
        }
    }

    private void processTypeSpecifier(CPP14Parser.TypeSpecifierContext spec, Set<Modifier> modifiers) {
        if (spec.trailingTypeSpecifier() != null) {
            // const/volatile修飾子の確認
            if (spec.trailingTypeSpecifier().cvQualifier() != null) {
                String cv = spec.trailingTypeSpecifier().cvQualifier().getText();
                if ("const".equals(cv)) {
                    modifiers.add(Modifier.READONLY);
                }
            }
        }
    }

    private void processOtherSpecifier(String specifier, Set<Modifier> modifiers) {
        switch (specifier) {
            case "constexpr":
                modifiers.add(Modifier.READONLY);
                break;
            case "volatile":
                modifiers.add(Modifier.VOLATILE);
                break;
            case "virtual":
                modifiers.add(Modifier.VIRTUAL);
                break;
        }
    }

    private String extractTypeName(CPP14Parser.DeclSpecifierSeqContext ctx) {
        StringBuilder type = new StringBuilder();

        for (CPP14Parser.DeclSpecifierContext spec : ctx.declSpecifier()) {
            String specText = spec.getText();
            System.out.println("DEBUG: Processing type specifier: " + specText);
            if (spec.typeSpecifier() != null) {
                var typeSpec = spec.typeSpecifier();

                // enumで始まる型名の特別処理
                if (specText.startsWith("enum")) {
                    String enumTypeName = specText.substring(4); // "enum"の4文字を除去
                    type.append(enumTypeName);
                    continue;
                }
                // 通常の型指定子の処理
                if (typeSpec.trailingTypeSpecifier() != null) {
                    processTrailingTypeSpecifier(typeSpec.trailingTypeSpecifier(), type);
                }
            }
            // デバッグ出力
            System.out.println("DEBUG: Processing type specifier: " + spec.getText());
        }

        String result = type.toString().trim();
        System.out.println("DEBUG: Extracted type name: " + result);
        return result;
    }

    private void processTrailingTypeSpecifier(CPP14Parser.TrailingTypeSpecifierContext ctx, StringBuilder type) {
        if (ctx.simpleTypeSpecifier() != null) {
            var simple = ctx.simpleTypeSpecifier();

            // 名前空間の処理
            if (simple.nestedNameSpecifier() != null) {
                type.append(simple.nestedNameSpecifier().getText());
            }

            // 基本型かユーザー定義型の処理
            if (simple.theTypeName() != null) {
                type.append(simple.theTypeName().getText());
            } else {
                // int, double などの基本型の処理
                String basicType = simple.getText().trim();
                if (!basicType.isEmpty()) {
                    type.append(basicType);
                }
            }

            // テンプレートパラメータの処理
            if (simple.simpleTemplateId() != null) {
                processTemplateId(simple.simpleTemplateId(), type);
            }
        }
    }

    private void processTemplateId(CPP14Parser.SimpleTemplateIdContext ctx, StringBuilder type) {
        type.append("<");
        if (ctx.templateArgumentList() != null) {
            type.append(ctx.templateArgumentList().getText());
        }
        type.append(">");
    }

    private String processAttributeType(String type, CPP14Parser.DeclaratorContext declarator) {
        StringBuilder processedType = new StringBuilder();

        System.err.println("タイプ！！: " + type);
        // テンプレート部分を保持しながら処理
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

        // 標準型の定義（スマートポインタを含む）
        Map<String, String> standardTypes = Map.of(
                "string", "String",
                "vector", "Vector",
                "list", "List",
                "map", "Map",
                "set", "Set",
                "array", "Array",
                "shared_ptr", "sharedptr", // スペースなし
                "unique_ptr", "uniqueptr", // スペースなし
                "unordered_map", "unorderedmap",
                "weak_ptr", "weakptr");

        // 基本型の変換
        if (standardTypes.containsKey(type)) {
            type = standardTypes.get(type);
        }

        processedType.append(type);

        // テンプレート部分の処理
        if (!templatePart.isEmpty()) {
            // テンプレート内のstd::を除去
            templatePart = templatePart.replaceAll("std::", "");

            // テンプレート内の標準型を変換
            for (Map.Entry<String, String> entry : standardTypes.entrySet()) {
                templatePart = templatePart.replaceAll(
                        "\\b" + entry.getKey() + "\\b",
                        entry.getValue());
            }

            processedType.append(templatePart);
        }

        // ポインタ/参照の処理（コレクション型でない場合のみ）
        String declaratorText = declarator.getText();
        if (!isCollectionType(processedType.toString()) && !isSmartPointer(processedType.toString())) {
            if (declaratorText.contains("*") || type.contains("*")) {
                processedType.append("*");
            }
            if (declaratorText.contains("&") || type.contains("&")) {
                processedType.append("&");
            }
        }

        // 配列サイズの処理
        if (declaratorText.matches(".*\\[\\d+\\]")) {
            String size = declaratorText.replaceAll(".*\\[(\\d+)\\].*", "[$1]");
            processedType.append(size);
        }

        return processedType.toString();
    }

    private String extractAttributeName(CPP14Parser.DeclaratorContext declarator) {
        String name = declarator.getText();
        return name.replaceAll("\\[.*\\]", "").trim();
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

    private boolean isUserDefinedType(String type) {
        Set<String> basicTypes = Set.of(
                "void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed",
                "string", "vector", "list", "map", "set",
                "array", "queue", "stack", "deque");

        if (type.startsWith("enum ")) {
            return true; // enumは常にユーザー定義型として扱う
        }

        // std::を除去
        type = type.replaceAll("std::", "");

        // 型名のみを抽出（テンプレートパラメータを除く）
        if (type.contains("<")) {
            type = type.substring(0, type.indexOf("<"));
        }

        return !basicTypes.contains(type.toLowerCase());
    }

    private boolean isCollectionType(String type) {
        // 型名からテンプレート部分を含めて判定
        return type.matches(".*(?:vector|list|set|map|array|queue|stack|deque)<.*>");
    }

    private String extractElementType(String type) {
        try {
            // テンプレート引数を抽出
            if (type.contains("<") && type.contains(">")) {
                // std:: の除去
                String cleaned = type.replaceAll("std::", "");
                // array<Type, N> の場合は最初の型引数のみを取得
                if (type.contains("array<")) {
                    String templateArgs = cleaned.replaceAll(".*?<(.+?)\\s*,.*>.*", "$1");
                    return extractBaseTypeName(templateArgs);
                }
                // テンプレート引数の抽出（最も外側のみ）
                String templateArgs = cleaned.replaceAll(".*?<(.+)>.*", "$1");

                System.out.println("Collection type processing:");
                System.out.println("  Collection type: " + templateArgs);

                // 要素の型を抽出（ポインタ/参照を除去）
                String elementType = templateArgs.replaceAll("[*&]", "").trim();
                System.out.println("  Element type: " + elementType);

                return elementType;
            }
            return extractBaseTypeName(type);
        } catch (Exception e) {
            System.err.println("Error processing collection type: " + e.getMessage());
            return type;
        }
    }

    private boolean isMethodDeclaration(CPP14Parser.MemberDeclaratorListContext memberDecList) {
        if (memberDecList == null)
            return false;

        for (CPP14Parser.MemberDeclaratorContext memberDec : memberDecList.memberDeclarator()) {
            if (memberDec.declarator() == null)
                continue;

            if (memberDec.declarator().parametersAndQualifiers() != null) {
                return true;
            }

            CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
            if (declarator.pointerDeclarator() != null) {
                var noPointerDec = declarator.pointerDeclarator().noPointerDeclarator();
                if (noPointerDec != null && noPointerDec.parametersAndQualifiers() != null) {
                    return true;
                }
            }

            if (declarator.pointerDeclarator() != null &&
                    declarator.getText().contains("(*)")) {
                return true;
            }
        }
        return false;
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
}