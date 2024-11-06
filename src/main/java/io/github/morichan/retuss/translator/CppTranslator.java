package io.github.morichan.retuss.translator;

// 基本的なJavaユーティリティ
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import org.antlr.v4.runtime.*;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser.MemberdeclarationContext;
import io.github.morichan.retuss.model.JavaModel;
// UMLモデル関連
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.Interaction;
import io.github.morichan.retuss.model.uml.InteractionFragment;
import io.github.morichan.retuss.model.uml.MessageSort;
import io.github.morichan.retuss.model.uml.OccurenceSpecification;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;

// 共通Translator
import io.github.morichan.retuss.translator.common.AbstractTranslator;

// 将来的に必要になる可能性があるもの
// import io.github.morichan.retuss.model.cpp.CppCodeFile;  // C++固有のコードファイル用
public class CppTranslator extends AbstractTranslator {
    /**
     * <p>
     * ソースコードのASTをUMLデータに変換する。
     * </p>
     */
    @Override
    public List<Class> translateCodeToUml(String code) {
        CharStream cs = CharStreams.fromString(code);
        return translateCodeToUml(cs);
    }

    private List<Class> translateCodeToUml(CharStream cs) {
        CPP14Lexer lexer = new CPP14Lexer(cs);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CPP14Parser parser = new CPP14Parser(tokens);

        List<Class> umlClassList = new ArrayList<>();

        CPP14Parser.TranslationUnitContext tree = parser.translationUnit();

        if (tree.declarationseq() != null) {
            // 抽象構文木から宣言の並びを取得し、リスト化したものを頭から処理する
            for (CPP14Parser.DeclarationContext declaration : tree.declarationseq().declaration()) {
                // 宣言がnullじゃない時
                if (declaration.blockDeclaration() != null
                        && declaration.blockDeclaration().simpleDeclaration() != null) {
                    CPP14Parser.SimpleDeclarationContext simpleDecl = declaration.blockDeclaration()
                            .simpleDeclaration();

                    // 宣言指定子が存在するとき
                    if (simpleDecl.declSpecifierSeq() != null) {
                        // 複数の指定子（static intであれば、static, int）を取得
                        for (CPP14Parser.DeclSpecifierContext declSpec : simpleDecl.declSpecifierSeq()
                                .declSpecifier()) {
                            // 型指定子（class）があり、クラス指定子（MyClass）あるとき
                            if (declSpec.typeSpecifier() != null &&
                                    declSpec.typeSpecifier().classSpecifier() != null) {
                                // クラス名を取得
                                CPP14Parser.ClassSpecifierContext classSpec = declSpec.typeSpecifier().classSpecifier();
                                String className = classSpec.classHead().classHeadName().getText();
                                Class umlClass = new Class(className);

                                // 継承関係の解析
                                if (classSpec.classHead().baseClause() != null) {
                                    for (CPP14Parser.BaseSpecifierContext baseSpec : classSpec.classHead().baseClause()
                                            .baseSpecifierList().baseSpecifier()) {
                                        String superClassName = baseSpec.baseTypeSpecifier().getText();
                                        umlClass.setSuperClass(new Class(superClassName, false));
                                    }
                                }

                                if (classSpec.memberSpecification() != null) {
                                    for (CPP14Parser.MemberdeclarationContext memberDecl : classSpec
                                            .memberSpecification().memberdeclaration()) {
                                        if (memberDecl.memberDeclaratorList().memberDeclarator() != null) {
                                            System.out.println("メンバー取得！");
                                        }
                                    }
                                }

                                umlClassList.add(umlClass); // 解析したクラスをリストに追加
                            }
                        }
                    }
                }
            }
        }

        return umlClassList; // UMLクラスリストを返す
    }

    @Override
    public CompilationUnit translateUmlToCode(List<Class> classList) {
        // UMLからコードへの変換処理
        return new CompilationUnit();
    }

    @Override
    public FieldDeclaration translateAttribute(Attribute attribute) {
        // 属性をFieldDeclarationに変換する処理
        return new FieldDeclaration();
    }

    @Override
    public MethodDeclaration translateOperation(Operation operation) {
        // 操作をMethodDeclarationに変換する処理
        return new MethodDeclaration();
    }

    @Override
    protected Optional<Modifier.Keyword> toSourceCodeVisibility(Visibility visibility) {
        // 可視性をJavaコードに変換する処理
        return Optional.of(Modifier.Keyword.PUBLIC);
    }

    @Override
    protected com.github.javaparser.ast.type.Type toSourceCodeType(Type umlType) {
        // 型をJavaの型に変換する処理
        return com.github.javaparser.ast.type.PrimitiveType.intType();
    }
}
