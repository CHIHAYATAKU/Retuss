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
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Method;

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
            Boolean isPointer = false;
            Boolean isRef = false;
            // memberDeclaratorListのnullチェック
            if (ctx.memberDeclaratorList() == null ||
                    ctx.memberDeclaratorList().memberDeclarator() == null ||
                    ctx.memberDeclaratorList().memberDeclarator().isEmpty()) {
                return;
            }

            for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                // declaratorのnullチェック
                if (memberDec.declarator() == null) {
                    continue;
                }

                // pointerDeclaratorのnullチェック
                if (memberDec.declarator().pointerDeclarator() == null) {
                    continue;
                }

                var noPtrDec = memberDec.declarator().pointerDeclarator().noPointerDeclarator();
                if (noPtrDec == null) {
                    System.err.println("DEBUG: noPtrDec is null");
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

                // 現在のノードの配列サイズをチェック
                String size = null;
                if (noPtrDec.constantExpression() != null) {
                    size = noPtrDec.constantExpression().getText();
                    System.err.println("DEBUG: Found size in current node: " + size);
                }

                // // 子ノードを確認
                // if (noPtrDec.noPointerDeclarator() != null) {
                // var childNoPtrDec = noPtrDec.noPointerDeclarator();
                // System.err.println("DEBUG: Found child NoPointerDeclarator: " +
                // childNoPtrDec.getText());

                // // 子ノードのconstantExpressionを確認
                // if (childNoPtrDec.constantExpression() != null) {
                // String childSize = childNoPtrDec.constantExpression().getText();
                // System.err.println("DEBUG: Found size in child node: " + childSize);
                // sizes.add(childSize);
                // }

                // // さらに深い階層も確認
                // for (int i = 0; i < childNoPtrDec.getChildCount(); i++) {
                // ParseTree child = childNoPtrDec.getChild(i);
                // System.err.println("DEBUG: Child node structure:");
                // System.err.println(" - Type: " + child.getClass().getSimpleName());
                // System.err.println(" - Text: " + child.getText());
                // }
                // }

                // サイズが見つかった場合、ArrayInfoを更新
                // if (size != null) {
                // System.err.println("DEBUG: Processing found size: " + size);
                // StringBuilder dimensions = new StringBuilder();

                // dimensions.append("[").append(size).append("]");

                // arrayInfo.dimensions = dimensions.toString();
                // System.err.println("DEBUG: Final dimensions string: " +
                // arrayInfo.dimensions);

                // int total = sizes.stream()
                // .mapToInt(Integer::parseInt)
                // .reduce(1, (a, b) -> a * b);
                // arrayInfo.multiplicity = String.valueOf(total);
                // System.err.println("DEBUG: Final multiplicity: " + arrayInfo.multiplicity);
                // }

                // 一時変数を使用して値を設定
                String attributeName = null;
                // 配列変数の場合、最初の子ノードに変数名が含まれている
                if (noPtrDec.getChildCount() > 0 &&
                        noPtrDec.getChild(0) instanceof CPP14Parser.NoPointerDeclaratorContext) {
                    var firstChild = (CPP14Parser.NoPointerDeclaratorContext) noPtrDec.getChild(0);
                    System.err.println("DEBUG: First child content: " + firstChild.getText());

                    // declaratoridから変数名を取得
                    if (firstChild.declaratorid() != null &&
                            firstChild.declaratorid().idExpression() != null &&
                            firstChild.declaratorid().idExpression().unqualifiedId() != null) {
                        attributeName = firstChild.declaratorid().idExpression().unqualifiedId().getText();
                        System.err.println("DEBUG: Found attribute name from first child: " + attributeName);
                    }
                } else {
                    // 通常の変数の場合（配列でない場合）
                    if (noPtrDec.declaratorid() != null &&
                            noPtrDec.declaratorid().idExpression() != null &&
                            noPtrDec.declaratorid().idExpression().unqualifiedId() != null) {
                        attributeName = noPtrDec.declaratorid().idExpression().unqualifiedId().getText();
                        System.err.println("DEBUG: Found attribute name directly: " + attributeName);
                    }
                }

                // 変数名が取得できなかった場合は処理を中断
                if (attributeName == null) {
                    System.err.println("DEBUG: Could not extract attribute name");
                    continue;
                }

                // 属性名の重複チェック
                for (Attribute attr : currentHeaderClass.getAttributeList()) {
                    if (attr.getName().getNameText().equals(attributeName)) {
                        System.err.println("DEBUG: Duplicate attribute name found: " + attributeName);
                        continue;
                    }
                }

                // ポインタか参照の抽出
                if (memberDec.declarator() != null &&
                        memberDec.declarator().pointerDeclarator() != null) {

                    var ptrDec = memberDec.declarator().pointerDeclarator();
                    // pointerOperatorのリストを取得
                    List<CPP14Parser.PointerOperatorContext> operators = ptrDec.pointerOperator();

                    if (operators != null && !operators.isEmpty()) {
                        // 各演算子をチェック
                        for (var op : operators) {
                            String opText = op.getText().trim();
                            if (opText.equals("*")) {
                                isPointer = true;
                                break; // 最初のポインタ指定を見つけたら終了
                            } else if (opText.equals("&")) {
                                isRef = true;
                                break; // 最初の参照指定を見つけたら終了
                            }
                        }
                    }
                }

                // 初期値の抽出
                String defaultValue = extractInitialValue(memberDec);

                // 修飾子の抽出
                Set<Modifier> modifiers = extractModifiers(ctx.declSpecifierSeq());

                // 型と関係性抽出
                System.err.println("DEBUG before type processing:");
                Object[] result = processTypeAndRelationship(ctx.declSpecifierSeq(), isPointer, isRef,
                        currentHeaderClass, attributeName,
                        this.context.getCurrentVisibility());

                // Attributeの生成と追加
                if (result != null) {
                    String typeName = (String) result[0];
                    System.err.println("DEBUG: Base type name: " + typeName);
                    // 型名に配列の次元情報を追加
                    if (size != null) {
                        typeName = typeName + "[" + size + "]";
                    }
                    System.err.println("DEBUG: Type name with dimensions: " + typeName);

                    Attribute attribute = new Attribute(new Name(attributeName));
                    attribute.setVisibility(convertVisibility(this.context.getCurrentVisibility()));
                    attribute.setType(new Type(typeName));
                    // デバッグ出力を追加
                    System.err.println("DEBUG: Created attribute:");
                    System.err.println(" - Name: " + attribute.getName().getNameText());
                    System.err.println(" - Type: " + attribute.getType().toString());
                    System.err.println(" - Visibility: " + attribute.getVisibility());

                    if (defaultValue != null)
                        attribute.setDefaultValue(new DefaultValue(new OneIdentifier(defaultValue)));

                    currentHeaderClass.addAttribute(attribute);
                    System.err.println("DEBUG: Added attribute to class");

                    // 修飾子の追加
                    for (Modifier modifier : modifiers) {
                        currentHeaderClass.addMemberModifier(attributeName, modifier);
                    }
                    // 関係性があれば追加
                    if (result[1] != null) {

                        RelationshipInfo relationshipInfo = (RelationshipInfo) result[1];
                        // アノテーションの取得と設定
                        String relationshipAnnotation = extractRelationshipAnnotation(memberDec);
                        if (relationshipAnnotation != null) {
                            switch (relationshipAnnotation) {
                                case "aggregation":
                                    relationshipInfo.setType(RelationType.AGGREGATION);
                                case "composition":
                                    relationshipInfo.setType(RelationType.COMPOSITION);
                                case "association":
                                    relationshipInfo.setType(RelationType.ASSOCIATION);
                            }
                        }

                        if (size != null) {
                            relationshipInfo.getElement().setMultiplicity(size);
                            System.err.println("DEBUG: Set relationship multiplicity: " + size);
                        }
                        currentHeaderClass.addRelationship(relationshipInfo);

                        System.err.println(" - Relationship added");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in attribute analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // private void handleAttribute(
    // CPP14Parser.MemberDeclaratorContext memberDec,
    // String type,
    // Set<Modifier> modifiers) {

    // CppHeaderClass currentHeaderClass = context.getCurrentHeaderClass();
    // String attributeName =
    // cleanName(extractAttributeName(memberDec.declarator()));
    // String initialValueStr = extractInitialValue(memberDec);
    // System.err.println("DEBUG: InitialValue of -> " + attributeName + " = " +
    // initialValueStr);

    // if (currentHeaderClass.getAttributeList().stream()
    // .anyMatch(attr -> attr.getName().getNameText().equals(attributeName))) {
    // return;
    // }

    // Attribute attribute = new Attribute(new Name(attributeName));
    // attribute.setType(new Type(type));
    // attribute.setVisibility(convertVisibility(context.getCurrentVisibility()));
    // // 初期値の設定
    // if (initialValueStr != null) {
    // // 数値の場合
    // if (initialValueStr.matches("-?\\d+(\\.\\d+)?")) {
    // attribute.setDefaultValue(new DefaultValue(new
    // OneIdentifier(initialValueStr)));
    // }
    // // 文字列の場合
    // else if (initialValueStr.startsWith("\"") && initialValueStr.endsWith("\""))
    // {
    // attribute.setDefaultValue(new DefaultValue(new
    // OneIdentifier(initialValueStr)));
    // }
    // // メソッド呼び出しや式の場合
    // else if (initialValueStr.contains("(") || initialValueStr.contains("+") ||
    // initialValueStr.contains("-") || initialValueStr.contains("*") ||
    // initialValueStr.contains("/")) {
    // // とりあえず単純な識別子として処理
    // attribute.setDefaultValue(new DefaultValue(new
    // OneIdentifier(initialValueStr)));
    // }
    // // その他の場合（変数名など）
    // else {
    // attribute.setDefaultValue(new DefaultValue(new
    // OneIdentifier(initialValueStr)));
    // }
    // System.out.println("Debug - Attribute: " + attributeName);
    // System.out.println(" - Type: " + attribute.getType().toString());
    // System.out.println(" - Default Value: " + attribute.getDefaultValue());
    // System.out.println(" - Default Value Class: " +
    // attribute.getDefaultValue().getClass());
    // }

    // currentHeaderClass.addAttribute(attribute);
    // for (Modifier modifier : modifiers) {
    // currentHeaderClass.addMemberModifier(attributeName, modifier);
    // }

    // // 型の関係解析
    // analyzeAttributeTypeRelationship(type, memberDec.declarator(), attributeName,
    // memberDec);
    // }

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

        // アノテーションの取得
        String relationshipAnnotation = extractRelationshipAnnotation(memberDec);
        System.out.println("Relationship annotation: " + relationshipAnnotation);

        CppHeaderClass currentClass = context.getCurrentHeaderClass();
        Visibility visibility = convertVisibility(context.getCurrentVisibility());
        Set<Modifier> mods = currentClass.getModifiers(attributeName);

        RelationType relationType = determineRelationshipType(type, declaratorText, relationshipAnnotation);

        // コレクション型の処理
        if (isCollectionType(type)) {
            System.out.println("DEBUG: type: " + type);
            handleCollectionRelationship(
                    type, attributeName, visibility, mods,
                    currentClass, relationType, declaratorText);
            return;
        }

        String multiplicity = determineMultiplicity(type, declaratorText);

        RelationshipInfo relation = new RelationshipInfo(cleanType, relationType);
        relation.setElement(
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
                case "association":
                    return RelationType.ASSOCIATION;
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
                String cleanType = extractBaseTypeName(paramType);
                RelationshipInfo relation = new RelationshipInfo(
                        cleanType,
                        determineRelationType(info));
                relation.setElement(
                        attributeName,
                        ElementType.ATTRIBUTE,
                        info.getMultiplicity(),
                        visibility);
                currentClass.addRelationship(relation);
            }
        }
    }

    private RelationType determineRelationType(CollectionTypeInfo info) {
        switch (info.getBaseType()) {
            case "uniqueptr":
                return RelationType.COMPOSITION;
            case "sharedptr":
            case "weakptr":
                return RelationType.ASSOCIATION;
            case "vector":
            case "unorderedmap":
                return RelationType.COMPOSITION; // コレクションは所有関係
            default:
                return RelationType.ASSOCIATION;
        }
    }

    private CollectionTypeInfo parseCollectionType(String type) {
        CollectionTypeInfo info = new CollectionTypeInfo();

        // 複雑なテンプレートの解析
        if (type.contains("<")) {
            String baseType = type.substring(0, type.indexOf("<"));
            info.setBaseType(baseType.replaceAll("std::", ""));

            // テンプレートパラメータの解析
            String params = extractTemplateParameters(type);
            System.out.println("DEBUG: params " + params);
            List<String> paramTypes = parseTemplateParameters(params);
            info.getParameterTypes().addAll(paramTypes);
        }
        return info;
    }

    private String extractTemplateParameters(String type) {
        // "<" と ">" の対応を考慮したパラメータ抽出
        int nestLevel = 0;
        int start = -1;

        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<') {
                nestLevel++;
                if (start == -1)
                    start = i + 1;
            } else if (c == '>') {
                nestLevel--;
                if (nestLevel == 0) {
                    return type.substring(start, i);
                }
            }
        }
        return "";
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

    private String extractInnerType(String type) {
        int start = type.indexOf("<") + 1;
        int end = type.lastIndexOf(">");
        return type.substring(start, end).trim().replaceAll("std::", "");
    }

    private Set<Modifier> extractModifiers(CPP14Parser.DeclSpecifierSeqContext ctx) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        if (ctx == null || ctx.declSpecifier() == null)
            return modifiers;

        for (CPP14Parser.DeclSpecifierContext spec : ctx.declSpecifier()) {
            // ストレージクラス指定子の処理
            if (spec.storageClassSpecifier() != null) {
                String storage = spec.storageClassSpecifier().getText();
                switch (storage) {
                    case "static":
                        modifiers.add(Modifier.STATIC);
                        break;
                    case "mutable":
                        modifiers.add(Modifier.MUTABLE);
                        break;
                    default:
                        break;
                }
                continue;
            }
            // 型指定子の処理
            if (spec.typeSpecifier() != null && spec.typeSpecifier().trailingTypeSpecifier() != null
                    && spec.typeSpecifier()
                            .trailingTypeSpecifier().cvQualifier() != null) {
                String typeSpecifier = spec.typeSpecifier().trailingTypeSpecifier().cvQualifier().getText();
                if ("const".equals(typeSpecifier)) {
                    modifiers.add(Modifier.READONLY);
                }
                continue;
            }
            // その他の指定子（constexpr等）
            if (spec != null && spec.getText().equals("constexpr")) {
                modifiers.add(Modifier.READONLY);
            }
        }
        return modifiers;
    }

    private Object[] processTypeAndRelationship(
            CPP14Parser.DeclSpecifierSeqContext ctx,
            Boolean isPointer,
            Boolean isRef,
            CppHeaderClass currentClass,
            String attributeName,
            String currentVisibility) {

        if (ctx == null || ctx.declSpecifier() == null)
            return new Object[] { null };

        for (CPP14Parser.DeclSpecifierContext spec : ctx.declSpecifier()) {
            if (spec.typeSpecifier() == null)
                continue;

            var typeSpec = spec.typeSpecifier();
            if (typeSpec.trailingTypeSpecifier() == null)
                continue;

            var trailing = typeSpec.trailingTypeSpecifier();
            if (trailing.simpleTypeSpecifier() == null)
                continue;

            var simple = trailing.simpleTypeSpecifier();

            // // テンプレート型のからの関係抽出
            // if (simple.theTypeName() != null && simple.theTypeName().simpleTemplateId()
            // != null) {
            // var templateId = simple.theTypeName().simpleTemplateId();
            // String extractedTypeName = templateId.getText();

            // // テンプレート引数からの関係抽出取得（一次元のみ対象）
            // if (templateId.templateArgumentList() != null &&
            // templateId.templateArgumentList().templateArgument() != null) {

            // var firstTempArg = templateId.templateArgumentList().templateArgument(0);
            // var firstTypeSpec =
            // firstTempArg.theTypeId().typeSpecifierSeq().typeSpecifier(0);

            // if (firstTypeSpec.trailingTypeSpecifier().simpleTypeSpecifier().theTypeName()
            // != null
            // && firstTypeSpec.trailingTypeSpecifier()
            // .simpleTypeSpecifier().theTypeName().className() != null) {

            // // 関係先クラス名
            // String targetClassName = firstTypeSpec.trailingTypeSpecifier()
            // .simpleTypeSpecifier().theTypeName().className().getText();

            // // 関係性の判定（ポインタ/参照の要素の場合はASSOCIATION）
            // String pointerOpe = "";
            // if (firstTempArg.theTypeId().abstractDeclarator() != null) {
            // pointerOpe = firstTempArg.theTypeId().abstractDeclarator()
            // .pointerAbstractDeclarator().pointerOperator(0).getText();
            // }

            // RelationType relationType = pointerOpe.equals("*") || pointerOpe.equals("&")
            // ? RelationType.ASSOCIATION
            // : RelationType.COMPOSITION;

            // System.err.println("DEBUG: targetClassName : " + targetClassName);
            // // 関係性の追加
            // RelationshipInfo relation = new RelationshipInfo(targetClassName,
            // relationType);
            // relation.setElement(
            // attributeName,
            // ElementType.ATTRIBUTE,
            // "*", // コレクション型なので多重度は*
            // convertVisibility(currentVisibility));

            // return new Object[] { extractedTypeName, relation };
            // }
            // }
            // } else
            if (simple.theTypeName() != null && simple.theTypeName().className() != null) { // 普通のユーザ宣言の場合（stdも含む）
                // ユーザ定義型名
                String extractedTypeName = simple.theTypeName().className().getText();
                if (isUserDefinedType(extractedTypeName)) {

                    // 関係性の判定（ポインタ/参照の要素の場合はASSOCIATION）
                    RelationType relationType = isPointer || isRef
                            ? RelationType.ASSOCIATION
                            : RelationType.COMPOSITION;

                    // 関係性の追加
                    RelationshipInfo relation = new RelationshipInfo(extractedTypeName,
                            relationType);
                    if (isPointer) {
                        extractedTypeName = extractedTypeName + "*";
                        relation.setElement(
                                attributeName,
                                ElementType.ATTRIBUTE,
                                "0..1",
                                convertVisibility(currentVisibility));
                    } else if (isRef) {
                        extractedTypeName = extractedTypeName + "&";
                        relation.setElement(
                                attributeName,
                                ElementType.ATTRIBUTE,
                                "1",
                                convertVisibility(currentVisibility));
                    } else {
                        relation.setElement(
                                attributeName,
                                ElementType.ATTRIBUTE,
                                "1",
                                convertVisibility(currentVisibility));
                    }

                    return new Object[] { extractedTypeName, relation };
                } else {
                    if (isPointer) {
                        extractedTypeName = extractedTypeName + "*";
                    } else if (isRef) {
                        extractedTypeName = extractedTypeName + "&";
                    }
                    return new Object[] { extractedTypeName, null };
                }
            } else {
                String extractedTypeName = simple.getText();
                if (isPointer) {
                    extractedTypeName = extractedTypeName + "*";
                } else if (isRef) {
                    extractedTypeName = extractedTypeName + "&";
                }
                return new Object[] { extractedTypeName, null };
            }
        }
        return new Object[] { null };

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

    private boolean isUserDefinedType(String typeName) {

        // stdライブラリの型のセット
        Set<String> stdTypes = Set.of(
                "string", "vector", "list", "map", "set",
                "queue", "deque", "array", "stack",
                "shared_ptr", "unique_ptr", "weak_ptr",
                "pair", "tuple", "function");

        return !stdTypes.contains(typeName) && !typeName.contains("<");
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