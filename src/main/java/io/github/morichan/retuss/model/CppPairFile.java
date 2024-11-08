package io.github.morichan.retuss.model;

import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.CPP14Lexer;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.parser.cpp.CPP14ParserBaseListener;
import io.github.morichan.retuss.translator.CppTranslator;

import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class CppPairFile implements ICodeFile {
    private final UUID ID = UUID.randomUUID();
    private CppFile headerFile;
    private CppFile implFile;
    private CppTranslator translator = new CppTranslator();
    private Set<String> dependencies = new HashSet<>();
    private List<Class> mergedUmlClassList = new ArrayList<>();
    private String className;

    public CppPairFile(String baseName) {
        this.headerFile = new CppFile(baseName + ".hpp", true);
        this.implFile = new CppFile(baseName + ".cpp", false);
        initialize(baseName);

        // ヘッダファイルの変更を監視
        headerFile.addChangeListener(new CppFile.FileChangeListener() {
            @Override
            public void onFileChanged(CppFile file) {
                updateImplementationIncludes();
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                // 実装ファイルの名前も更新
                String oldBaseName = oldName.replace(".hpp", "");
                String newBaseName = newName.replace(".hpp", "");
                String oldImplName = implFile.getFileName();
                String newImplName = oldImplName.replace(oldBaseName, newBaseName);

                // 実装ファイルの更新
                StringBuilder newCode = new StringBuilder();
                newCode.append("#include \"").append(newName).append("\"\n\n");
                newCode.append(implFile.getCode().substring(implFile.getCode().indexOf("\n\n") + 2));
                implFile.updateCode(newCode.toString());
            }
        });
    }

    private void initialize(String baseName) {
        // 初期化処理
        StringBuilder sb = new StringBuilder();
        sb.append("#include \"").append(baseName).append(".hpp\"\n\n");
        implFile.updateCode(sb.toString());
    }

    @Override
    public UUID getID() {
        return ID;
    }

    @Override
    public String getFileName() {
        return headerFile.getFileName(); // ペアを代表してヘッダーファイル名を返す
    }

    @Override
    public List<CppFile> getFiles() {
        return Arrays.asList(headerFile, implFile);
    }

    // メインファイル（ヘッダー）かどうかの判定
    @Override
    public boolean isMainFile() {
        return true; // ヘッダーファイルをメインとする
    }

    @Override
    public String getCode() {
        return headerFile.getCode(); // ペアを代表してヘッダーファイルのコードを返す
    }

    @Override
    public List<Class> getUmlClassList() {
        // ヘッダーファイルの基本情報を取得
        List<Class> baseClasses = new ArrayList<>(headerFile.getUmlClassList());

        if (!baseClasses.isEmpty() && implFile != null) {
            // 実装ファイルから関係性を解析して統合
            Class mainClass = baseClasses.get(0);
            analyzeImplementationRelationships(mainClass);
            mergedUmlClassList = baseClasses; // 統合結果を保存
        }

        return Collections.unmodifiableList(mergedUmlClassList);
    }

    @Override
    public void updateCode(String code) {
        // クラス名の抽出と更新
        Optional<String> newClassName = translator.extractClassName(code);
        newClassName.ifPresent(name -> {
            if (!name.equals(className)) {
                className = name;
                // ヘッダーファイルとインプリメンテーションファイルの名前を更新
                String oldHeaderName = headerFile.getFileName();
                String newHeaderName = name + ".hpp";
                String oldImplName = implFile.getFileName();
                String newImplName = name + ".cpp";
                // 実装ファイルの内容も更新
                implFile.getCode().replace(oldHeaderName, newHeaderName);
            }

            headerFile.updateCode(code);
            implFile.updateCode(implFile.getCode());
            System.err.println("ヘッダー！ = " + headerFile.getCode());
            System.err.println("実装！ = " + implFile.getCode());

        });
    }

    @Override
    public void addUmlClass(Class umlClass) {
        // ヘッダーファイルにクラスを追加
        headerFile.addUmlClass(umlClass);
        // 実装ファイルのインクルードを更新
        updateImplementationIncludes();
    }

    @Override
    public void removeClass(Class umlClass) {
        headerFile.removeClass(umlClass);
        // 実装ファイルも更新
        initialize(umlClass.getName());
    }

    public CppFile getFileByType(boolean isHeader) {
        return isHeader ? headerFile : implFile;
    }

    // CppPairFile固有のメソッド
    public CppFile getHeaderFile() {
        return headerFile;
    }

    public CppFile getImplFile() {
        return implFile;
    }

    private void updateImplFileName(String oldHeaderName, String newHeaderName) {
        String oldBaseName = oldHeaderName.replace(".hpp", "");
        String newBaseName = newHeaderName.replace(".hpp", "");
        String oldImplName = implFile.getFileName();
        String newImplName = oldImplName.replace(oldBaseName, newBaseName);

        // 実装ファイルの更新
        StringBuilder newCode = new StringBuilder();
        newCode.append("#include \"").append(newHeaderName).append("\"\n\n");
        String existingCode = implFile.getCode();
        int implStart = existingCode.indexOf("\n\n");
        if (implStart != -1) {
            newCode.append(existingCode.substring(implStart + 2));
        }
        implFile.updateCode(newCode.toString());
    }

    public void addInclude(String headerName) {
        dependencies.add(headerName);
        updateHeaderIncludes();
    }

    private void analyzeImplementationRelationships(Class mainClass) {
        try {
            String implCode = implFile.getCode();
            if (implCode == null || implCode.trim().isEmpty())
                return;

            // 実装ファイルのパース
            CharStream input = CharStreams.fromString(implCode);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            // 実装関係の解析用リスナー
            ImplRelationshipAnalyzer analyzer = new ImplRelationshipAnalyzer(mainClass);
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(analyzer, parser.translationUnit());

            // 解析結果を反映
            applyRelationships(mainClass, analyzer.getRelationships());

        } catch (Exception e) {
            System.err.println("Failed to analyze implementation relationships: " + e.getMessage());
        }
    }

    private void updateHeaderIncludes() {
        String code = headerFile.getCode();
        int classPos = code.indexOf("class");
        if (classPos == -1)
            return;

        StringBuilder newCode = new StringBuilder();

        // ヘッダーガードを保持
        int guardEnd = code.indexOf("\n\n");
        if (guardEnd != -1) {
            newCode.append(code.substring(0, guardEnd + 2));
        }

        // インクルードを追加（重複を避ける）
        Set<String> addedIncludes = new HashSet<>();
        for (String dep : dependencies) {
            String includeLine = dep.startsWith("<") ? "#include " + dep : "#include \"" + dep + "\"";
            if (addedIncludes.add(includeLine)) {
                newCode.append(includeLine).append("\n");
            }
        }
        newCode.append("\n");

        // 残りのコードを追加
        newCode.append(code.substring(classPos));
        headerFile.updateCode(newCode.toString());
    }

    private void updateImplementationIncludes() {
        String currentImplCode = implFile.getCode();
        String headerInclude = "#include \"" + headerFile.getFileName() + "\"\n";

        // 実装ファイルのコード更新
        StringBuilder newCode = new StringBuilder();
        newCode.append(headerInclude);

        // 既存のコードの処理
        int implStart = currentImplCode.indexOf("\n\n");
        if (implStart != -1 && currentImplCode.length() > implStart + 2) {
            // 既存のコードがある場合は維持
            String existingCode = currentImplCode.substring(implStart + 2).trim();
            if (!existingCode.isEmpty()) {
                newCode.append("\n").append(existingCode);
            }
        }

        implFile.updateCode(newCode.toString());
    }

    public void updateHeaderCode(String code) {
        headerFile.updateCode(code);
    }

    public void updateImplementationCode(String code) {
        // ヘッダーインクルードの確保
        String headerInclude = "#include \"" + headerFile.getFileName() + "\"";
        if (!code.contains(headerInclude)) {
            StringBuilder newCode = new StringBuilder();
            newCode.append(headerInclude).append("\n\n");
            if (!code.startsWith(headerInclude)) {
                newCode.append(code);
            }
            code = newCode.toString();
        }
        implFile.updateCode(code);
    }

    private class ImplRelationshipListener extends CPP14ParserBaseListener {
        private Class mainClass;
        private Set<String> usedClasses = new HashSet<>();

        public ImplRelationshipListener(Class mainClass) {
            this.mainClass = mainClass;
        }

        @Override
        public void enterSimpleDeclaration(CPP14Parser.SimpleDeclarationContext ctx) {
            try {
                if (ctx.declSpecifierSeq() != null) {
                    String type = ctx.declSpecifierSeq().getText();
                    System.out.println("Found type usage: " + type);
                    usedClasses.add(type);
                }
            } catch (Exception e) {
                System.err.println("Error in enterSimpleDeclaration: " + e.getMessage());
            }
        }

        @Override
        public void enterFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
            try {
                if (ctx.declarator() != null) {
                    // メソッド実装内の依存関係を検出
                    String text = ctx.declarator().getText();
                    int scopePos = text.indexOf("::");
                    if (scopePos > 0) {
                        String className = text.substring(0, scopePos);
                        System.out.println("Found method implementation for class: " + className);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in enterFunctionDefinition: " + e.getMessage());
            }
        }

        @Override
        public void enterDeclarator(CPP14Parser.DeclaratorContext ctx) {
            try {
                String text = ctx.getText();
                // インスタンス生成や型使用の検出
                if (text.contains("new ")) {
                    String[] parts = text.split("new\\s+");
                    if (parts.length > 1) {
                        String typeName = parts[1].split("[^a-zA-Z0-9_]")[0];
                        System.out.println("Found object creation of type: " + typeName);
                        usedClasses.add(typeName);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in enterDeclarator: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CppPairFile that = (CppPairFile) o;
        return ID.equals(that.ID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ID);
    }

    private class ImplRelationshipAnalyzer extends CPP14ParserBaseListener {
        private final Class mainClass;
        private final Map<String, RelationType> relationships = new HashMap<>();
        private String currentMethod = null;

        public ImplRelationshipAnalyzer(Class mainClass) {
            this.mainClass = mainClass;
        }

        @Override
        public void enterFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
            if (ctx.declarator() != null) {
                String text = ctx.declarator().getText();
                // クラスのメソッド定義を検出（例：ClassName::methodName）
                int scopePos = text.indexOf("::");
                if (scopePos > 0) {
                    currentMethod = text.substring(scopePos + 2);
                }
            }
        }

        @Override
        public void exitFunctionDefinition(CPP14Parser.FunctionDefinitionContext ctx) {
            currentMethod = null;
        }

        @Override
        public void enterSimpleDeclaration(CPP14Parser.SimpleDeclarationContext ctx) {
            if (ctx.declSpecifierSeq() != null) {
                String type = ctx.declSpecifierSeq().getText();
                // メンバー変数の型を解析して依存関係を追加
                if (!isBuiltInType(type)) {
                    relationships.put(type, RelationType.DEPENDENCY);
                }
            }
        }

        @Override
        public void enterDeclarator(CPP14Parser.DeclaratorContext ctx) {
            String text = ctx.getText();

            // newによるインスタンス生成を検出（集約/コンポジション関係）
            if (text.contains("new ")) {
                String[] parts = text.split("new\\s+");
                if (parts.length > 1) {
                    String type = parts[1].split("[^a-zA-Z0-9_]")[0];
                    relationships.put(type, RelationType.AGGREGATION);
                }
            }

            // ポインタメンバを検出（コンポジション関係の可能性）
            if (text.contains("*") && currentMethod == null) {
                String type = text.replaceAll("[*&]", "").trim();
                if (!isBuiltInType(type)) {
                    relationships.put(type, RelationType.COMPOSITION);
                }
            }
        }

        private boolean isBuiltInType(String type) {
            Set<String> builtInTypes = Set.of("int", "char", "bool", "float", "double", "void", "long");
            return builtInTypes.contains(type) || type.startsWith("std::");
        }

        public Map<String, RelationType> getRelationships() {
            return relationships;
        }
    }

    private enum RelationType {
        DEPENDENCY,
        AGGREGATION,
        COMPOSITION
    }

    private void applyRelationships(Class mainClass, Map<String, RelationType> relationships) {
        for (Map.Entry<String, RelationType> entry : relationships.entrySet()) {
            String targetClassName = entry.getKey();
            RelationType relationType = entry.getValue();

            switch (relationType) {
                case DEPENDENCY:
                    // 依存関係の追加（UMLモデルに依存関係を追加するメソッドが必要）
                    addDependency(mainClass, targetClassName);
                    break;
                case AGGREGATION:
                    // 集約関係の追加
                    addAggregation(mainClass, targetClassName);
                    break;
                case COMPOSITION:
                    // コンポジション関係の追加
                    addComposition(mainClass, targetClassName);
                    break;
            }
        }
    }

    private void addDependency(Class source, String targetClassName) {
        // UMLモデルに依存関係を追加
        // 実装は使用しているUMLライブラリに依存
    }

    private void addAggregation(Class source, String targetClassName) {
        // UMLモデルに集約関係を追加
    }

    private void addComposition(Class source, String targetClassName) {
        // UMLモデルにコンポジション関係を追加
    }
}