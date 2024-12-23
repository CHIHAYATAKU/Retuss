package io.github.morichan.retuss.translator.cpp.header.analyzers.class_definition;

import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.*;
import io.github.morichan.retuss.translator.cpp.header.analyzers.namespace.NamespaceAnalyzer;

import org.antlr.v4.runtime.ParserRuleContext;

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

    private void analyzeClass(CPP14Parser.ClassSpecifierContext ctx) {
        if (ctx.classHead() != null &&
                ctx.classHead().classHeadName() != null &&
                ctx.classHead().classHeadName().className() != null) {

            String className = ctx.classHead().classHeadName().className().getText();
            CppHeaderClass cppClass = new CppHeaderClass(className);
            cppClass.setNamespace(this.context.getCurrentNamespace());
            this.context.setCurrentHeaderClass(cppClass);

            System.out.println("DEBUG: Created class: " + className);
        }
    }

    private void analyzeEnum(CPP14Parser.EnumSpecifierContext ctx) {
        if (ctx.enumHead() != null) {
            // enum名の取得
            String enumName = getEnumName(ctx.enumHead());

            // enumクラスとして作成
            CppHeaderClass enumClass = new CppHeaderClass(enumName);
            enumClass.setEnum(true);

            String baseType = null;
            if (ctx.enumHead().enumbase() != null) {
                baseType = ctx.enumHead().enumbase().getText().replace(":", "").trim();
            }

            // enum値の解析
            if (ctx.enumeratorList() != null) {
                for (CPP14Parser.EnumeratorDefinitionContext enumDef : ctx.enumeratorList().enumeratorDefinition()) {
                    String enumValue = enumDef.enumerator().getText();
                    String value = null;

                    // 初期化式がある場合は値を取得
                    if (enumDef.constantExpression() != null) {
                        value = enumDef.constantExpression().getText();
                    }

                    enumClass.addEnumValue(enumValue, value, baseType);
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