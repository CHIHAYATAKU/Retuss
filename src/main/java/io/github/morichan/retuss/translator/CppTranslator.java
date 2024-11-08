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

    private class CppClassExtractor extends CPP14ParserBaseListener {
        private List<Class> extractedClasses = new ArrayList<>();
        private Class currentClass = null;
        private String currentVisibility = "private"; // デフォルトの可視性

        @Override
        public void enterClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
            if (ctx.classHead() != null &&
                    ctx.classHead().classHeadName() != null &&
                    ctx.classHead().classHeadName().className() != null) {

                String className = ctx.classHead().classHeadName().className().getText();
                System.out.println("DEBUG: Found class: " + className);
                currentClass = new Class(className);
            }
        }

        @Override
        public void exitClassSpecifier(CPP14Parser.ClassSpecifierContext ctx) {
            if (currentClass != null) {
                extractedClasses.add(currentClass);
                System.out.println("DEBUG: Finished processing class: " + currentClass.getName());
                dumpClassInfo(currentClass);
                currentClass = null;
                currentVisibility = "private"; // リセット
            }
        }

        @Override
        public void enterMemberSpecification(CPP14Parser.MemberSpecificationContext ctx) {
            if (ctx.accessSpecifier() != null && !ctx.accessSpecifier().isEmpty()) {
                currentVisibility = ctx.accessSpecifier().get(0).getText().toLowerCase();
                System.out.println("DEBUG: Access specifier found: " + currentVisibility);
            }
        }

        @Override
        public void enterMemberdeclaration(CPP14Parser.MemberdeclarationContext ctx) {
            if (currentClass == null)
                return;

            try {
                if (ctx.declSpecifierSeq() != null) {
                    String type = ctx.declSpecifierSeq().getText();
                    System.out.println("DEBUG: Processing member - Type: " + type +
                            ", Current visibility: " + currentVisibility);

                    if (ctx.memberDeclaratorList() != null) {
                        for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList()
                                .memberDeclarator()) {

                            processMemberDeclarator(memberDec, type);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in enterMemberdeclaration: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void processMemberDeclarator(CPP14Parser.MemberDeclaratorContext memberDec, String type) {
            if (memberDec.declarator() == null)
                return;

            CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
            String declaratorText = declarator.getText();

            // パラメータリストの存在を確認してメソッドかを判定
            boolean isMethod = declarator.parametersAndQualifiers() != null;

            System.out.println("DEBUG: Processing declarator: " + declaratorText +
                    ", isMethod: " + isMethod +
                    ", visibility: " + currentVisibility);

            if (isMethod) {
                // メソッドの処理
                String methodName = declaratorText.substring(0, declaratorText.indexOf('('));
                Operation operation = new Operation(new Name(methodName));
                operation.setReturnType(new Type(type));
                operation.setVisibility(convertToVisibility(currentVisibility));

                currentClass.addOperation(operation);
                System.out.println("DEBUG: Added method - " + methodName +
                        " with visibility " + currentVisibility);
            } else {
                // フィールドの処理
                Attribute attribute = new Attribute(new Name(declaratorText));
                attribute.setType(new Type(type));
                attribute.setVisibility(convertToVisibility(currentVisibility));

                currentClass.addAttribute(attribute);
                System.out.println("DEBUG: Added field - " + declaratorText +
                        " with visibility " + currentVisibility);
            }
        }

        private void dumpCurrentState() {
            System.out.println("\nDEBUG: Current Parser State ----");
            System.out.println("Current class: " + (currentClass != null ? currentClass.getName() : "null"));
            System.out.println("Current visibility: " + currentVisibility);
            if (currentClass != null) {
                System.out.println("Current attributes count: " + currentClass.getAttributeList().size());
                for (Attribute attr : currentClass.getAttributeList()) {
                    System.out.println("  - " + attr.getVisibility() + " " +
                            attr.getName() + " : " + attr.getType());
                }
            }
            System.out.println("------------------------\n");
        }

        private Visibility getVisibility(String visibility) {
            if (visibility == null) {
                System.out.println("DEBUG: Using default PRIVATE visibility");
                return Visibility.Private;
            }

            switch (visibility.toUpperCase()) {
                case "PUBLIC":
                    System.out.println("DEBUG: Converting to PUBLIC visibility");
                    return Visibility.Public;
                case "PROTECTED":
                    System.out.println("DEBUG: Converting to PROTECTED visibility");
                    return Visibility.Protected;
                case "PRIVATE":
                    System.out.println("DEBUG: Converting to PRIVATE visibility");
                    return Visibility.Private;
                default:
                    System.out.println("DEBUG: Using default PRIVATE visibility");
                    return Visibility.Private;
            }
        }

        public List<Class> getExtractedClasses() {
            return extractedClasses;
        }
    }

    /**
     * ヘッダーファイルとソースファイルの両方からUML情報を抽出
     */
    @Override
    public List<Class> translateCodeToUml(String code) {
        try {
            System.out.println("\nDEBUG: Starting C++ code translation");
            System.out.println("Original code:\n" + code);

            // プリプロセッサディレクティブを一時的に削除
            String processedCode = code.replaceAll("#.*\\n", "\n");
            System.out.println("\nProcessed code:\n" + processedCode);

            CharStream input = CharStreams.fromString(processedCode);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            ParseTree tree = parser.translationUnit();
            System.out.println("\nDEBUG: Parse Tree:");
            System.out.println(tree.toStringTree(parser));

            CppClassExtractor extractor = new CppClassExtractor();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(extractor, tree);

            List<Class> classes = extractor.getExtractedClasses();
            System.out.println("\nDEBUG: Translation complete. Found " + classes.size() + " classes.");

            return classes;
        } catch (Exception e) {
            System.err.println("Failed to translate C++ code to UML: " + e.getMessage());
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

    private void dumpClassInfo(Class cls) {
        System.out.println("\nDEBUG: Class Dump ----");
        System.out.println("Class name: " + cls.getName());
        System.out.println("Attributes:");
        for (Attribute attr : cls.getAttributeList()) {
            System.out.println("  - Name: " + attr.getName() +
                    ", Type: " + attr.getType() +
                    ", Visibility: " + attr.getVisibility());
        }
        System.out.println("------------------\n");
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
    public String translateUmlToCode(List<Class> classList) { // Object から String に変更
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

        return code.toString(); // toString()を呼び出して文字列を返す
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
    public String translateAttribute(Attribute attribute) { // Object から String に変更
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
    public String translateOperation(Operation operation) { // Object から String に変更
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
        public void enterMemberdeclaration(CPP14Parser.MemberdeclarationContext ctx) {
            if (currentClass == null)
                return;

            try {
                if (ctx.declSpecifierSeq() != null) {
                    String type = ctx.declSpecifierSeq().getText();
                    System.out.println("DEBUG: Found member declaration - Type: " + type +
                            ", Current visibility: " + currentVisibility);

                    if (ctx.memberDeclaratorList() != null) {
                        for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList()
                                .memberDeclarator()) {

                            if (memberDec.declarator() != null) {
                                String declaratorText = memberDec.declarator().getText();

                                // メソッド宣言の判定をより正確に
                                boolean isMethod = isMethodDeclaration(memberDec.declarator());

                                if (isMethod) {
                                    // メソッドの処理
                                    String methodName = extractMethodName(declaratorText);
                                    Operation operation = new Operation(new Name(methodName));
                                    operation.setReturnType(new Type(type));
                                    operation.setVisibility(convertToVisibility(currentVisibility));

                                    // パラメータ処理も必要に応じて追加

                                    currentClass.addOperation(operation);
                                    System.out.println("DEBUG: Added method - Name: " + methodName +
                                            ", Return Type: " + type +
                                            ", Visibility: " + currentVisibility);
                                } else {
                                    // フィールドの処理
                                    Attribute attribute = new Attribute(new Name(declaratorText));
                                    attribute.setType(new Type(type));
                                    attribute.setVisibility(convertToVisibility(currentVisibility));

                                    currentClass.addAttribute(attribute);
                                    System.out.println("DEBUG: Added field - Name: " + declaratorText +
                                            ", Type: " + type +
                                            ", Visibility: " + currentVisibility);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing member declaration: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private boolean isMethodDeclaration(CPP14Parser.DeclaratorContext declarator) {
            // パラメータリストを探す
            if (declarator.parametersAndQualifiers() != null) {
                return true;
            }
            // または他のメソッド判定基準を追加
            return false;
        }

        private String extractMethodName(String declaratorText) {
            // 括弧の前の部分を取得
            int parenIndex = declaratorText.indexOf('(');
            if (parenIndex > 0) {
                return declaratorText.substring(0, parenIndex);
            }
            return declaratorText;
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
            // アクセス指定子を直接取得
            currentVisibility = ctx.getText().toLowerCase();
            System.out.println("DEBUG: Access specifier changed to: " + currentVisibility);
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

    private Visibility convertToVisibility(String visibility) {
        if (visibility == null) {
            System.out.println("DEBUG: Null visibility, using PRIVATE");
            return Visibility.Private;
        }

        System.out.println("DEBUG: Converting visibility: " + visibility);
        Visibility result;
        if (visibility == null) {
            System.out.println("DEBUG: Unknown visibility: null, using PRIVATE");
            result = Visibility.Private;
        } else {
            switch (visibility.toUpperCase()) {
                case "PUBLIC":
                    System.out.println("DEBUG: Set to PUBLIC");
                    result = Visibility.Public;
                    break;
                case "PROTECTED":
                    System.out.println("DEBUG: Set to PROTECTED");
                    result = Visibility.Protected;
                    break;
                case "PRIVATE":
                    System.out.println("DEBUG: Set to PRIVATE");
                    result = Visibility.Private;
                    break;
                default:
                    System.out.println("DEBUG: Unknown visibility: " + visibility + ", using PRIVATE");
                    result = Visibility.Private;
                    break;
            }
        }
        return result;

    }
}