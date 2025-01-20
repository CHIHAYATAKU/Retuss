package io.github.morichan.retuss.translator.cpp.header.analyzers.class_definition;

import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.*;
// import io.github.morichan.retuss.translator.cpp.header.analyzers.namespace.NamespaceAnalyzer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class ClassAnalyzer extends AbstractAnalyzer {
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        return context instanceof CPP14Parser.ClassSpecifierContext ||
                context instanceof CPP14Parser.EnumSpecifierContext;
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        if (context instanceof CPP14Parser.ClassSpecifierContext) {
            analyzeClass((CPP14Parser.ClassSpecifierContext) context);
        } else if (context instanceof CPP14Parser.EnumSpecifierContext) {
            analyzeEnum((CPP14Parser.EnumSpecifierContext) context);
        }
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

    private void analyzeClass(CPP14Parser.ClassSpecifierContext ctx) {
        if (ctx.classHead() != null &&
                ctx.classHead().classHeadName() != null &&
                ctx.classHead().classHeadName().className() != null) {
            // System.err.println("DEBUG: ClassSpecifierContext: " + ctx.getText());
            // System.err.println("DEBUG: classHead: " + ctx.classHead().getText());
            // System.err.println("DEBUG: classHead.classHeadName: " +
            // ctx.classHead().classHeadName().getText());
            // System.err.println("DEBUG: classHead.classHeadName.className: "
            // + ctx.classHead().classHeadName().className().getText());
            // System.err.println("DEBUG: classSpecifier structure:");
            // System.err.println(" - Child count: " + ctx.getChildCount());
            // for (int i = 0; i < ctx.getChildCount(); i++) {
            // ParseTree child = ctx.getChild(i);
            // System.err.println(" - Child " + i + ": [" + child.getClass().getSimpleName()
            // + "] " + child.getText());
            // }
            debugNode(ctx, 0);
            String className = ctx.classHead().classHeadName().className().getText();
            CppHeaderClass cppClass = new CppHeaderClass(className);
            // cppClass.setNamespace(this.context.getCurrentNamespace());
            this.context.setCurrentHeaderClass(cppClass);

            System.out.println("DEBUG: Created class: " + className);
        }
    }

    private void analyzeEnum(CPP14Parser.EnumSpecifierContext ctx) {
        if (ctx.enumHead() != null) {
            System.err.println("DEBUG: EnumSpecifierContext: " + ctx.getText());
            System.err.println("DEBUG: enumHead: " + ctx.enumHead().getText());

            debugNode(ctx, 0);

            // enum名の取得
            String enumName = getEnumName(ctx.enumHead());
            System.err.println("DEBUG: enumName: " + enumName);

            // enumクラスとして作成
            CppHeaderClass enumClass = new CppHeaderClass(enumName);
            enumClass.setEnum(true);

            String baseType = null;
            if (ctx.enumHead().enumbase() != null) {
                baseType = ctx.enumHead().enumbase().getText().replace(":", "").trim();
                System.err.println("DEBUG: enumBase: " + baseType);
            }

            // enum値の解析
            if (ctx.enumeratorList() != null) {
                System.err.println("DEBUG: Processing enumeratorList...");
                for (CPP14Parser.EnumeratorDefinitionContext enumDef : ctx.enumeratorList().enumeratorDefinition()) {
                    String enumValue = enumDef.enumerator().getText();
                    String value = null;

                    // 初期化式がある場合は値を取得
                    if (enumDef.constantExpression() != null) {
                        value = enumDef.constantExpression().getText();
                    }

                    System.err.println("DEBUG: Found enum value: " + enumValue +
                            (value != null ? " = " + value : ""));

                    enumClass.addEnumValue(enumValue, value);
                }
            }

            this.context.setCurrentHeaderClass(enumClass);
            System.out.println("DEBUG: Created enum class: " + enumName +
                    (baseType != null ? " with base type: " + baseType : ""));
        }
    }

    private String getEnumName(CPP14Parser.EnumHeadContext enumHead) {
        // enumの名前を取得するロジック
        if (enumHead != null) {
            // 文法に応じて適切な方法で識別子を取得
            if (enumHead.Identifier() != null) { // identifier()メソッドが存在する場合
                return enumHead.Identifier().getText();
            }
        }
        return "Anonymous" + System.currentTimeMillis();
    }
}