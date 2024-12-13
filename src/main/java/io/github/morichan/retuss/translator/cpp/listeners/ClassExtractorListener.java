package io.github.morichan.retuss.translator.cpp.listeners;

import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.parser.cpp.CPP14ParserBaseListener;
import io.github.morichan.retuss.translator.cpp.analyzers.base.AnalyzerContext;
import io.github.morichan.retuss.translator.cpp.analyzers.base.IAnalyzer;
import io.github.morichan.retuss.translator.cpp.analyzers.class_definition.*;
import io.github.morichan.retuss.translator.cpp.analyzers.member.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;

import java.util.*;

public class ClassExtractorListener extends CPP14ParserBaseListener {
    private final List<IAnalyzer> analyzers;
    private final AnalyzerContext context;
    private final List<CppHeaderClass> extractedHeaderClasses;
    private final boolean isHeaderFile;

    public ClassExtractorListener(boolean isHeaderFile) {
        this.isHeaderFile = isHeaderFile;
        this.context = new AnalyzerContext(isHeaderFile);
        this.extractedHeaderClasses = new ArrayList<>();

        if (isHeaderFile()) {
            this.analyzers = Arrays.asList(
                    new ClassAnalyzer(),
                    new InheritanceAnalyzer(),
                    new VisibilityAnalyzer(),
                    new AttributeAnalyzer(),
                    new OperationAnalyzer(),
                    new ConstructorAndDestructorAnalyzer());
        } else {
            this.analyzers = Arrays.asList(new OperationAnalyzer());
        }
    }

    public boolean isHeaderFile() {
        return isHeaderFile;
    }

    @Override
    public void enterClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
        System.out.println("DEBUG: Entering class specifier");
        for (IAnalyzer analyzer : analyzers) {
            if (analyzer.appliesTo(ctx)) {
                analyzer.analyze(ctx, context);
            }
        }

        // 新しいクラスが作成された場合、リストに追加
        if (context.getCurrentHeaderClass() != null) {
            CppHeaderClass currentClass = context.getCurrentHeaderClass();
            if (!extractedHeaderClasses.contains(currentClass)) {
                extractedHeaderClasses.add(currentClass);
                System.out.println("DEBUG: Added new class to extracted classes: " +
                        currentClass.getName());
            }
        }
    }

    @Override
    public void exitClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
        System.out.println("DEBUG: Exiting class specifier");
        // クラス解析終了時の処理があれば実装
    }

    @Override
    public void enterMemberdeclaration(CPP14Parser.MemberdeclarationContext ctx) {
        if (context.getCurrentHeaderClass() == null)
            return;

        System.out.println("DEBUG: Processing member declaration");
        for (IAnalyzer analyzer : analyzers) {
            if (analyzer.appliesTo(ctx)) {
                analyzer.analyze(ctx, context);
            }
        }
    }

    @Override
    public void enterAccessSpecifier(CPP14Parser.AccessSpecifierContext ctx) {
        System.out.println("DEBUG: Processing access specifier");
        for (IAnalyzer analyzer : analyzers) {
            if (analyzer.appliesTo(ctx)) {
                analyzer.analyze(ctx, context);
            }
        }
    }

    public List<CppHeaderClass> getExtractedClasses() {
        return Collections.unmodifiableList(extractedHeaderClasses);
    }

    @Override
    public void enterTranslationUnit(CPP14Parser.TranslationUnitContext ctx) {
        System.out.println("DEBUG: Starting C++ code analysis");
    }

    @Override
    public void exitTranslationUnit(CPP14Parser.TranslationUnitContext ctx) {
        System.out.println("DEBUG: Completed C++ code analysis");
        System.out.println("DEBUG: Extracted " + extractedHeaderClasses.size() + " classes");

        // クラス情報のダンプ（デバッグ用）
        for (CppHeaderClass cls : extractedHeaderClasses) {
            CppHeaderClass cppClass = cls;
            System.out.println("\nClass: " + cppClass.getName());
            System.out.println("Attributes: " + cppClass.getAttributeList().size());
            System.out.println("Operations: " + cppClass.getOperationList().size());

            // // 関係情報の出力
            // System.out.println("Relationship: " + rel.getTargetClass() +
            // " (" + rel.getType() + ")");
            // for (RelationshipInfo.RelationshipElement elem : rel.getElements()) {
            // System.out.println(" Element: " + elem.getName() +
            // " [" + elem.getMultiplicity() + "]");
            // }
        }
    }

    // エラー処理
    @Override
    public void visitErrorNode(org.antlr.v4.runtime.tree.ErrorNode node) {
        System.err.println("Error parsing node: " + node.getText());
    }
}