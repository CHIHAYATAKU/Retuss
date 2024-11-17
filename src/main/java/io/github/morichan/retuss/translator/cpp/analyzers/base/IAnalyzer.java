package io.github.morichan.retuss.translator.cpp.analyzers.base;

import org.antlr.v4.runtime.ParserRuleContext;

public interface IAnalyzer {
    void analyze(ParserRuleContext context, AnalyzerContext analysisContext);

    boolean appliesTo(ParserRuleContext context);
}
