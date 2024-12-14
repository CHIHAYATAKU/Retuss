package io.github.morichan.retuss.translator.cpp.header.analyzers.member;

import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.AbstractAnalyzer;

import org.antlr.v4.runtime.ParserRuleContext;

public class VisibilityAnalyzer extends AbstractAnalyzer {
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        return context instanceof CPP14Parser.AccessSpecifierContext;
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        CPP14Parser.AccessSpecifierContext ctx = (CPP14Parser.AccessSpecifierContext) context;
        String visibility = ctx.getText().toLowerCase();
        this.context.setCurrentVisibility(visibility);
        System.out.println("DEBUG: Visibility changed to: " + visibility);
    }
}