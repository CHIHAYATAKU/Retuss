package io.github.morichan.retuss.translator.cpp;

import io.github.morichan.retuss.model.uml.*;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.retuss.translator.common.AbstractLanguageTranslator;
import io.github.morichan.retuss.translator.common.CodeToUmlTranslator;
import io.github.morichan.retuss.translator.common.UmlToCodeTranslator;
import io.github.morichan.retuss.translator.cpp.listeners.CppMethodAnalyzer;
import io.github.morichan.retuss.translator.cpp.util.*;
import io.github.morichan.retuss.translator.model.MethodCall;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import java.util.*;

import org.antlr.runtime.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

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
    public String generateSequenceDiagram(String headerCode, String implCode, String methodName) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam style strictuml\n\n");

        try {
            List<Class> classes = translateCodeToUml(headerCode);
            if (!classes.isEmpty()) {
                Class mainClass = classes.get(0);
                String className = mainClass.getName();

                sb.append("participant \"").append(className).append("\"\n\n");
                sb.append("[-> \"").append(className).append("\" : ").append(methodName).append("()\n");
                sb.append("activate \"").append(className).append("\"\n");

                // 実装ファイルの解析
                CharStream input = CharStreams.fromString(implCode);
                CPP14Lexer lexer = new CPP14Lexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                CPP14Parser parser = new CPP14Parser(tokens);

                CppMethodAnalyzer analyzer = new CppMethodAnalyzer(mainClass);
                ParseTreeWalker walker = new ParseTreeWalker();
                walker.walk(analyzer, parser.translationUnit());

                // メソッド呼び出しを追加
                List<MethodCall> calls = analyzer.getMethodCalls();
                for (MethodCall call : calls) {
                    sb.append(call.toString()).append("\n");
                }

                sb.append("[<-- \"").append(className).append("\"\n");
                sb.append("deactivate \"").append(className).append("\"\n");
            }
        } catch (Exception e) {
            System.err.println("Error generating sequence diagram: " + e.getMessage());
            e.printStackTrace();
        }

        sb.append("@enduml\n");
        System.out.println("Generated PlantUML:\n" + sb.toString());
        return sb.toString();
    }

    private String generateSequenceDiagramPlantUML(Interaction interaction) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam style strictuml\n\n");

        // 参加者の収集と出力
        Set<String> participants = collectParticipants(interaction);
        for (String participant : participants) {
            sb.append("participant \"").append(participant).append("\"\n");
        }
        sb.append("\n");

        // メインのシーケンス
        generateInteractionSequence(sb, interaction.getInteractionFragmentList(), 0);

        sb.append("@enduml\n");
        return sb.toString();
    }

    private Set<String> collectParticipants(Interaction interaction) {
        Set<String> participants = new HashSet<>();
        for (InteractionFragment fragment : interaction.getInteractionFragmentList()) {
            if (fragment instanceof OccurenceSpecification) {
                OccurenceSpecification occurence = (OccurenceSpecification) fragment;
                participants.add(occurence.getLifeline().getSignature());
                if (occurence.getMessage() != null) {
                    participants.add(occurence.getMessage().getMessageEnd().getLifeline().getSignature());
                }
            } else if (fragment instanceof CombinedFragment) {
                CombinedFragment combined = (CombinedFragment) fragment;
                participants.add(combined.getLifeline().getSignature());
            }
        }
        return participants;
    }

    private void generateInteractionSequence(
            StringBuilder sb,
            List<InteractionFragment> fragments,
            int indent) {
        String indentation = "  ".repeat(indent);

        for (InteractionFragment fragment : fragments) {
            if (fragment instanceof OccurenceSpecification) {
                generateOccurenceSpecification(sb, (OccurenceSpecification) fragment, indentation);
            } else if (fragment instanceof CombinedFragment) {
                generateCombinedFragment(sb, (CombinedFragment) fragment, indent);
            }
        }
    }

    private void generateOccurenceSpecification(
            StringBuilder sb,
            OccurenceSpecification occurence,
            String indent) {
        Message message = occurence.getMessage();
        if (message != null) {
            sb.append(indent)
                    .append("\"").append(occurence.getLifeline().getSignature()).append("\"")
                    .append(" -> ")
                    .append("\"").append(message.getMessageEnd().getLifeline().getSignature()).append("\"")
                    .append(" : ")
                    .append(message.getName());

            // パラメータの追加
            if (!message.getParameterList().isEmpty()) {
                sb.append("(");
                List<String> params = new ArrayList<>();
                for (Parameter param : message.getParameterList()) {
                    params.add(param.getName().getNameText());
                }
                sb.append(String.join(", ", params));
                sb.append(")");
            } else {
                sb.append("()");
            }
            sb.append("\n");

            // アクティベーションバーの追加
            if (message.getMessageSort() == MessageSort.synchCall) {
                sb.append(indent)
                        .append("activate \"")
                        .append(message.getMessageEnd().getLifeline().getSignature())
                        .append("\"\n");
            }
        }
    }

    private void generateCombinedFragment(
            StringBuilder sb,
            CombinedFragment fragment,
            int indent) {
        String indentation = "  ".repeat(indent);

        // フラグメントの種類に応じた開始部分の生成
        switch (fragment.getKind()) {
            case opt:
                sb.append(indentation).append("opt ");
                if (!fragment.getInteractionOperandList().isEmpty()) {
                    sb.append(fragment.getInteractionOperandList().get(0).getGuard());
                }
                sb.append("\n");
                break;

            case alt:
                boolean isFirst = true;
                for (InteractionOperand operand : fragment.getInteractionOperandList()) {
                    if (isFirst) {
                        sb.append(indentation).append("alt ").append(operand.getGuard()).append("\n");
                        isFirst = false;
                    } else {
                        sb.append(indentation).append("else ");
                        if (!operand.getGuard().equals("else")) {
                            sb.append(operand.getGuard());
                        }
                        sb.append("\n");
                    }
                    generateInteractionSequence(
                            sb,
                            operand.getInteractionFragmentList(),
                            indent + 1);
                }
                break;

            case loop:
                sb.append(indentation).append("loop ");
                if (!fragment.getInteractionOperandList().isEmpty()) {
                    sb.append(fragment.getInteractionOperandList().get(0).getGuard());
                }
                sb.append("\n");
                break;

            case BREAK:
                sb.append(indentation).append("break ");
                if (!fragment.getInteractionOperandList().isEmpty()) {
                    sb.append(fragment.getInteractionOperandList().get(0).getGuard());
                }
                sb.append("\n");
                break;
        }

        // フラグメント内のシーケンスを生成
        for (InteractionOperand operand : fragment.getInteractionOperandList()) {
            generateInteractionSequence(
                    sb,
                    operand.getInteractionFragmentList(),
                    indent + 1);
        }

        // フラグメントの終了
        sb.append(indentation).append("end\n");
    }

    /**
     * 実装ファイルの特定のメソッドを解析
     */
    private void analyzeMethodImplementation(
            Class umlClass,
            String implCode,
            String methodName) {
        try {
            CharStream input = CharStreams.fromString(implCode);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            // コンストラクタに引数を渡す
            CppMethodAnalyzer methodAnalyzer = new CppMethodAnalyzer(umlClass);
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(methodAnalyzer, parser.translationUnit());

        } catch (Exception e) {
            System.err.println("Error analyzing method implementation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<MethodCall> analyzeMethodCalls(String code, String methodName) {
        try {
            CharStream input = CharStreams.fromString(code);
            CPP14Lexer lexer = new CPP14Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CPP14Parser parser = new CPP14Parser(tokens);

            // 空のクラスを作成して渡す（一時的なもの）
            io.github.morichan.retuss.model.uml.Class tempClass = new io.github.morichan.retuss.model.uml.Class(
                    methodName);

            // コンストラクタに引数を渡す
            CppMethodAnalyzer analyzer = new CppMethodAnalyzer(tempClass);
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