package io.github.morichan.retuss.translator.cpp;

import io.github.morichan.retuss.translator.common.CodeToUmlTranslator;
import io.github.morichan.retuss.translator.cpp.listeners.ClassExtractorListener;
import io.github.morichan.retuss.translator.cpp.util.CppTypeMapper;
import io.github.morichan.retuss.translator.cpp.util.CppVisibilityMapper;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.CPP14Lexer;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class CppToUmlTranslator implements CodeToUmlTranslator {
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;

    public CppToUmlTranslator() {
        this.typeMapper = new CppTypeMapper();
        this.visibilityMapper = new CppVisibilityMapper();
    }

    @Override
    public List<Class> translate(String code) {
        try {
            String processedCode = preprocessCode(code);
            ParseTree tree = parseCode(processedCode);
            return extractClasses(tree);
        } catch (Exception e) {
            System.err.println("Failed to translate C++ code to UML: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String preprocessCode(String code) {
        return code.replaceAll("#.*\\n", "\n");
    }

    private ParseTree parseCode(String code) {
        CharStream input = CharStreams.fromString(code);
        CPP14Lexer lexer = new CPP14Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CPP14Parser parser = new CPP14Parser(tokens);
        return parser.translationUnit();
    }

    private List<Class> extractClasses(ParseTree tree) {
        ClassExtractorListener extractor = new ClassExtractorListener(typeMapper, visibilityMapper);
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(extractor, tree);
        return extractor.getExtractedClasses();
    }
}