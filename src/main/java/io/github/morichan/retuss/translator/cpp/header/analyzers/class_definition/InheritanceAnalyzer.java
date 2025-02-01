package io.github.morichan.retuss.translator.cpp.header.analyzers.class_definition;

import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

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

    private void debugNode(ParseTree node, int depth) {
        if (node == null)
            return;

        String indent = "  ".repeat(depth);

        // ノード名の取得方法を修正
        String nodeName;
        if (node instanceof RuleContext) {
            nodeName = CPP14Parser.ruleNames[((RuleContext) node).getRuleIndex()];
        } else if (node instanceof TerminalNode) {
            int tokenType = ((TerminalNode) node).getSymbol().getType();
            nodeName = "Terminal(" + CPP14Parser.VOCABULARY.getSymbolicName(tokenType) + ")";
        } else {
            nodeName = node.getClass().getSimpleName();
        }

        // テキストの取得を安全に行う
        String nodeText;
        try {
            nodeText = node.getText();
        } catch (Exception e) {
            nodeText = "<error getting text>";
        }

        System.err.println(indent + nodeName + ": " + nodeText);

        // 子ノードの処理
        try {
            for (int i = 0; i < node.getChildCount(); i++) {
                ParseTree child = node.getChild(i);
                if (child != null) {
                    debugNode(child, depth + 1);
                }
            }
        } catch (Exception e) {
            System.err.println(indent + "  Error processing children: " + e.getMessage());
        }
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        CPP14Parser.ClassSpecifierContext ctx = (CPP14Parser.ClassSpecifierContext) context;
        CppHeaderClass currentClass = this.context.getCurrentHeaderClass();

        if (currentClass == null || ctx.classHead().baseClause() == null) {
            return;
        }

        try {
            // debugNode(ctx, 0);
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

        RelationshipInfo relation = new RelationshipInfo(
                baseClassName,
                RelationType.GENERALIZATION);
        // インターフェースである可能性
        CppModel.getInstance().findClass(baseClassName)
                .ifPresent(targetClass -> {
                    // インターフェースの条件チェック
                    if (targetClass.getInterface() && targetClass.getAttributeList().isEmpty()) {
                        // インターフェースを継承している時点で実現関係に変更
                        relation.setType(RelationType.REALIZATION);
                    }
                });
        currentClass.addRelationship(relation);
    }
}