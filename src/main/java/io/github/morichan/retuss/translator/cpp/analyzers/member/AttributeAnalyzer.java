package io.github.morichan.retuss.translator.cpp.analyzers.member;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.model.uml.CppClass.RelationshipInfo;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.analyzers.base.AbstractAnalyzer;
import org.antlr.v4.runtime.ParserRuleContext;
import java.util.*;

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
        CppClass currentClass = this.context.getCurrentClass();

        if (currentClass == null || ctx.declSpecifierSeq() == null) {
            return;
        }

        try {
            String rawType = ctx.declSpecifierSeq().getText();
            Set<CppClass.Modifier> modifiers = extractModifiers(rawType);
            String processedType = cleanType(rawType);

            if (ctx.memberDeclaratorList() != null) {
                for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                    handleAttribute(memberDec, processedType, modifiers);
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
            Set<CppClass.Modifier> modifiers) {

        CppClass currentClass = context.getCurrentClass();
        String attributeName = extractAttributeName(memberDec.declarator());

        if (currentClass.getAttributeList().stream()
                .anyMatch(attr -> attr.getName().getNameText().equals(attributeName))) {
            return;
        }

        Attribute attribute = new Attribute(new Name(attributeName));
        attribute.setType(new Type(processAttributeType(type, memberDec.declarator())));
        attribute.setVisibility(convertVisibility(context.getCurrentVisibility()));

        currentClass.addAttribute(attribute);
        for (CppClass.Modifier modifier : modifiers) {
            currentClass.addMemberModifier(attributeName, modifier);
        }

        // 型の関係解析
        analyzeAttributeTypeRelationship(type, memberDec.declarator(), attributeName);
    }

    private void analyzeAttributeTypeRelationship(
            String type,
            CPP14Parser.DeclaratorContext declarator,
            String attributeName) {

        String cleanType = cleanTypeName(type);
        if (!isUserDefinedType(cleanType)) {
            return;
        }

        CppClass currentClass = context.getCurrentClass();
        String declaratorText = declarator.getText();
        Visibility visibility = convertVisibility(context.getCurrentVisibility());
        Set<CppClass.Modifier> mods = currentClass.getModifiers(attributeName);

        // コレクション型の処理
        if (isCollectionType(type)) {
            String elementType = extractElementType(type);
            if (isUserDefinedType(elementType)) {
                RelationshipInfo relation = new RelationshipInfo(
                        elementType,
                        RelationshipInfo.RelationType.COMPOSITION);
                relation.addElement(
                        attributeName,
                        RelationshipInfo.ElementType.ATTRIBUTE,
                        "*",
                        visibility,
                        type,
                        null, // returnType
                        null, // defaultValue
                        false, // isPureVirtual
                        mods);
                currentClass.addRelationship(relation);
            }
            return;
        }

        // 配列の処理
        if (declaratorText.matches(".*\\[\\d+\\]")) {
            String size = declaratorText.replaceAll(".*\\[(\\d+)\\].*", "$1");
            RelationshipInfo relation = new RelationshipInfo(
                    cleanType,
                    RelationshipInfo.RelationType.COMPOSITION);
            relation.addElement(
                    attributeName,
                    RelationshipInfo.ElementType.ATTRIBUTE,
                    size,
                    visibility,
                    type,
                    null, // returnType
                    null, // defaultValue
                    false, // isPureVirtual
                    mods);
            currentClass.addRelationship(relation);
            return;
        }

        // ポインタ処理
        if (type.contains("*") || declaratorText.contains("*")) {
            RelationshipInfo relation = new RelationshipInfo(
                    cleanType,
                    RelationshipInfo.RelationType.AGGREGATION);
            relation.addElement(
                    attributeName,
                    RelationshipInfo.ElementType.ATTRIBUTE,
                    "0..1",
                    visibility,
                    type,
                    null, // returnType
                    null, // defaultValue
                    false, // isPureVirtual
                    mods);
            currentClass.addRelationship(relation);
            return;
        }

        // 参照処理
        if (type.contains("&") || declaratorText.contains("&")) {
            RelationshipInfo relation = new RelationshipInfo(
                    cleanType,
                    RelationshipInfo.RelationType.ASSOCIATION);
            relation.addElement(
                    attributeName,
                    RelationshipInfo.ElementType.ATTRIBUTE,
                    "1",
                    visibility,
                    type,
                    null, // returnType
                    null, // defaultValue
                    false, // isPureVirtual
                    mods);
            currentClass.addRelationship(relation);
            return;
        }

        // 通常のインスタンスメンバ
        RelationshipInfo relation = new RelationshipInfo(
                cleanType,
                RelationshipInfo.RelationType.COMPOSITION);
        relation.addElement(
                attributeName,
                RelationshipInfo.ElementType.ATTRIBUTE,
                "1",
                visibility,
                type,
                null, // returnType
                null, // defaultValue
                false, // isPureVirtual
                mods);
        currentClass.addRelationship(relation);
    }

    private Set<CppClass.Modifier> extractModifiers(String type) {
        Set<CppClass.Modifier> modifiers = EnumSet.noneOf(CppClass.Modifier.class);
        if (type.contains("static"))
            modifiers.add(CppClass.Modifier.STATIC);
        if (type.contains("const"))
            modifiers.add(CppClass.Modifier.CONST);
        if (type.contains("mutable"))
            modifiers.add(CppClass.Modifier.MUTABLE);
        return modifiers;
    }

    private String processAttributeType(String type, CPP14Parser.DeclaratorContext declarator) {
        StringBuilder processedType = new StringBuilder();

        // 修飾子の処理
        if (type.contains("const")) {
            processedType.append("const ");
        }

        // 基本型（修飾子を除去）
        String baseType = type.replaceAll("(static|const|mutable)", "")
                .replaceAll("std::", "")
                .trim();
        processedType.append(baseType);

        // ポインタ/参照の処理
        String declaratorText = declarator.getText();
        if (declaratorText.contains("*") || type.contains("*")) {
            processedType.append("*");
        }
        if (declaratorText.contains("&") || type.contains("&")) {
            processedType.append("&");
        }

        return processedType.toString();
    }

    private String extractAttributeName(CPP14Parser.DeclaratorContext declarator) {
        String name = declarator.getText();
        return name.replaceAll("\\[.*\\]", "").trim();
    }

    private String cleanType(String type) {
        return type.replaceAll("(static|const|mutable|volatile|virtual)", "") // virtualを追加
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanTypeName(String typeName) {
        return typeName.replaceAll("[*&]", "")
                .replaceAll("\\s+", "")
                .replaceAll("const", "")
                .replaceAll("static", "")
                .replaceAll("mutable", "")
                .replaceAll("std::", "")
                .replaceAll("virtual", "")
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

    private boolean isCollectionType(String type) {
        return type.contains("vector<") ||
                type.contains("array<") ||
                type.contains("list<") ||
                type.contains("set<") ||
                type.contains("deque<") ||
                type.contains("queue<") ||
                type.contains("stack<") ||
                type.contains("map<");
    }

    private String extractElementType(String type) {
        if (type.contains("<")) {
            return cleanTypeName(type.replaceAll(".*<(.+)>.*", "$1"));
        }
        return type;
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