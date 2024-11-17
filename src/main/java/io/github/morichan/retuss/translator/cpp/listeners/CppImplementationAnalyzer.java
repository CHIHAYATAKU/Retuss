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
    }

    private CPP14Parser createParser(String code) {
        CharStream input = CharStreams.fromString(code);
        CPP14Lexer lexer = new CPP14Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new CPP14Parser(tokens);
    }
}