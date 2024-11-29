package io.github.morichan.retuss.translator.cpp;

import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.parser.cpp.*;
import io.github.morichan.retuss.translator.common.AbstractLanguageTranslator;
import io.github.morichan.retuss.translator.common.CodeToUmlTranslator;
import io.github.morichan.retuss.translator.common.UmlToCodeTranslator;
import io.github.morichan.retuss.translator.cpp.util.*;
import io.github.morichan.retuss.translator.model.MethodCall;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class CppTranslator {
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;
    protected final CppToUmlTranslator cppToUmlTranslator;
    protected final UmlToCppTranslator umlToCppTranslator;

    public CppTranslator() {
        super();
        this.typeMapper = new CppTypeMapper();
        this.visibilityMapper = new CppVisibilityMapper();
        this.cppToUmlTranslator = createCodeToUmlTranslator();
        this.umlToCppTranslator = createUmlToCodeTranslator();
    }

    public List<CppHeaderClass> translateCodeToUml(String code) {
        return cppToUmlTranslator.translate(code);
    }

    public String translateVisibility(Visibility visibility) {
        return visibilityMapper.toSourceCode(visibility);
    }

    public String translateType(Type type) {
        return typeMapper.mapType(type.toString());
    }

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

    public String translateOperation(Operation operation) {
        StringBuilder sb = new StringBuilder();

        // 戻り値の型と名前
        sb.append(translateType(operation.getReturnType()))
                .append(" ")
                .append(operation.getName())
                .append("(");

        if (!operation.getParameters().isEmpty() && operation.getParameters() != null) {
            // パラメータ
            List<String> params = new ArrayList<>();

            operation.getParameters().forEach(param -> params.add(String.format("%s %s",
                    translateType(param.getType()),
                    param.getName())));
            sb.append(String.join(", ", params));
        }

        sb.append(")");

        return sb.toString();
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

    protected CppToUmlTranslator createCodeToUmlTranslator() {
        return new CppToUmlTranslator();
    }

    protected UmlToCppTranslator createUmlToCodeTranslator() {
        return new UmlToCppTranslator();
    }

    // CppTranslator.java に追加
    public String addAttribute(String existingCode, CppHeaderClass cls, Attribute attribute) {
        return umlToCppTranslator.addAttribute(existingCode, cls, attribute);
    }

    public String addOperation(String existingCode, CppHeaderClass cls, Operation operation) {
        return umlToCppTranslator.addOperation(existingCode, cls, operation);
    }

    public String addInheritance(String existingCode, String derivedClassName, String baseClassName) {
        return umlToCppTranslator.addInheritance(existingCode, derivedClassName, baseClassName);
    }

    public String addRealization(String code, String interfaceName) {
        return umlToCppTranslator.addRealization(code, interfaceName);
    }

    public String removeInheritance(String code, String baseClassName) {
        return umlToCppTranslator.removeInheritance(code, baseClassName);
    }

    public String addComposition(String existingCode, String componentName, String memberName, Visibility visibility) {
        return umlToCppTranslator.addComposition(existingCode, componentName, memberName, visibility);
    }

    public String addCompositionWithAnnotation(String existingCode, String componentName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addCompositionWithAnnotation(existingCode, componentName, memberName, visibility);
    }

    public String addAggregation(String existingCode, String componentName, String memberName, Visibility visibility) {
        return umlToCppTranslator.addAggregation(existingCode, componentName, memberName, visibility);
    }

    public String addAggregationWithAnnotation(String existingCode, String componentName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addAggregationWithAnnotation(existingCode, componentName, memberName, visibility);
    }

    public String addAssociation(String existingCode, String targetClassName, String memberName,
            Visibility visibility) {
        return umlToCppTranslator.addAssociation(existingCode, targetClassName, memberName, visibility);
    }

    public String removeOperation(String existingCode, Operation operation) {
        return umlToCppTranslator.removeOperation(existingCode, operation);
    }

    public String removeAttribute(String existingCode, Attribute attribute) {
        return umlToCppTranslator.removeAttribute(existingCode, attribute);
    }

    // public String generateClassDiagram(List<Class> classes) {
    // StringBuilder sb = new StringBuilder();
    // sb.append("@startuml\n\n");

    // for (Class cls : classes) {
    // if (cls.getAbstruct()) {
    // sb.append("abstract ");
    // }
    // sb.append("class ").append(cls.getName()).append(" {\n");

    // // 属性
    // for (Attribute attr : cls.getAttributeList()) {
    // sb.append(" ")
    // .append(visibilityMapper.toSymbol(attr.getVisibility()))
    // .append(" ")
    // .append(translateAttribute(attr))
    // .append("\n");
    // }

    // // 操作
    // for (Operation op : cls.getOperationList()) {
    // sb.append(" ")
    // .append(visibilityMapper.toSymbol(op.getVisibility()))
    // .append(" ")
    // .append(translateOperation(op))
    // .append("\n");
    // }

    // sb.append("}\n\n");
    // }

    // sb.append("@enduml");
    // return sb.toString();
    // }

    // public String generateSequenceDiagram(String headerCode, String implCode,
    // String methodName) {
    // StringBuilder sb = new StringBuilder();
    // sb.append("@startuml\n");
    // sb.append("skinparam style strictuml\n\n");

    // try {
    // List<Class> classes = translateCodeToUml(headerCode);
    // if (!classes.isEmpty()) {
    // Class mainClass = classes.get(0);
    // CppMethodAnalyzer analyzer = new CppMethodAnalyzer(mainClass);

    // // 実装コードの解析
    // CharStream input = CharStreams.fromString(implCode);
    // CPP14Lexer lexer = new CPP14Lexer(input);
    // CommonTokenStream tokens = new CommonTokenStream(lexer);
    // CPP14Parser parser = new CPP14Parser(tokens);
    // ParseTreeWalker walker = new ParseTreeWalker();
    // walker.walk(analyzer, parser.translationUnit());

    // // シーケンス図のヘッダー
    // sb.append("title ").append(mainClass.getName())
    // .append("::").append(methodName).append("\n\n");

    // // 参加者の定義（改行を入れて整理）
    // sb.append("participant \"").append(mainClass.getName())
    // .append("\" as Main\n");

    // // 標準ライブラリの参加者を追加
    // for (StandardLifeline lifeline : analyzer.getUsedLifelines()) {
    // sb.append("participant ")
    // .append(lifeline.getDisplayName())
    // .append(" as ")
    // .append(lifeline.getIdentifier())
    // .append("\n");
    // }
    // sb.append("\n");

    // // メソッド開始
    // sb.append("[-> Main : ").append(methodName).append("()\n");
    // sb.append("activate Main\n\n");

    // // メソッド呼び出し
    // for (MethodCall call : analyzer.getMethodCalls()) {
    // String caller = "Main";
    // String callee = call.getCallee();

    // sb.append("Main -> \"")
    // .append(callee)
    // .append("\" : ");

    // if (call.getMethodName().equals("output") ||
    // call.getMethodName().equals("input")) {
    // sb.append(call.getArguments().get(0));
    // } else {
    // sb.append(call.getMethodName())
    // .append("(")
    // .append(String.join(", ", call.getArguments()))
    // .append(")");
    // }
    // sb.append("\n");

    // // アクティベーション
    // if (!caller.equals(callee)) {
    // sb.append("activate \"").append(callee).append("\"\n");
    // sb.append("\"").append(callee).append("\" --> Main\n");
    // sb.append("deactivate \"").append(callee).append("\"\n");
    // }
    // }

    // // メソッド終了
    // sb.append("\n[<-- Main\n");
    // sb.append("deactivate Main\n");
    // }
    // } catch (Exception e) {
    // System.err.println("Error generating sequence diagram: " + e.getMessage());
    // e.printStackTrace();
    // }

    // sb.append("@enduml");

    // // デバッグ出力
    // System.out.println("Generated PlantUML:\n" + sb.toString());

    // return sb.toString();
    // }

    // private void generateInteractionSequence(
    // StringBuilder sb,
    // List<InteractionFragment> fragments,
    // int indent) {
    // String indentation = " ".repeat(indent);

    // for (InteractionFragment fragment : fragments) {
    // if (fragment instanceof OccurenceSpecification) {
    // generateOccurenceSpecification(sb, (OccurenceSpecification) fragment,
    // indentation);
    // } else if (fragment instanceof CombinedFragment) {
    // generateCombinedFragment(sb, (CombinedFragment) fragment, indent);
    // }
    // }
    // }

    // private void generateOccurenceSpecification(
    // StringBuilder sb,
    // OccurenceSpecification occurence,
    // String indent) {
    // Message message = occurence.getMessage();
    // if (message != null) {
    // sb.append(indent)
    // .append("\"").append(occurence.getLifeline().getSignature()).append("\"")
    // .append(" -> ")
    // .append("\"").append(message.getMessageEnd().getLifeline().getSignature()).append("\"")
    // .append(" : ")
    // .append(message.getName());

    // // パラメータの追加
    // if (!message.getParameterList().isEmpty()) {
    // sb.append("(");
    // List<String> params = new ArrayList<>();
    // for (Parameter param : message.getParameterList()) {
    // params.add(param.getName().getNameText());
    // }
    // sb.append(String.join(", ", params));
    // sb.append(")");
    // } else {
    // sb.append("()");
    // }
    // sb.append("\n");

    // // アクティベーションバーの追加
    // if (message.getMessageSort() == MessageSort.synchCall) {
    // sb.append(indent)
    // .append("activate \"")
    // .append(message.getMessageEnd().getLifeline().getSignature())
    // .append("\"\n");
    // }
    // }
    // }

    // private void generateCombinedFragment(
    // StringBuilder sb,
    // CombinedFragment fragment,
    // int indent) {
    // String indentation = " ".repeat(indent);

    // // フラグメントの種類に応じた開始部分の生成
    // switch (fragment.getKind()) {
    // case opt:
    // sb.append(indentation).append("opt ");
    // if (!fragment.getInteractionOperandList().isEmpty()) {
    // sb.append(fragment.getInteractionOperandList().get(0).getGuard());
    // }
    // sb.append("\n");
    // break;

    // case alt:
    // boolean isFirst = true;
    // for (InteractionOperand operand : fragment.getInteractionOperandList()) {
    // if (isFirst) {
    // sb.append(indentation).append("alt
    // ").append(operand.getGuard()).append("\n");
    // isFirst = false;
    // } else {
    // sb.append(indentation).append("else ");
    // if (!operand.getGuard().equals("else")) {
    // sb.append(operand.getGuard());
    // }
    // sb.append("\n");
    // }
    // generateInteractionSequence(
    // sb,
    // operand.getInteractionFragmentList(),
    // indent + 1);
    // }
    // break;

    // case loop:
    // sb.append(indentation).append("loop ");
    // if (!fragment.getInteractionOperandList().isEmpty()) {
    // sb.append(fragment.getInteractionOperandList().get(0).getGuard());
    // }
    // sb.append("\n");
    // break;

    // case BREAK:
    // sb.append(indentation).append("break ");
    // if (!fragment.getInteractionOperandList().isEmpty()) {
    // sb.append(fragment.getInteractionOperandList().get(0).getGuard());
    // }
    // sb.append("\n");
    // break;
    // }

    // // フラグメント内のシーケンスを生成
    // for (InteractionOperand operand : fragment.getInteractionOperandList()) {
    // generateInteractionSequence(
    // sb,
    // operand.getInteractionFragmentList(),
    // indent + 1);
    // }

    // // フラグメントの終了
    // sb.append(indentation).append("end\n");
    // }

    // public List<MethodCall> analyzeMethodCalls(String code, String methodName) {
    // try {
    // CharStream input = CharStreams.fromString(code);
    // CPP14Lexer lexer = new CPP14Lexer(input);
    // CommonTokenStream tokens = new CommonTokenStream(lexer);
    // CPP14Parser parser = new CPP14Parser(tokens);

    // // 空のクラスを作成して渡す（一時的なもの）
    // io.github.morichan.retuss.model.uml.Class tempClass = new
    // io.github.morichan.retuss.model.uml.Class(
    // methodName);

    // // コンストラクタに引数を渡す
    // CppMethodAnalyzer analyzer = new CppMethodAnalyzer(tempClass);
    // ParseTreeWalker walker = new ParseTreeWalker();
    // walker.walk(analyzer, parser.translationUnit());

    // return analyzer.getMethodCalls();
    // } catch (Exception e) {
    // System.err.println("Failed to analyze method calls: " + e.getMessage());
    // return new ArrayList<>();
    // }
    // }
}