package io.github.morichan.retuss.translator.cpp;

import io.github.morichan.retuss.translator.common.CodeToUmlTranslator;
import io.github.morichan.retuss.translator.cpp.listeners.ClassExtractorListener;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.parser.cpp.CPP14Lexer;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class CppToUmlTranslator {

    public List<CppHeaderClass> translateHeader(String code) {
        try {
            String processedCode = preprocessCode(code);
            CharStream input = CharStreams.fromString(processedCode);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);
            // 構文ツリーを取得
            // ParseTree tree = parser.translationUnit();

            // 構文ツリーを文字列として出力
            // System.out.println(tree.toStringTree(parser));
            ParseTreeWalker walker = new ParseTreeWalker();

            // ヘッダファイルとして解析（true）
            ClassExtractorListener classExtractor = new ClassExtractorListener(true);
            walker.walk(classExtractor, parser.translationUnit());

            return classExtractor.getExtractedClasses();
        } catch (Exception e) {
            System.err.println("Failed to translate C++ code: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private String preprocessCode(String code) {
        return code.replaceAll("#.*\\n", "\n");
    }
}