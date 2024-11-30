package io.github.morichan.retuss.translator.cpp.listeners;

import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.model.uml.Relationship;
import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.retuss.translator.cpp.listeners.CppDependencyAnalyzer;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class CppImplementationAnalyzer {
    private final String headerCode;
    private final String implementationCode;
    private final String className;

    public CppImplementationAnalyzer(String headerCode, String implementationCode, String className) {
        this.headerCode = headerCode;
        this.implementationCode = implementationCode;
        this.className = className;
    }

    public void analyze(CppClass cppClass) {
        // 実装ファイルの解析
        CppDependencyAnalyzer analyzer = new CppDependencyAnalyzer(className);
        ParseTreeWalker walker = new ParseTreeWalker();

        try {
            CPP14Parser parser = createParser(implementationCode);
            walker.walk(analyzer, parser.translationUnit());

            // インスタンス化されたクラスと使用されたクラスの記録
            analyzer.getInstantiatedClasses().forEach(cppClass::addDependency);
            analyzer.getUsedClasses().forEach(cppClass::addDependency);

        } catch (Exception e) {
            System.err.println("Error analyzing implementation file: " + e.getMessage());
        }
    }

    private CPP14Parser createParser(String code) {
        CharStream input = CharStreams.fromString(code);
        CPP14Lexer lexer = new CPP14Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new CPP14Parser(tokens);
    }
}