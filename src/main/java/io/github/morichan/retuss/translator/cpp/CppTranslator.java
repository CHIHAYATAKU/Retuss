package io.github.morichan.retuss.translator.cpp;

import io.github.morichan.retuss.translator.common.*;
import io.github.morichan.retuss.translator.cpp.listeners.CppMethodAnalyzer;
import io.github.morichan.retuss.translator.cpp.util.*;
import io.github.morichan.retuss.translator.model.MethodCall;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.fescue.feature.*;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class CppTranslator extends AbstractLanguageTranslator {
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;

    public CppTranslator() {
        super();
        this.typeMapper = new CppTypeMapper();
        this.visibilityMapper = new CppVisibilityMapper();
    }

    @Override
    public String translateVisibility(Visibility visibility) {
        return visibilityMapper.toSourceCode(visibility);
    }

    @Override
    public String translateType(Type type) {
        return typeMapper.mapType(type.toString());
    }

    @Override
    public String translateAttribute(Attribute attribute) {
        StringBuilder sb = new StringBuilder();

        // 型と名前
        sb.append(translateType(attribute.getType()))
                .append(" ")
                .append(attribute.getName());

        // デフォルト値がある場合
        try {
            if (attribute.getDefaultValue() != null) {
                sb.append(" = ").append(attribute.getDefaultValue().toString());
            }
        } catch (IllegalStateException e) {
            // デフォルト値なし
        }

        return sb.toString();
    }

    @Override
    public String translateOperation(Operation operation) {
        StringBuilder sb = new StringBuilder();

        // 戻り値の型と名前
        sb.append(translateType(operation.getReturnType()))
                .append(" ")
                .append(operation.getName())
                .append("(");

        // パラメータ
        List<String> params = new ArrayList<>();
        operation.getParameters().forEach(param -> params.add(String.format("%s %s",
                translateType(param.getType()),
                param.getName())));
        sb.append(String.join(", ", params));

        sb.append(")");

        return sb.toString();
    }

    @Override
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

    @Override
    protected CodeToUmlTranslator createCodeToUmlTranslator() {
        return new CppToUmlTranslator();
    }

    @Override
    protected UmlToCodeTranslator createUmlToCodeTranslator() {
        return new UmlToCppTranslator();
    }

    @Override
    public String generateClassDiagram(List<Class> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n\n");

        for (Class cls : classes) {
            if (cls.getAbstruct()) {
                sb.append("abstract ");
            }
            sb.append("class ").append(cls.getName()).append(" {\n");

            // 属性
            for (Attribute attr : cls.getAttributeList()) {
                sb.append("  ")
                        .append(visibilityMapper.toSymbol(attr.getVisibility()))
                        .append(" ")
                        .append(translateAttribute(attr))
                        .append("\n");
            }

            // 操作
            for (Operation op : cls.getOperationList()) {
                sb.append("  ")
                        .append(visibilityMapper.toSymbol(op.getVisibility()))
                        .append(" ")
                        .append(translateOperation(op))
                        .append("\n");
            }

            sb.append("}\n\n");
        }

        sb.append("@enduml");
        return sb.toString();
    }

    @Override
    public String generateSequenceDiagram(
            String headerCode,
            String implCode,
            String methodName) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n\n");

        try {
            // ヘッダーファイルの解析でクラス構造を取得
            List<Class> classes = translateCodeToUml(headerCode);

            // 実装ファイルの解析でメソッド呼び出しを取得
            List<MethodCall> methodCalls = analyzeMethodCalls(implCode, methodName);
            Set<String> participants = new HashSet<>();

            // クラス情報とメソッド呼び出しを関連付け
            for (MethodCall call : methodCalls) {
                if (!call.getCaller().isEmpty())
                    participants.add(call.getCaller());
                if (!call.getCallee().isEmpty())
                    participants.add(call.getCallee());

                // クラスの存在確認と型情報の補完
                for (Class cls : classes) {
                    if (cls.getName().equals(call.getCaller()) ||
                            cls.getName().equals(call.getCallee())) {
                        participants.add(cls.getName());
                    }
                }
            }

            // 参加者の定義
            for (String participant : participants) {
                sb.append("participant ").append(participant).append("\n");
            }

            sb.append("\n");

            // メソッド呼び出しシーケンスの生成
            generateMethodCallSequence(sb, methodCalls);

        } catch (Exception e) {
            System.err.println("Failed to generate sequence diagram: " + e.getMessage());
            e.printStackTrace();
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private List<MethodCall> analyzeMethodCalls(String code, String methodName) {
        try {
            CharStream input = CharStreams.fromString(code);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            CppMethodAnalyzer analyzer = new CppMethodAnalyzer();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(analyzer, parser.translationUnit());

            return analyzer.getMethodCalls();
        } catch (Exception e) {
            System.err.println("Failed to analyze method calls: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void generateMethodCallSequence(StringBuilder sb, List<MethodCall> methodCalls) {
        int currentNestLevel = 0;
        Stack<String> activeGroups = new Stack<>();

        for (MethodCall call : methodCalls) {
            // ネストレベルの調整
            while (currentNestLevel > call.getNestingLevel()) {
                currentNestLevel--;
                if (!activeGroups.empty()) {
                    sb.append("  ".repeat(currentNestLevel))
                            .append("end ").append(activeGroups.pop()).append("\n");
                }
            }

            if (call.getNestingLevel() > currentNestLevel) {
                String groupType = call.getStructureType() == MethodCall.ControlStructureType.LOOP ? "loop" : "alt";
                String condition = call.getCondition() != null ? call.getCondition() : "";
                sb.append("  ".repeat(currentNestLevel))
                        .append(groupType).append(" ").append(condition).append("\n");
                activeGroups.push(groupType);
                currentNestLevel = call.getNestingLevel();
            }

            // メソッド呼び出しの表示
            sb.append("  ".repeat(currentNestLevel))
                    .append(call.getCaller())
                    .append(" -> ")
                    .append(call.getCallee())
                    .append(" : ")
                    .append(call.getMethodName())
                    .append("(")
                    .append(String.join(", ", call.getArguments()))
                    .append(")\n");
        }

        // 残りのグループを閉じる
        while (!activeGroups.empty()) {
            currentNestLevel--;
            sb.append("  ".repeat(currentNestLevel))
                    .append("end ").append(activeGroups.pop()).append("\n");
        }
    }
}