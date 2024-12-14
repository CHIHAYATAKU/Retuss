package io.github.morichan.retuss.translator.cpp.header;

import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.retuss.translator.cpp.header.util.*;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class CppTranslator {
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;
    protected final CppToUmlTranslator cppToUmlTranslator;
    protected final UmlToCppTranslator umlToCppTranslator;

    public CppTranslator() {
        super();
        this.typeMapper = new CppTypeMapper();
        this.visibilityMapper = new CppVisibilityMapper();
        this.cppToUmlTranslator = createCodeToUmlTranslator();
        this.umlToCppTranslator = createUmlToCodeTranslator();
    }

    public List<CppHeaderClass> translateHeaderCodeToUml(String code) {
        return cppToUmlTranslator.translateHeader(code);
    }

    public String translateVisibility(Visibility visibility) {
        return visibilityMapper.toSourceCode(visibility);
    }

    public String translateType(Type type) {
        return typeMapper.mapType(type.toString());
    }

    public String translateAttribute(Attribute attribute) {
        StringBuilder sb = new StringBuilder();

        // 型と名前
        sb.append(translateType(attribute.getType()))
                .append(" ")
                .append(attribute.getName());

        // デフォルト値がある場合
        try {
            if (attribute.getDefaultValue() != null) {
                sb.append(" = ").append(attribute.getDefaultValue().toString());
            }
        } catch (IllegalStateException e) {
            // デフォルト値なし
        }

        return sb.toString();
    }

    public String translateOperation(Operation operation) {
        StringBuilder sb = new StringBuilder();

        // 戻り値の型と名前
        sb.append(translateType(operation.getReturnType()))
                .append(" ")
                .append(operation.getName())
                .append("(");

        if (!operation.getParameters().isEmpty() && operation.getParameters() != null) {
            // パラメータ
            List<String> params = new ArrayList<>();

            operation.getParameters().forEach(param -> params.add(String.format("%s %s",
                    translateType(param.getType()),
                    param.getName())));
            sb.append(String.join(", ", params));
        }

        sb.append(")");

        return sb.toString();
    }

    public Optional<String> extractClassName(String code) {
        try {
            if (code == null || code.trim().isEmpty()) {
                return Optional.empty();
            }

            // プリプロセッサディレクティブを一時的に削除
            String processedCode = code.replaceAll("#.*\\n", "\n");

            CharStream input = CharStreams.fromString(processedCode);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            // クラス名を抽出するリスナー
            class ClassNameListener extends CPP14ParserBaseListener {
                private String className = null;

                public void enterClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
                    if (ctx.classHead() != null &&
                            ctx.classHead().classHeadName() != null &&
                            ctx.classHead().classHeadName().className() != null) {
                        className = ctx.classHead().classHeadName().className().getText();
                    }
                }

                public Optional<String> getClassName() {
                    return Optional.ofNullable(className);
                }
            }

            ClassNameListener listener = new ClassNameListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, parser.translationUnit());

            return listener.getClassName();
        } catch (Exception e) {
            System.err.println("Failed to extract class name: " + e.getMessage());
            return Optional.empty();
        }
    }

    protected CppToUmlTranslator createCodeToUmlTranslator() {
        return new CppToUmlTranslator();
    }

    protected UmlToCppTranslator createUmlToCodeTranslator() {
        return new UmlToCppTranslator();
    }

    // CppTranslator.java に追加
    public String addAttribute(String existingCode, CppHeaderClass cls, Attribute attribute) {
        return umlToCppTranslator.addAttribute(existingCode, cls, attribute);
    }

    public String addOperation(String existingCode, CppHeaderClass cls, Operation operation) {
        return umlToCppTranslator.addOperation(existingCode, cls, operation);
    }

    public String addInheritance(String existingCode, String derivedClassName, String baseClassName) {
        return umlToCppTranslator.addInheritance(existingCode, derivedClassName, baseClassName);
    }

    public String addRealization(String code, String interfaceName) {
        return umlToCppTranslator.addRealization(code, interfaceName);
    }

    public String removeInheritance(String code, String baseClassName) {
        return umlToCppTranslator.removeInheritance(code, baseClassName);
    }

    public String addComposition(String existingCode, String componentName, String memberName, Visibility visibility) {
        return umlToCppTranslator.addComposition(existingCode, componentName, memberName, visibility);
    }

    public String addCompositionWithAnnotation(String existingCode, String componentName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addCompositionWithAnnotation(existingCode, componentName, memberName, visibility);
    }

    public String addAggregation(String existingCode, String componentName, String memberName, Visibility visibility) {
        return umlToCppTranslator.addAggregation(existingCode, componentName, memberName, visibility);
    }

    public String addAggregationWithAnnotation(String existingCode, String componentName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addAggregationWithAnnotation(existingCode, componentName, memberName, visibility);
    }

    public String addAssociation(String existingCode, String targetClassName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addAssociation(existingCode, targetClassName, memberName, visibility);
    }

    public String removeOperation(String existingCode, Operation operation) {
        return umlToCppTranslator.removeOperation(existingCode, operation);
    }

    public String removeAttribute(String existingCode, Attribute attribute) {
        return umlToCppTranslator.removeAttribute(existingCode, attribute);
    }
}