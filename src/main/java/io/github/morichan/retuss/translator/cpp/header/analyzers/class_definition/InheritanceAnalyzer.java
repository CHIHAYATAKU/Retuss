package io.github.morichan.retuss.translator.cpp.header.analyzers.class_definition;

import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.*;

import org.antlr.v4.runtime.ParserRuleContext;

public class InheritanceAnalyzer extends AbstractAnalyzer {
    // 構文木に継承情報が含まれているかを検証する
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        if (!(context instanceof CPP14Parser.ClassSpecifierContext)) {
            return false;
        }
        CPP14Parser.ClassSpecifierContext ctx = (CPP14Parser.ClassSpecifierContext) context;
        return ctx.classHead() != null && ctx.classHead().baseClause() != null;
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        CPP14Parser.ClassSpecifierContext ctx = (CPP14Parser.ClassSpecifierContext) context;
        CppHeaderClass currentClass = this.context.getCurrentHeaderClass();

        if (currentClass == null || ctx.classHead().baseClause() == null) {
            return;
        }

        try {
            CPP14Parser.BaseClauseContext baseClause = ctx.classHead().baseClause();
            for (CPP14Parser.BaseSpecifierContext baseSpec : baseClause.baseSpecifierList().baseSpecifier()) {

                handleBaseSpecifier(baseSpec, currentClass);
            }
        } catch (Exception e) {
            System.err.println("Error analyzing inheritance: " + e.getMessage());
        }
    }

    private void handleBaseSpecifier(
            CPP14Parser.BaseSpecifierContext baseSpec,
            CppHeaderClass currentClass) {

        String baseClassName = baseSpec.baseTypeSpecifier().getText();
        String accessSpecifier = baseSpec.accessSpecifier() != null
                ? baseSpec.accessSpecifier().getText().toLowerCase()
                : "private";

        // 可視性を取得
        Visibility visibility;
        switch (accessSpecifier) {
            case "public":
                visibility = Visibility.Public;
                break;
            case "protected":
                visibility = Visibility.Protected;
                break;
            default:
                visibility = Visibility.Private;
        }

        RelationshipInfo relation = new RelationshipInfo(
                baseClassName,
                RelationType.INHERITANCE);
        relation.addElement(
                "",
                ElementType.ATTRIBUTE,
                "1",
                visibility);
        currentClass.addRelationship(relation);
    }
}