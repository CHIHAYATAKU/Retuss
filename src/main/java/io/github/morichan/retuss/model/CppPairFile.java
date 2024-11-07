package io.github.morichan.retuss.model;

import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.CPP14Lexer;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.parser.cpp.CPP14ParserBaseListener;

import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class CppPairFile {
    private final UUID ID = UUID.randomUUID();
    private CppFile headerFile;
    private CppFile implFile;
    private Set<String> dependencies = new HashSet<>();
    private List<Class> mergedUmlClassList = new ArrayList<>();

    public CppPairFile(String baseName) {
        this.headerFile = new CppFile(baseName + ".hpp", true);
        this.implFile = new CppFile(baseName + ".cpp", false);
        initializeImplFile(baseName);

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

    private void initializeImplFile(String baseName) {
        StringBuilder sb = new StringBuilder();
        sb.append("#include \"").append(baseName).append(".hpp\"\n\n");
        implFile.updateCode(sb.toString());
    }

    public UUID getID() {
        return ID;
    }

    public CppFile getHeaderFile() {
        return headerFile;
    }

    public CppFile getImplFile() {
        return implFile;
    }

    public List<Class> getUmlClassList() {
        // ヘッダーファイルからの基本情報
        List<Class> headerClasses = headerFile.getUmlClassList();

        // 実装ファイルからの追加情報を統合
        if (!headerClasses.isEmpty() && implFile != null) {
            Class mainClass = headerClasses.get(0);

            // 実装ファイルのコードを解析して関係性を抽出
            analyzeImplementationRelationships(mainClass, implFile.getCode());

            mergedUmlClassList = headerClasses;
        }

        return Collections.unmodifiableList(mergedUmlClassList);
    }

    private void analyzeImplementationRelationships(Class mainClass, String implCode) {
        // 実装ファイルから関係性を解析
        // 例：
        // - メソッド内での他クラスのインスタンス化
        // - フレンド関係
        // - 集約関係
        // - その他の依存関係
        try {
            if (implCode != null && !implCode.trim().isEmpty()) {
                // 実装ファイルのパース
                CharStream input = CharStreams.fromString(implCode);
                CPP14Lexer lexer = new CPP14Lexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                CPP14Parser parser = new CPP14Parser(tokens);

                // カスタムリスナーで関係性を抽出
                ImplRelationshipListener listener = new ImplRelationshipListener(mainClass);
                ParseTreeWalker walker = new ParseTreeWalker();
                walker.walk(listener, parser.translationUnit());
            }
        } catch (Exception e) {
            System.err.println("Failed to analyze implementation relationships: " + e.getMessage());
        }
    }

    public void addInclude(String headerName) {
        dependencies.add(headerName);
        updateHeaderIncludes();
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
}