// translator/CppTranslator.java
package io.github.morichan.retuss.translator;

import io.github.morichan.retuss.translator.common.AbstractTranslator;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.Interaction;
import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;

public class CppTranslator extends AbstractTranslator {
    private Map<String, Class> classMap = new HashMap<>();
    private Map<String, String> typeMap = new HashMap<>();

    public CppTranslator() {
        initializeTypeMap();
    }

    private void initializeTypeMap() {
        typeMap.put("int", "int");
        typeMap.put("double", "double");
        typeMap.put("float", "float");
        typeMap.put("string", "std::string");
        typeMap.put("boolean", "bool");
        typeMap.put("void", "void");
        typeMap.put("char", "char");
        typeMap.put("long", "long");
    }

    @Override
    public List<Class> translateCodeToUml(String code) {
        classMap.clear();

        if (code == null || code.trim().isEmpty()) {
            System.out.println("Empty code provided");
            return new ArrayList<>();
        }

        try {
            // プリプロセッサディレクティブを一時的に削除
            String processedCode = code.replaceAll("#.*\\n", "\n");

            CharStream input = CharStreams.fromString(processedCode);
            CPP14Lexer lexer = new CPP14Lexer(input);

            // カスタムエラーハンドリング
            lexer.removeErrorListeners();
            lexer.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                        int line, int charPositionInLine, String msg, RecognitionException e) {
                    System.err.println("Lexer error at " + line + ":" + charPositionInLine + " - " + msg);
                }
            });

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);
            System.err.println(parser);

            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                        int line, int charPositionInLine, String msg, RecognitionException e) {
                    System.err.println("Parser error at " + line + ":" + charPositionInLine + " - " + msg);
                }
            });

            // デバッグモードを有効化
            parser.setTrace(true);

            ParseTree tree = parser.translationUnit();
            System.out.println("Abstract Syntax Tree (AST):");
            System.out.println(tree.toStringTree(parser));

            if (parser.getNumberOfSyntaxErrors() > 0) {
                System.err.println("Failed to parse C++ code: " + parser.getNumberOfSyntaxErrors() + " errors");
                return new ArrayList<>();
            }

            CppHeaderParseListener listener = new CppHeaderParseListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            resolveInheritance();

            return new ArrayList<>(classMap.values());
        } catch (Exception e) {
            System.err.println("Error in translateCodeToUml: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * ヘッダーファイルとソースファイルの両方からUML情報を抽出
     */
    public List<Class> translateCodeToUml(String headerCode, String implCode) {
        classMap.clear();

        try {
            // まずヘッダーファイルを解析
            parseHeaderFile(headerCode);

            // 次に実装ファイルを解析
            if (implCode != null && !implCode.trim().isEmpty()) {
                parseImplementationFile(implCode);
            }

            // 継承関係を解決
            resolveInheritance();

            return new ArrayList<>(classMap.values());
        } catch (Exception e) {
            System.err.println("Error in translateCodeToUml: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void parseHeaderFile(String code) {
        if (code == null || code.trim().isEmpty())
            return;

        try {
            // プリプロセッサディレクティブを一時的に削除
            String processedCode = code.replaceAll("#.*\\n", "\n");

            CharStream input = CharStreams.fromString(processedCode);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            ParseTree tree = parser.translationUnit();
            System.out.println("Parsing header file AST:");
            System.out.println(tree.toStringTree(parser));

            CppHeaderParseListener listener = new CppHeaderParseListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);
        } catch (Exception e) {
            System.err.println("Error parsing header file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseImplementationFile(String code) {
        if (code == null || code.trim().isEmpty())
            return;

        try {
            CharStream input = CharStreams.fromString(code);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            ParseTree tree = parser.translationUnit();
            System.out.println("Parsing implementation file AST:");
            System.out.println(tree.toStringTree(parser));

            CppImplParseListener listener = new CppImplParseListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);
        } catch (Exception e) {
            System.err.println("Error parsing implementation file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private class CppImplParseListener extends CPP14ParserBaseListener {
        private Class currentClass;
        private Set<String> usedTypes = new HashSet<>();

        @Override
        public void enterFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
            try {
                if (ctx.declarator() != null) {
                    String className = extractClassName(ctx.declarator());
                    if (!className.isEmpty()) {
                        currentClass = classMap.get(className);
                        System.out.println("Current class in function definition: " + className);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in enterFunctionDefinition: " + e.getMessage());
            }
        }

        @Override
        public void enterSimpleDeclaration(CPP14Parser.SimpleDeclarationContext ctx) {
            try {
                if (currentClass != null && ctx.declSpecifierSeq() != null) {
                    // 型の使用を検出
                    String type = ctx.declSpecifierSeq().getText();
                    System.out.println("Found type usage: " + type);
                    usedTypes.add(type);
                }
            } catch (Exception e) {
                System.err.println("Error in enterSimpleDeclaration: " + e.getMessage());
            }
        }

        @Override
        public void enterDeclarator(CPP14Parser.DeclaratorContext ctx) {
            try {
                if (currentClass != null && ctx.getText().contains("::")) {
                    // スコープ解決演算子を含む宣言を検出
                    String[] parts = ctx.getText().split("::");
                    if (parts.length > 1) {
                        String referencedClass = parts[0];
                        System.out.println("Found class reference: " + referencedClass);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in enterDeclarator: " + e.getMessage());
            }
        }

        private String extractClassName(CPP14Parser.DeclaratorContext ctx) {
            String text = ctx.getText();
            int scopePos = text.indexOf("::");
            if (scopePos > 0) {
                return text.substring(0, scopePos);
            }
            return "";
        }
    }

    private void resolveInheritance() {
        for (Map.Entry<String, Class> entry : classMap.entrySet()) {
            Class currentClass = entry.getValue();
            if (currentClass.getSuperClass().isPresent()) {
                String superClassName = currentClass.getSuperClass().get().getName();
                Class superClass = classMap.get(superClassName);
                if (superClass != null) {
                    currentClass.setSuperClass(superClass);
                }
            }
        }
    }

    @Override
    public Object translateUmlToCode(List<Class> classList) {
        StringBuilder code = new StringBuilder();

        // ヘッダーインクルード
        Set<String> includes = new HashSet<>();
        includes.add("<string>");

        // クラスの解析からインクルードを追加
        for (Class umlClass : classList) {
            for (Attribute attr : umlClass.getAttributeList()) {
                String type = attr.getType().toString();
                if (type.contains("vector"))
                    includes.add("<vector>");
                if (type.contains("map"))
                    includes.add("<map>");
            }
        }

        // ヘッダーガード
        if (!classList.isEmpty()) {
            String guardName = classList.get(0).getName().toUpperCase() + "_H";
            code.append("#ifndef ").append(guardName).append("\n");
            code.append("#define ").append(guardName).append("\n\n");

            // インクルード文の追加
            for (String include : includes) {
                code.append("#include ").append(include).append("\n");
            }
            code.append("\n");
        }

        // クラス定義
        for (Class umlClass : classList) {
            code.append(translateClass(umlClass)).append("\n\n");
        }

        // ヘッダーガード終了
        if (!classList.isEmpty()) {
            code.append("#endif // ").append(classList.get(0).getName().toUpperCase()).append("_H\n");
        }

        return code.toString();
    }

    private String translateClass(Class umlClass) {
        StringBuilder builder = new StringBuilder();

        // クラスコメント（アクティブクラスの場合）
        if (umlClass.getActive()) {
            builder.append("// Active class\n");
        }

        // クラス宣言（classキーワードの重複を防ぐ）
        builder.append("class ").append(umlClass.getName());

        // 継承
        if (umlClass.getSuperClass().isPresent()) {
            builder.append(" : public ").append(umlClass.getSuperClass().get().getName());
        }

        builder.append(" {\n");

        // 属性のグループ化
        Map<String, List<Attribute>> attributesByVisibility = new HashMap<>();
        for (Attribute attr : umlClass.getAttributeList()) {
            String visibility = attr.getVisibility().toString();
            attributesByVisibility
                    .computeIfAbsent(visibility, k -> new ArrayList<>())
                    .add(attr);
        }

        // 属性の出力
        for (Map.Entry<String, List<Attribute>> entry : attributesByVisibility.entrySet()) {
            builder.append(entry.getKey().toLowerCase()).append(":\n");
            for (Attribute attr : entry.getValue()) {
                builder.append("    ").append(translateAttribute(attr)).append(";\n");
            }
            builder.append("\n");
        }

        // メソッドの出力（アクセス修飾子でグループ化）
        Map<String, List<Operation>> operationsByVisibility = new HashMap<>();
        for (Operation op : umlClass.getOperationList()) {
            String visibility = op.getVisibility().toString();
            operationsByVisibility
                    .computeIfAbsent(visibility, k -> new ArrayList<>())
                    .add(op);
        }

        for (Map.Entry<String, List<Operation>> entry : operationsByVisibility.entrySet()) {
            builder.append(entry.getKey().toLowerCase()).append(":\n");
            for (Operation op : entry.getValue()) {
                // 抽象メソッドの場合
                if (umlClass.getAbstruct() && op.toString().contains("abstract")) {
                    builder.append("    virtual ")
                            .append(translateOperation(op))
                            .append(" = 0;\n");
                } else {
                    builder.append("    ").append(translateOperation(op)).append("\n");
                }
            }
            builder.append("\n");
        }

        builder.append("};\n");

        return builder.toString();
    }

    @Override
    public Object translateAttribute(Attribute attribute) {
        StringBuilder builder = new StringBuilder();

        // constメンバの場合
        if (attribute.toString().contains("const")) {
            builder.append("const ");
        }

        // 型と名前
        builder.append(toSourceCodeType(attribute.getType()))
                .append(" ")
                .append(attribute.getName());

        return builder.toString();
    }

    @Override
    public Object translateOperation(Operation operation) {
        StringBuilder builder = new StringBuilder();

        // 仮想関数の場合
        if (operation.toString().contains("virtual")) {
            builder.append("virtual ");
        }

        // 戻り値の型と関数名
        builder.append(toSourceCodeType(operation.getReturnType()))
                .append(" ")
                .append(operation.getName())
                .append("(");

        // パラメータ
        List<String> params = new ArrayList<>();
        operation.getParameters().forEach(param -> params.add(String.format("%s %s",
                toSourceCodeType(param.getType()),
                param.getName())));
        builder.append(String.join(", ", params));

        builder.append(");");

        return builder.toString();
    }

    @Override
    protected Object toSourceCodeVisibility(Visibility visibility) {
        switch (visibility.toString()) {
            case "PUBLIC":
                return "public";
            case "PRIVATE":
                return "private";
            case "PROTECTED":
                return "protected";
            default:
                return "private";
        }
    }

    @Override
    protected Object toSourceCodeType(Type umlType) {
        String typeName = umlType.toString();
        return typeMap.getOrDefault(typeName, typeName);
    }

    private class CppHeaderParseListener extends CPP14ParserBaseListener {
        private Class currentClass;
        private String currentVisibility = "private";

        @Override
        public void enterMemberDeclarator(CPP14Parser.MemberDeclaratorContext ctx) {
            try {
                if (currentClass == null)
                    return;

                if (ctx.getParent() instanceof CPP14Parser.MemberDeclaratorListContext) {
                    CPP14Parser.MemberdeclarationContext memberCtx = (CPP14Parser.MemberdeclarationContext) ctx
                            .getParent().getParent();

                    // メソッド宣言はスキップ
                    if (ctx.getText().contains("(")) {
                        System.out.println("Skipping method declaration");
                        return;
                    }

                    if (memberCtx != null && memberCtx.declSpecifierSeq() != null) {
                        String type = memberCtx.declSpecifierSeq().getText();
                        String name = ctx.declarator().getText();

                        System.out.println("Processing member: type=" + type + ", name=" + name +
                                ", visibility=" + currentVisibility);

                        try {
                            // Nameオブジェクトを確実に生成
                            Name attributeName = new Name(name);
                            // Typeオブジェクトを確実に生成
                            Type attributeType = new Type(type);

                            // 属性の作成
                            Attribute attribute = new Attribute(attributeName);
                            attribute.setName(attributeName);
                            attribute.setType(attributeType);
                            attribute.setVisibility(Visibility.valueOf(currentVisibility.toUpperCase()));

                            // クラスに属性を追加
                            currentClass.addAttribute(attribute);
                            System.out.println("Successfully added attribute: " + attribute);
                        } catch (Exception e) {
                            System.err.println("Error creating attribute: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in enterMemberDeclarator: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void enterClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
            try {
                if (ctx.classHead() != null &&
                        ctx.classHead().classHeadName() != null &&
                        ctx.classHead().classHeadName().className() != null) {

                    String className = ctx.classHead().classHeadName().className().getText();
                    System.out.println("Found class: " + className);

                    currentClass = new Class(className);
                    classMap.put(className, currentClass);
                }
            } catch (Exception e) {
                System.err.println("Error in enterClassSpecifier: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void enterAccessSpecifier(CPP14Parser.AccessSpecifierContext ctx) {
            if (ctx != null && ctx.getText() != null) {
                currentVisibility = ctx.getText().toLowerCase();
                System.out.println("Access specifier changed to: " + currentVisibility);
            }
        }

        @Override
        public void exitClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
            currentClass = null;
            currentVisibility = "private";
            System.out.println("Exiting class definition");
        }
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

                @Override
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
}