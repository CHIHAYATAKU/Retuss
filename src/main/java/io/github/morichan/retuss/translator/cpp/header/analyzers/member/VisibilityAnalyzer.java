package io.github.morichan.retuss.translator.cpp.header.analyzers.member;

import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.AbstractAnalyzer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class VisibilityAnalyzer extends AbstractAnalyzer {
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        return context instanceof CPP14Parser.AccessSpecifierContext;
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
        CPP14Parser.AccessSpecifierContext ctx = (CPP14Parser.AccessSpecifierContext) context;
        // debugNode(ctx, 0);
        String visibility = ctx.getText().toLowerCase();
        this.context.setCurrentVisibility(visibility);
        System.out.println("DEBUG: Visibility changed to: " + visibility);
    }
}