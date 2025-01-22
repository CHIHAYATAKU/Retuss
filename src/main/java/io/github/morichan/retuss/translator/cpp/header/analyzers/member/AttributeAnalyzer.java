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
import org.antlr.v4.runtime.tree.ParseTree;

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

        // memberDeclaratorListのnullチェック
        if (ctx.memberDeclaratorList() == null ||
                ctx.memberDeclaratorList().memberDeclarator() == null ||
                ctx.memberDeclaratorList().memberDeclarator().isEmpty()) {
            return false;
        }

        return ctx.declSpecifierSeq() != null &&
                !isMethodDeclaration(ctx.memberDeclaratorList());
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
        CppHeaderClass currentHeaderClass = this.context.getCurrentHeaderClass();

        if (currentHeaderClass == null) {
            return;
        }

        try {
            Boolean isPointer = false;
            Boolean isRef = false;

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
                        currentHeaderClass.addMemberModifier(attribute, modifier);
                    }
                    // 関係性があれば追加
                    if (result[1] != null) {

                        RelationshipInfo relationshipInfo = (RelationshipInfo) result[1];
                        // アノテーションの取得と設定
                        String relationshipAnnotation = extractRelationshipAnnotation(memberDec);
                        System.err.println("DEBUG: Set relationshipAnnotation: " + relationshipAnnotation);
                        if (relationshipAnnotation != null) {
                            switch (relationshipAnnotation) {
                                case "aggregation":
                                    RelationType aggregation = RelationType.AGGREGATION;
                                    relationshipInfo.setType(aggregation);
                                case "composition":
                                    RelationType composition = RelationType.COMPOSITION;
                                    relationshipInfo.setType(composition);
                                case "association":
                                    RelationType association = RelationType.ASSOCIATION;
                                    relationshipInfo.setType(association);
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
                                "0..1",
                                convertVisibility(currentVisibility));
                    } else if (isRef) {
                        extractedTypeName = extractedTypeName + "&";
                        relation.setElement(
                                attributeName,
                                "1",
                                convertVisibility(currentVisibility));
                    } else {
                        relation.setElement(
                                attributeName,
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

    private boolean isUserDefinedType(String typeName) {

        // stdライブラリの型のセット
        Set<String> stdTypes = Set.of(
                "string", "vector", "list", "map", "set",
                "queue", "deque", "array", "stack",
                "shared_ptr", "unique_ptr", "weak_ptr",
                "pair", "tuple", "function");

        return !stdTypes.contains(typeName) && !typeName.contains("<");
    }

    private boolean isMethodDeclaration(CPP14Parser.MemberDeclaratorListContext memberDecList) {
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