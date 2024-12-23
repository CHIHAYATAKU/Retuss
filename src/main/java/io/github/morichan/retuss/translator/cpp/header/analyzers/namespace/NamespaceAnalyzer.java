package io.github.morichan.retuss.translator.cpp.header.analyzers.namespace;

import org.antlr.v4.runtime.ParserRuleContext;

import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.AbstractAnalyzer;

public class NamespaceAnalyzer extends AbstractAnalyzer {
    private String currentNamespace = "";

    @Override
    public boolean appliesTo(ParserRuleContext context) {
        return context instanceof CPP14Parser.NamespaceDefinitionContext;
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        CPP14Parser.NamespaceDefinitionContext ctx = (CPP14Parser.NamespaceDefinitionContext) context;

        if (ctx.Identifier() != null) {
            String namespace = ctx.Identifier().getText();
            this.context.setCurrentNamespace(namespace);
        }
    }
}