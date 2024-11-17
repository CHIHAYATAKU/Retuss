package io.github.morichan.retuss.translator.cpp.analyzers.class_definition;

import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.analyzers.base.AbstractAnalyzer;
import org.antlr.v4.runtime.ParserRuleContext;

public class ClassAnalyzer extends AbstractAnalyzer {
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        return context instanceof CPP14Parser.ClassSpecifierContext;
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        CPP14Parser.ClassSpecifierContext ctx = (CPP14Parser.ClassSpecifierContext) context;
        if (ctx.classHead() != null &&
                ctx.classHead().classHeadName() != null &&
                ctx.classHead().classHeadName().className() != null) {

            String className = ctx.classHead().classHeadName().className().getText();
            CppClass cppClass = new CppClass(className);
            this.context.setCurrentClass(cppClass);

            System.out.println("DEBUG: Created class: " + className);
        }
    }
}