package io.github.morichan.retuss.translator.cpp.header.analyzers.base;

import org.antlr.v4.runtime.ParserRuleContext;

public abstract class AbstractAnalyzer implements IAnalyzer {
    protected AnalyzerContext context;

    @Override
    public void analyze(ParserRuleContext context, AnalyzerContext analysisContext) {
        this.context = analysisContext;
        if (appliesTo(context)) {
            analyzeInternal(context);
        }
    }

    protected abstract void analyzeInternal(ParserRuleContext context);
}