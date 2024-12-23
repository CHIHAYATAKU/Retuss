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

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AttributeAnalyzer extends AbstractAnalyzer {
    private static class TypeInfo {
        String baseType;
        Set<Modifier> modifiers = new HashSet<>();
        boolean isSmartPointer;
        String smartPointerType;
        String innerType;
        boolean isStdString;
        boolean isCollection;
        String collectionType;
        String elementType;
        boolean isPointer;
        boolean isReference;
    }

    private static class DeclaratorInfo {
        String name;
        boolean isPointer;
        boolean isReference;
        String arraySpec;
        String initialValue;
    }

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
            TypeInfo typeInfo = extractTypeInfo(ctx.declSpecifierSeq());

            if (ctx.memberDeclaratorList() != null) {
                for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                    handleAttribute(memberDec, typeInfo);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in attribute analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private TypeInfo extractTypeInfo(CPP14Parser.DeclSpecifierSeqContext ctx) {
        TypeInfo info = new TypeInfo();

        for (CPP14Parser.DeclSpecifierContext spec : ctx.declSpecifier()) {
            if (spec.storageClassSpecifier() != null) {
                // ストレージクラス指定子
                processStorageClassSpecifier(spec.storageClassSpecifier(), info);
            } else if (spec.typeSpecifier() != null) {
                // 型指定子
                processTypeSpecifier(spec.typeSpecifier(), info);
            } else {
                // その他の修飾子
                processOtherSpecifier(spec.getText(), info);
            }
        }
        return info;
    }

    private void processStorageClassSpecifier(
            CPP14Parser.StorageClassSpecifierContext ctx,
            TypeInfo info) {
        // ストレージ期間と連結を指定する修飾子
        String specifier = ctx.getText();
        switch (specifier) {
            case "static": // 静的記憶域期間
                info.modifiers.add(Modifier.STATIC);
                break;
            case "extern": // 外部連結
                info.modifiers.add(Modifier.EXTERN);
                break;
            case "mutable": // const内での可変性
                info.modifiers.add(Modifier.MUTABLE);
                break;
            case "register": // レジスタ記憶域（非推奨）
                info.modifiers.add(Modifier.REGISTER);
                break;
            case "thread_local": // スレッドローカルストレージ
                info.modifiers.add(Modifier.THREAD_LOCAL);
                break;
        }
    }

    private void processOtherSpecifier(String specifier, TypeInfo info) {
        switch (specifier) {
            // cv修飾子（const/volatile）
            case "const":
                info.modifiers.add(Modifier.READONLY);
                break;
            case "volatile":
                info.modifiers.add(Modifier.VOLATILE);
                break;

            // 関数指定子
            case "virtual":
                info.modifiers.add(Modifier.VIRTUAL);
                break;
            case "explicit":
                info.modifiers.add(Modifier.EXPLICIT);
                break;
            case "inline":
                info.modifiers.add(Modifier.INLINE);
                break;
            case "constexpr":
                info.modifiers.add(Modifier.CONSTEXPR);
                break;
            case "consteval":
                info.modifiers.add(Modifier.CONSTEVAL);
                break;
            case "constinit":
                info.modifiers.add(Modifier.CONSTINIT);
                break;

            // 特殊指定子
            case "friend":
                info.modifiers.add(Modifier.FRIEND);
                break;
            case "final":
                info.modifiers.add(Modifier.FINAL);
                break;
            case "override":
                info.modifiers.add(Modifier.OVERRIDE);
                break;
            case "noexcept":
                info.modifiers.add(Modifier.NOEXCEPT);
                break;
        }
    }

    private void processTypeQualifiers(CPP14Parser.DeclSpecifierContext spec, TypeInfo info) {
        String text = spec.getText();
        if (text.equals("const")) {
            info.modifiers.add(Modifier.READONLY);
        } else if (text.equals("volatile")) {
            info.modifiers.add(Modifier.VOLATILE);
        }
    }

    private void processTypeSpecifier(
            CPP14Parser.TypeSpecifierContext ctx,
            TypeInfo info) {
        String specText = ctx.getText();

        // 修飾子の処理
        if (specText.equals("const")) {
            info.modifiers.add(Modifier.READONLY);
        } else if (specText.equals("volatile")) {
            info.modifiers.add(Modifier.VOLATILE);
        }

        // 型情報の処理
        if (ctx.trailingTypeSpecifier() != null &&
                ctx.trailingTypeSpecifier().simpleTypeSpecifier() != null) {

            CPP14Parser.SimpleTypeSpecifierContext simpleType = ctx.trailingTypeSpecifier().simpleTypeSpecifier();

            if (isStdNamespace(simpleType)) {
                processStdType(simpleType, info);
            } else {
                info.baseType = extractBaseType(simpleType);
            }
        }
    }

    private void processStdType(
            CPP14Parser.SimpleTypeSpecifierContext simpleType,
            TypeInfo info) {
        if (simpleType.theTypeName() != null &&
                simpleType.theTypeName().className() != null) {

            CPP14Parser.ClassNameContext className = simpleType.theTypeName().className();

            if (className.simpleTemplateId() != null) {
                processTemplateType(className.simpleTemplateId(), info);
            } else if (className.getText().equals("string")) {
                info.isStdString = true;
                info.baseType = "string";
            }
        }
    }

    private void processTemplateType(
            CPP14Parser.SimpleTemplateIdContext templateId,
            TypeInfo info) {
        String templateName = templateId.templateName().getText();

        // スマートポインタのチェック
        if (isSmartPointerType(templateName)) {
            info.isSmartPointer = true;
            info.smartPointerType = templateName;
            info.innerType = extractTemplateArgument(templateId);
        }
        // コレクション型のチェック
        else if (isCollectionType(templateName)) {
            info.isCollection = true;
            info.collectionType = templateName;
            info.elementType = extractTemplateArgument(templateId);
        }
    }

    // ヘルパーメソッド
    private boolean isStdNamespace(CPP14Parser.SimpleTypeSpecifierContext ctx) {
        return ctx.nestedNameSpecifier() != null &&
                ctx.nestedNameSpecifier().theTypeName() != null &&
                ctx.nestedNameSpecifier().theTypeName().className() != null &&
                ctx.nestedNameSpecifier().theTypeName().className().getText().equals("std");
    }

    private boolean isSmartPointerType(String type) {
        return type.equals("unique_ptr") ||
                type.equals("shared_ptr") ||
                type.equals("weak_ptr");
    }

    private boolean isCollectionType(String type) {
        return type.equals("vector") || type.equals("list") ||
                type.equals("set") || type.equals("map") ||
                type.equals("array");
    }

    private DeclaratorInfo processDeclarator(CPP14Parser.DeclaratorContext ctx) {
        DeclaratorInfo info = new DeclaratorInfo();

        if (ctx.pointerDeclarator() != null) {
            CPP14Parser.PointerDeclaratorContext ptrDec = ctx.pointerDeclarator();

            // ポインタ演算子の処理
            for (CPP14Parser.PointerOperatorContext op : ptrDec.pointerOperator()) {
                if (op.getText().equals("*")) {
                    info.isPointer = true;
                }
                if (op.getText().equals("&")) {
                    info.isReference = true;
                }
            }

            // 名前の抽出
            if (ptrDec.noPointerDeclarator() != null) {
                info.name = extractDeclaratorName(ptrDec.noPointerDeclarator());
                info.arraySpec = extractArraySpecifier(ptrDec.noPointerDeclarator());
            }
        }

        return info;
    }

    private String extractDeclaratorName(CPP14Parser.NoPointerDeclaratorContext ctx) {
        if (ctx.declaratorid() != null &&
                ctx.declaratorid().idExpression() != null &&
                ctx.declaratorid().idExpression().unqualifiedId() != null) {
            return ctx.declaratorid().idExpression().unqualifiedId().getText();
        }
        return "";
    }

    private String extractArraySpecifier(CPP14Parser.NoPointerDeclaratorContext ctx) {
        if (ctx.constantExpression() != null) {
            return "[" + ctx.constantExpression().getText() + "]";
        }
        return null;
    }

    private String extractTemplateArgument(CPP14Parser.SimpleTemplateIdContext ctx) {
        if (ctx.templateArgumentList() != null) {
            // 最初のテンプレート引数を取得
            var args = ctx.templateArgumentList().templateArgument();
            if (!args.isEmpty()) {
                var arg = args.get(0);
                if (arg.theTypeId() != null &&
                        arg.theTypeId().typeSpecifierSeq() != null) {
                    return extractTypeFromSpecifierSeq(
                            arg.theTypeId().typeSpecifierSeq());
                }
            }
        }
        return null;
    }

    private String extractTypeFromSpecifierSeq(
            CPP14Parser.TypeSpecifierSeqContext ctx) {
        StringBuilder type = new StringBuilder();
        for (CPP14Parser.TypeSpecifierContext spec : ctx.typeSpecifier()) {
            if (spec.trailingTypeSpecifier() != null &&
                    spec.trailingTypeSpecifier().simpleTypeSpecifier() != null) {

                type.append(spec.trailingTypeSpecifier()
                        .simpleTypeSpecifier()
                        .getText());
            }
        }
        return type.toString();
    }

    private String extractInitialValue(
            CPP14Parser.BraceOrEqualInitializerContext ctx) {
        if (ctx.initializerClause() != null) {
            return processInitializerClause(ctx.initializerClause());
        }
        if (ctx.bracedInitList() != null) {
            return processBracedInitList(ctx.bracedInitList());
        }
        return null;
    }

    private String processInitializerClause(
            CPP14Parser.InitializerClauseContext ctx) {
        if (ctx.assignmentExpression() != null) {
            return ctx.assignmentExpression().getText();
        }
        if (ctx.bracedInitList() != null) {
            return processBracedInitList(ctx.bracedInitList());
        }
        return ctx.getText();
    }

    private String processBracedInitList(CPP14Parser.BracedInitListContext ctx) {
        if (ctx.initializerList() != null) {
            return "{" + ctx.initializerList().getText() + "}";
        }
        return "{}";
    }

    private static final Set<String> BASIC_TYPES = Set.of(
            "void", "bool", "char", "int", "float", "double",
            "long", "short", "unsigned", "signed");

    private static final Set<String> STD_CONTAINERS = Set.of(
            "vector", "list", "set", "map", "array",
            "queue", "stack", "deque");

    private static final Set<String> SMART_PTRS = Set.of(
            "unique_ptr", "shared_ptr", "weak_ptr");

    private void handleAttribute(
            CPP14Parser.MemberDeclaratorContext memberDec,
            TypeInfo typeInfo) {
        DeclaratorInfo declInfo = processDeclarator(memberDec.declarator());

        // 初期値の処理
        if (memberDec.braceOrEqualInitializer() != null) {
            declInfo.initialValue = extractInitialValue(
                    memberDec.braceOrEqualInitializer());
        }

        // 属性の作成と設定
        Attribute attribute = createAttribute(typeInfo, declInfo);

        // クラスへの属性追加
        CppHeaderClass currentClass = context.getCurrentHeaderClass();
        currentClass.addAttribute(attribute);

        // 修飾子の追加
        for (Modifier mod : typeInfo.modifiers) {
            currentClass.addMemberModifier(declInfo.name, mod);
        }

        // 関係性の解析と追加
        if (shouldCreateRelationship(typeInfo)) {
            addRelationship(typeInfo, declInfo);
        }
    }

    private Attribute createAttribute(TypeInfo typeInfo, DeclaratorInfo declInfo) {
        Attribute attribute = new Attribute(new Name(declInfo.name));

        // 型の設定
        String fullType = buildFullType(typeInfo, declInfo);
        attribute.setType(new Type(fullType));

        // 可視性の設定
        attribute.setVisibility(
                convertVisibility(context.getCurrentVisibility()));

        // 初期値の設定
        if (declInfo.initialValue != null) {
            attribute.setDefaultValue(
                    new DefaultValue(new OneIdentifier(declInfo.initialValue)));
        }

        return attribute;
    }

    private void addRelationship(TypeInfo typeInfo, DeclaratorInfo declInfo) {
        CppHeaderClass currentClass = context.getCurrentHeaderClass();

        RelationType relationType = determineRelationType(typeInfo);
        String targetType = determineTargetType(typeInfo);
        String multiplicity = determineMultiplicity(typeInfo, declInfo);

        RelationshipInfo relation = new RelationshipInfo(targetType, relationType);
        relation.addElement(
                declInfo.name,
                ElementType.ATTRIBUTE,
                multiplicity,
                convertVisibility(context.getCurrentVisibility()));

        currentClass.addRelationship(relation);
    }

    private RelationType determineRelationType(TypeInfo typeInfo) {
        if (typeInfo.isSmartPointer) {
            return typeInfo.smartPointerType.equals("unique_ptr") ? RelationType.COMPOSITION : RelationType.ASSOCIATION;
        }
        if (typeInfo.isCollection) {
            return RelationType.COMPOSITION;
        }
        if (typeInfo.isPointer || typeInfo.isReference) {
            return RelationType.ASSOCIATION;
        }
        return RelationType.COMPOSITION;
    }

    private String determineMultiplicity(TypeInfo typeInfo, DeclaratorInfo declInfo) {
        if (typeInfo.isCollection)
            return "*";
        if (typeInfo.isSmartPointer) {
            switch (typeInfo.smartPointerType) {
                case "unique_ptr":
                    return "0..1";
                case "shared_ptr":
                    return "0..*";
                case "weak_ptr":
                    return "0..*";
            }
        }
        if (declInfo.isPointer)
            return "0..1";
        if (declInfo.isReference)
            return "1";
        return "1";
    }

    private String cleanName(String name) {
        return name.replaceAll("[*&]", "").trim();
    }

    private String extractInitialValue(CPP14Parser.MemberDeclaratorContext memberDec) {
        try {
            // braceOrEqualInitializerの処理
            if (memberDec.braceOrEqualInitializer() != null) {
                CPP14Parser.BraceOrEqualInitializerContext initContext = memberDec.braceOrEqualInitializer();
                return cleanInitialValue(initContext.getText());
            }

            // virualInitializerの処理
            if (memberDec.virtualSpecifierSeq() != null) {
                return null; // virtual指定子は初期値ではない
            }

            // pureSpecifierの処理（純粋仮想関数の場合）
            if (memberDec.pureSpecifier() != null) {
                return null; // pure指定子は初期値ではない
            }

            return null;
        } catch (Exception e) {
            System.err.println("Error extracting initial value: " + e.getMessage());
            return null;
        }
    }

    private String cleanInitialValue(String value) {
        if (value == null)
            return null;

        // 基本的なクリーンアップ
        value = value
                .replaceFirst("^=\\s*", "") // 先頭の = を除去
                .trim();

        // ブレース初期化のクリーンアップ
        if (value.startsWith("{") && value.endsWith("}")) {
            value = value.replaceFirst("^\\{\\s*(.+?)\\s*\\}$", "{$1}");
        }

        return value;
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

    private String extractInnerType(String type) {
        if (type.contains("<") && type.contains(">")) {
            return type.replaceAll(".*<(.+)>.*", "$1")
                    .replaceAll("\\s+", "")
                    .replaceAll("std::", "");
        }
        return type;
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

        String elementType = extractElementType(type);
        String multiplicity = determineCollectionMultiplicity(type, declaratorText);

        System.out.println("Processing collection:");
        System.out.println("  Collection type: " + type);
        System.out.println("  Element type: " + elementType);
        System.out.println("  Multiplicity: " + multiplicity);

        if (isUserDefinedType(elementType)) {
            RelationshipInfo relation = new RelationshipInfo(
                    elementType,
                    relationType);
            relation.addElement(
                    attributeName,
                    ElementType.ATTRIBUTE,
                    multiplicity, // 固定サイズか可変サイズかを反映
                    visibility);
            currentClass.addRelationship(relation);
        }
    }

    private Set<Modifier> extractModifiers(String type) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        if (type.contains(Modifier.STATIC.getCppText(false)))
            modifiers.add(Modifier.STATIC);
        if (type.contains(Modifier.READONLY.getCppText(false)))
            modifiers.add(Modifier.READONLY);
        if (type.contains(Modifier.MUTABLE.getCppText(false)))
            modifiers.add(Modifier.MUTABLE);
        if (type.contains(Modifier.FINAL.getCppText(false)))
            modifiers.add(Modifier.FINAL);
        return modifiers;
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
                "unique_ptr", "unique_ptr",
                "shared_ptr", "shared_ptr",
                "weak_ptr", "weak_ptr");

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

    private String cleanTypeModifiers(String type) {
        // テンプレート部分と基本型を分離
        int templateStart = type.indexOf('<');
        if (templateStart != -1) {
            int templateEnd = type.lastIndexOf('>');
            if (templateEnd != -1) {
                // テンプレート前の部分のみ修飾子を除去
                String baseType = type.substring(0, templateStart);
                baseType = removeModifiers(baseType);

                // テンプレート部分はそのまま保持
                String templatePart = type.substring(templateStart);

                return baseType + templatePart;
            }
        }

        // テンプレートがない場合は通常の処理
        return removeModifiers(type);
    }

    private String removeModifiers(String text) {
        // 単語境界を使用して修飾子を確実に識別
        return text
                .replaceAll("\\s+", " ")
                .replaceAll(
                        "(static|constexpr|const|mutable|volatile|virtual|final|explicit|friend|inline|thread_local|register|extern)",
                        "")
                .replaceAll("\\s+", " ")
                .trim();
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
                "thread_local", "register", "extern"
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

        return !basicTypes.contains(type) &&
                !type.startsWith("std::") &&
                Character.isUpperCase(type.charAt(0));
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

    private String extractBaseType(CPP14Parser.SimpleTypeSpecifierContext ctx) {
        // 基本型の抽出
        if (ctx.theTypeName() != null) {
            return ctx.theTypeName().getText();
        }
        return ctx.getText();
    }

    private String buildFullType(TypeInfo typeInfo, DeclaratorInfo declInfo) {
        StringBuilder type = new StringBuilder();
        type.append(typeInfo.baseType);

        if (typeInfo.isSmartPointer) {
            type.append("<").append(typeInfo.innerType).append(">");
        }

        if (declInfo.isPointer) {
            type.append("*");
        }
        if (declInfo.isReference) {
            type.append("&");
        }
        if (declInfo.arraySpec != null) {
            type.append(declInfo.arraySpec);
        }
        return type.toString();
    }

    private boolean shouldCreateRelationship(TypeInfo typeInfo) {
        return typeInfo.isSmartPointer ||
                typeInfo.isCollection ||
                isUserDefinedType(typeInfo.baseType);
    }

    private String determineTargetType(TypeInfo typeInfo) {
        if (typeInfo.isSmartPointer) {
            return typeInfo.innerType;
        }
        if (typeInfo.isCollection) {
            return typeInfo.elementType;
        }
        return typeInfo.baseType;
    }
}