package io.github.morichan.retuss.translator.cpp.header;

import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.parser.cpp.CPP14Lexer;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.listeners.CppToUmlParseListener;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class CppToUmlTranslator {

    public List<CppHeaderClass> translateHeader(String code) {
        try {
            CharStream input = CharStreams.fromString(code);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);
            // try {
            // // 構文ツリーを取得
            // ParseTree tree = parser.translationUnit();
            // // 構文ツリーを文字列として出力
            // System.out.println(tree.toStringTree(parser));
            // } catch (Exception e) {
            // }

            ParseTreeWalker walker = new ParseTreeWalker();

            // ヘッダファイルとして解析（true）
            CppToUmlParseListener cppToUmlParseListener = new CppToUmlParseListener();
            walker.walk(cppToUmlParseListener, parser.translationUnit());

            return cppToUmlParseListener.getExtractedClasses();
        } catch (Exception e) {
            System.err.println("Failed to translate C++ code: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}