package io.github.morichan.retuss.translator;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.multiplicity.MultiplicityRange;
import io.github.morichan.fescue.feature.multiplicity.Bounder;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.property.Property;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.language.Model;
import io.github.morichan.retuss.language.java.*;
import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.language.uml.Package;

import io.github.morichan.retuss.language.cpp.Cpp;
import io.github.morichan.retuss.language.cpp.MemberVariable;
import io.github.morichan.retuss.language.cpp.MemberFunction;
import io.github.morichan.retuss.language.cpp.AccessSpecifier;
import io.github.morichan.retuss.window.diagram.AttributeGraphic;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import io.github.morichan.retuss.window.diagram.sequence.Interaction;
import io.github.morichan.retuss.window.diagram.sequence.InteractionFragment;
import io.github.morichan.retuss.window.diagram.sequence.Lifeline;
import io.github.morichan.retuss.window.diagram.sequence.MessageOccurrenceSpecification;
import io.github.morichan.retuss.window.diagram.sequence.MessageType;
import io.github.morichan.retuss.window.diagram.sequence.CombinedFragment;
import io.github.morichan.retuss.window.diagram.sequence.InteractionOperandKind;
import io.github.morichan.retuss.window.diagram.sequence.InteractionOperand;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * <p>
 * UML翻訳者クラス
 * </p>
 */
public class UMLTranslator {
    private Model model;
    private Package classPackage;

    public UMLTranslator(Model model) {
        this.model = model;
    }

    /**
     * <p>
     * Javaからクラス図のパッケージに翻訳します
     * </p>
     *
     * @param java Javaソースコード
     * @return クラス図のパッケージ
     */
    public Package translate(Java java) {
        // 変換時間の計測
//        long time1 = System.nanoTime();

        classPackage = new Package();

        for (io.github.morichan.retuss.language.java.Class javaClass : java.getClasses()) {
            classPackage.addClass(createUmlClass(javaClass));
        }

        searchGeneralizationClass(java.getClasses());
        searchMethod(classPackage);

//        long time2 = System.nanoTime();
//        System.out.printf("trans:%d\n", time2 - time1);

        return classPackage;
    }

    /**
     * <p>
     * Cppからクラス図のパッケージに翻訳します
     * </p>
     *
     * @param cpp C++ソースコード
     * @return クラス図のパッケージ
     */
    public Package translate(Cpp cpp) {
        // List<Boolean> flagOperationImplementations =
        // classPackage.getClasses().get(0).getFlagOperationsImplementations();
        classPackage = new Package();

        for (io.github.morichan.retuss.language.cpp.Class cppClass : cpp.getClasses()) {
            classPackage.addClass(createClass(cppClass));
        }

        searchGeneralizationClass_Cpp(cpp.getClasses());

        return classPackage;
    }

    private Class createUmlClass(io.github.morichan.retuss.language.java.Class javaClass) {
        Class umlClass = new Class(javaClass.getName());

        // クラス図関係
        for (Field field : javaClass.getFields()) {
            Attribute attribute = new Attribute(new Name(field.getName()));
            attribute.setType(new Type(field.getType().toString()));
            attribute.setVisibility(convert(field.getAccessModifier()));
            if (field.getValue() != null) {
                if (field.getArrayLength() != null) {
                    attribute.setMultiplicityRange(
                            new MultiplicityRange(new Bounder(new OneIdentifier(field.getArrayLength().getLength()))));
                } else {
                    attribute.setDefaultValue(new DefaultValue(new OneIdentifier(field.getValue().toString())));
                }
            }
            umlClass.addAttribute(attribute);
        }

        // シーケンス図関係
        Lifeline lifeline = new Lifeline(umlClass);
        for (Method method : javaClass.getMethods()) {
            Operation operation = new Operation(new Name(method.getName()));
            operation.setReturnType(new Type(method.getType().toString()));
            operation.setVisibility(convert(method.getAccessModifier()));
            for (Argument argument : method.getArguments()) {
                Parameter parameter = new Parameter(new Name(argument.getName()));
                parameter.setType(new Type(argument.getType().toString()));
                operation.addParameter(parameter);
            }
            OperationGraphic operationGraphic = new OperationGraphic(operation);
            MessageOccurrenceSpecification message = new MessageOccurrenceSpecification();
            message.setLifeline(lifeline);
            message.setType(umlClass);
            message.setName(createMessageText(operation));
            message.setMessageType(MessageType.Method);

            if (method.getMethodBody() != null) {
                for (BlockStatement statement : method.getMethodBody().getStatements()) {
                    // if、while、forがあったらCombinedFragmentを作成する
                    if (statement instanceof If || statement instanceof While || statement instanceof For) {
                        CombinedFragment combinedFragment = createCombinedFragment(statement, message, lifeline);
                        message.addInteractionFragment(combinedFragment);
                    } else {
                        InteractionFragment interactionFragment = new InteractionFragment();
                        interactionFragment.setMessage(convert(message, lifeline, statement));
                        message.addInteractionFragment(interactionFragment);
                    }
                }
            }

            operationGraphic.setInteraction(new Interaction(message));
            umlClass.addOperation(operationGraphic, method.isAbstract());
        }

        return umlClass;
    }

    private CombinedFragment createCombinedFragment(BlockStatement statement, MessageOccurrenceSpecification message,
            Lifeline lifeline) {

        InteractionOperandKind kind = InteractionOperandKind.undefined;
        CombinedFragment combinedFragment = new CombinedFragment(kind);

        if(statement instanceof If) {
            If ifClass = (If) statement;

            if (ifClass.getElseStatements().size() > 0) {
                kind = InteractionOperandKind.alt;
            } else {
                kind = InteractionOperandKind.opt;
            }

            combinedFragment = new CombinedFragment(kind);
            combinedFragment.setInteractionOperandList(convertIftoInteractionOperand(ifClass, message, lifeline));

        } else if (statement instanceof While) {
            While whileClass = (While) statement;
            kind = InteractionOperandKind.loop;
            InteractionOperand interactionOperand = new InteractionOperand(whileClass.getCondition());

            for (BlockStatement blockStatement : whileClass.getStatements()) {
                InteractionFragment interactionFragment = new InteractionFragment();
                if (blockStatement instanceof Assignment) {
                    interactionFragment.setMessage(convertAssignment(message, lifeline, blockStatement, interactionOperand));
                } else {
                    interactionFragment.setMessage(convert(message, lifeline, blockStatement));
                }
                interactionOperand.addInteractionFragment(interactionFragment);
            }
            combinedFragment = new CombinedFragment(kind);
            combinedFragment.addInteractionOperand(interactionOperand);
        } else if (statement instanceof For) {
            For forClass = (For) statement;
            kind = InteractionOperandKind.loop;
            combinedFragment = new CombinedFragment(kind);

            InteractionOperand interactionOperand = new InteractionOperand("");
            combinedFragment.setCodeText(forClass.getForInit() + ";" + forClass.getExpression() + ";" + forClass.getForUpdate());
            if (!forClass.getNumLoop().isEmpty()) {
                combinedFragment.setTextNextToKind(forClass.getNumLoop());
            } else {
                interactionOperand = new InteractionOperand(forClass.getExpression());
            }

            for (BlockStatement blockStatement : forClass.getStatements()) {
                InteractionFragment interactionFragment = new InteractionFragment();
                if (blockStatement instanceof Assignment) {
                    interactionFragment.setMessage(convertAssignment(message, lifeline, blockStatement, interactionOperand));
                } else {
                    interactionFragment.setMessage(convert(message, lifeline, blockStatement));
                }
                interactionOperand.addInteractionFragment(interactionFragment);
            }
            combinedFragment.addInteractionOperand(interactionOperand);
        }

        return combinedFragment;

    }

    private ArrayList<InteractionOperand> convertIftoInteractionOperand(If ifClass, MessageOccurrenceSpecification message,
                                                                        Lifeline lifeline) {
        ArrayList<InteractionOperand> interactionOperandList = new ArrayList<InteractionOperand>();
        InteractionOperand interactionOperand = new InteractionOperand(ifClass.getCondition());

        for (BlockStatement blockStatement : ifClass.getStatements()) {
            InteractionFragment interactionFragment = new InteractionFragment();
            if (blockStatement instanceof Assignment) {
                interactionFragment.setMessage(convertAssignment(message, lifeline, blockStatement, interactionOperand));
            } else {
                interactionFragment.setMessage(convert(message, lifeline, blockStatement));
            }
            interactionOperand.addInteractionFragment(interactionFragment);
        }
        interactionOperandList.add(interactionOperand);

        if (ifClass.getHasElse()) {
            if (ifClass.getElseStatements().size() > 0 && ifClass.getElseStatements().get(0) instanceof If) {
                interactionOperandList.addAll(convertIftoInteractionOperand((If) ifClass.getElseStatements().get(0), message, lifeline));
            } else {
                InteractionOperand elseInteractionOperand = new InteractionOperand("else");
                for (BlockStatement blockStatement : ifClass.getElseStatements()) {
                    InteractionFragment interactionFragment = new InteractionFragment();
                    if (blockStatement instanceof Assignment) {
                        interactionFragment.setMessage(convertAssignment(message, lifeline, blockStatement, interactionOperand));
                    } else {
                        interactionFragment.setMessage(convert(message, lifeline, blockStatement));
                    }
                    elseInteractionOperand.addInteractionFragment(interactionFragment);
                }
                interactionOperandList.add(elseInteractionOperand);
            }
        }

        return interactionOperandList;
    }

    private String createMessageText(Operation operation) {
        StringBuilder sb = new StringBuilder();

        sb.append(operation.getName());

        try {
            if (operation.getParameters().size() > 0) {
                StringJoiner sj = new StringJoiner(", ");
                for (Parameter param : operation.getParameters())
                    sj.add(param.toString());

                sb.append("(");
                sb.append(sj);
                sb.append(")");
            } else {
                sb.append("()");
            }
        } catch (IllegalStateException e) {
            sb.append("()");
        }

        sb.append(" : ");
        sb.append(operation.getReturnType());

        return sb.toString();
    }

    private void searchMethod(Package umlPackage) {
        for (Class umlClass : umlPackage.getClasses()) {
//        for (Class umlClass : model.getUml().getClasses()) {
            for (OperationGraphic og : umlClass.getOperationGraphics()) {
                searchInteractionFragment(umlPackage, umlClass, og, og.getInteraction().getMessage().getInteractionFragmentList());
            }
        }
    }

    private void searchInteractionFragment(Package umlPackage, Class umlClass, OperationGraphic og, List<InteractionFragment> interactionFragmentList){
        toSearchNextInteractionFragment: for(int i = 0; i < interactionFragmentList.size(); i++) {
            InteractionFragment interactionFragment = interactionFragmentList.get(i);
            if (interactionFragment instanceof CombinedFragment) {
                // 複合フラグメント
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                for (InteractionOperand io : cf.getInteractionOperandList()) {
                    searchInteractionFragment(umlPackage, umlClass, og, io.getInteractionFragmentList());
                }
            } else {
                // メッセージ
                MessageOccurrenceSpecification message = interactionFragment.getMessage();
                if (message.getMessageType() != MessageType.Method)
                    continue;

                if (umlClass.getName().equals(
                        searchClass(message.getType().getName(), message, message.getLifeline()).getName())) {
                    // 自クラスのメソッド呼び出し
                    for (Class sourceUmlClass : umlPackage.getClasses()) {
                        if (!umlClass.getName().equals(sourceUmlClass.getName()))
                            continue;
                        for (OperationGraphic sourceOg : sourceUmlClass.getOperationGraphics()) {
                            MessageOccurrenceSpecification sourceMessage = sourceOg.getInteraction().getMessage();

                            if (sourceMessage.getName().contains(message.getName() + "(")) {
                                // sourceMessageのinteractionFragmentListをメソッド呼び出しのmessageのinteractionFragmentListに代入する
                                InteractionFragment newInteractionFragment = new InteractionFragment();
                                MessageOccurrenceSpecification callMethodMessage = interactionFragmentList.get(i).getMessage();
                                callMethodMessage.setInteractionFragmentList(sourceMessage.getInteractionFragmentList());
                                newInteractionFragment.setMessage(callMethodMessage);
                                interactionFragmentList.set(i, newInteractionFragment);
                                continue toSearchNextInteractionFragment;
                            }
                        }
                    }

                } else {
                    // 他クラスのメソッド呼び出し
                    for (Class sourceUmlClass : model.getUml().getClasses()) {
                        if (umlClass.getName().equals(sourceUmlClass.getName()))
                            continue;
                        for (OperationGraphic sourceOg : sourceUmlClass.getOperationGraphics()) {
                            MessageOccurrenceSpecification sourceMessage = sourceOg.getInteraction().getMessage();

                            if (sourceMessage.getName().contains(message.getName() + "(")) {
                                String instance = "";

                                for (AttributeGraphic ag : umlClass.getAttributeGraphics()) {
                                    if (sourceUmlClass.getName().equals(ag.getAttribute().getType().getName().getNameText())) {
                                        instance = ag.getAttribute().getName().getNameText();
                                        break;
                                    }
                                }

                                InteractionFragment newInteractionFragment = new InteractionFragment();
                                MessageOccurrenceSpecification callMethodMessage = interactionFragmentList.get(i).getMessage();
                                callMethodMessage.setInteractionFragmentList(sourceMessage.getInteractionFragmentList());
                                // lifelineをsourceMessageと同じにしないとシーケンス図上で別オブジェクトのメソッド呼び出しとして描画されない
                                callMethodMessage.setLifeline(sourceMessage.getLifeline());
                                newInteractionFragment.setMessage(callMethodMessage);
                                interactionFragmentList.set(i, newInteractionFragment);
                                continue toSearchNextInteractionFragment;
                            }
                        }
                    }
                }
            }
        }
    }

    private Visibility convert(AccessModifier accessModifier) {
        if (accessModifier == AccessModifier.Public) {
            return Visibility.Public;
        } else if (accessModifier == AccessModifier.Protected) {
            return Visibility.Protected;
        } else if (accessModifier == AccessModifier.Package) {
            return Visibility.Package;
        } else {
            return Visibility.Private;
        }
    }

    /**
     * <p>
     * 汎化関係のクラスをディープコピーします
     * </p>
     *
     * <p>
     * 手法について詳しくは {@link JavaTranslator#searchGeneralizationClass(List)} を参照してください。
     * </p>
     *
     * @param javaClasses Javaのクラスリスト
     */
    private void searchGeneralizationClass(List<io.github.morichan.retuss.language.java.Class> javaClasses) {
        for (int i = 0; i < javaClasses.size(); i++) {
            if (javaClasses.get(i).getExtendsClassName() != null) {
                int finalI = i;
                List<io.github.morichan.retuss.language.uml.Class> oneExtendsClass = classPackage.getClasses().stream()
                        .filter(cp -> cp.getName().equals(javaClasses.get(finalI).getExtendsClassName()))
                        .collect(Collectors.toList());
                try {
                    io.github.morichan.retuss.language.uml.Class oneClass = oneExtendsClass.get(0);
                    classPackage.getClasses().get(finalI).setGeneralizationClass(oneClass);
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("This is Set Error because same class wasn't had, so don't set.");
                }
            }
        }
    }

    // C++の変換

    private Class createClass(io.github.morichan.retuss.language.cpp.Class cppClass) {
        Class classClass = new Class(cppClass.getName());

        for (MemberVariable memberVariable : cppClass.getMemberVariables()) {

            Attribute attribute = new Attribute(new Name(memberVariable.getName()));
            if (memberVariable.getConstantExpression() != null) {
                MultiplicityRange multiplicityRange = new MultiplicityRange(
                        new Bounder(memberVariable.getConstantExpression()));
                attribute.setMultiplicityRange(multiplicityRange);
            }
            if (memberVariable.getType().toString().equals("string")) {
                attribute.setType(new Type("String"));
            } else {
                attribute.setType(new Type(memberVariable.getType().toString()));
            }
            attribute.setVisibility(convert(memberVariable.getAccessSpecifier()));
            if (memberVariable.getValue() != null) {
                attribute.setDefaultValue(new DefaultValue(new OneIdentifier(memberVariable.getValue().toString())));
            }

            classClass.addAttribute(attribute);
        }
        for (MemberFunction memberFunction : cppClass.getMemberFunctions()) {
            Operation operation = new Operation(new Name(memberFunction.getName()));
            operation.setReturnType(new Type(memberFunction.getType().toString()));
            operation.setVisibility(convert(memberFunction.getAccessSpecifier()));
            for (io.github.morichan.retuss.language.cpp.Argument argument : memberFunction.getArguments()) {
                Parameter parameter = new Parameter(new Name(argument.getName()));
                parameter.setType(new Type(argument.getType().toString()));
                operation.addParameter(parameter);
            }
            // Boolean flagOperationsImplementation =
            // memberFunction.getFlagImplementation();
            // Boolean flagOperationsImplementation =
            // Boolean.valueOf(memberFunction.getFlagImplementation());
            // classClass.addFlagOperationsImplementation(flagOperationsImplementation);
            OperationGraphic operationGraphic = new OperationGraphic(operation);
            classClass.addOperation(operationGraphic, memberFunction.isAbstract());
        }

        return classClass;
    }

    private Visibility convert(AccessSpecifier accessSpecifier) {
        if (accessSpecifier == AccessSpecifier.Public) {
            return Visibility.Public;
        } else if (accessSpecifier == AccessSpecifier.Protected) {
            return Visibility.Protected;
        }
        // else if (accessSpecifier == AccessSpecifier.Package) {
        // return Visibility.Package;
        // }
        else {
            return Visibility.Private;
        }
    }

    private MessageOccurrenceSpecification convertAssignment(MessageOccurrenceSpecification owner, Lifeline lifeline,
                                                   BlockStatement statement, InteractionOperand interactionOperand) {
        MessageOccurrenceSpecification message = new MessageOccurrenceSpecification();

        message.setMessageType(MessageType.Assignment);
        message.setType(searchPreviousDeclaredFieldType(statement, owner, lifeline, interactionOperand));
        message.setName(statement.getName());
        message.setValue(((Assignment) statement).getValue().toString());
        message.setLifeline(lifeline);

        return message;
    }

    private MessageOccurrenceSpecification convert(MessageOccurrenceSpecification owner, Lifeline lifeline,
            BlockStatement statement) {
        MessageOccurrenceSpecification message = new MessageOccurrenceSpecification();

        if (statement instanceof LocalVariableDeclaration) {
            message.setMessageType(MessageType.Declaration);
            message.setType(new Class(statement.getType().toString()));
            message.setName(statement.getName());
            if (((LocalVariableDeclaration) statement).getValue() != null)
                message.setValue(((LocalVariableDeclaration) statement).getValue().toString());
            message.setLifeline(lifeline);

        } else if (statement instanceof Assignment) {
            message.setMessageType(MessageType.Assignment);
            message.setType(searchPreviousDeclaredFieldType(statement, owner, lifeline));
            message.setName(statement.getName());
            message.setValue(((Assignment) statement).getValue().toString());
            message.setLifeline(lifeline);

        } else if (statement instanceof Method) {
            Method method = (Method) statement;
            message.setMessageType(MessageType.Method);
            message.setType(new Class(statement.getType().toString()));
            message.setName(statement.getName());
            message.setLifeline(lifeline);
            if (method.getArguments().size() > 0){
                // 引数をmessage.argumentに設定する ex)"1,2,4"
                // method.getTypeはTmpType
                for (Argument argument : method.getArguments()){
                    message.addArgument(argument.getType().toString(), argument.getName());
                }
            }
        }

        return message;
    }

    /**
     * instanceのクラスを特定する
     * @param instance
     * @param owner
     * @param lifeline
     * @return
     */
    private Class searchClass(String instance, MessageOccurrenceSpecification owner, Lifeline lifeline) {
        Class sourceClass = searchPreviousDeclaredFieldType(instance, owner, lifeline);

        // 変換対象のUML情報だけではなく、すべてのUML情報を探索する
        for (Class umlClass : model.getUml().getClasses()) {
            if (umlClass.getName().equals(sourceClass.getName())) {
                return umlClass;
            }
        }

        return lifeline.getUmlClass();
    }

    private Class searchPreviousDeclaredFieldType(BlockStatement statement, MessageOccurrenceSpecification owner,
                                                  Lifeline lifeline) {

        for (MessageOccurrenceSpecification message : owner.getMessages())
            if (message.getName().equals(statement.getName()))
                return message.getType();

        for (AttributeGraphic ag : lifeline.getUmlClass().getAttributeGraphics())
            if (ag.getAttribute().getName().getNameText().equals(statement.getName()))
                return new Class(ag.getAttribute().getType().getName().getNameText());

        return new Class("NotKnownType");
    }

    private Class searchPreviousDeclaredFieldType(BlockStatement statement, MessageOccurrenceSpecification owner,
            Lifeline lifeline, InteractionOperand interactionOperand) {

        // 複合フラグメント内にある変数宣言を探す
        for (InteractionFragment interactionFragment : interactionOperand.getInteractionFragmentList()) {
            if (interactionFragment.getMessage().getName().equals(statement.getName())) {
                return interactionFragment.getMessage().getType();
            }
        }

        for (MessageOccurrenceSpecification message : owner.getMessages())
            if (message.getName().equals(statement.getName()))
                return message.getType();

        for (AttributeGraphic ag : lifeline.getUmlClass().getAttributeGraphics())
            if (ag.getAttribute().getName().getNameText().equals(statement.getName()))
                return new Class(ag.getAttribute().getType().getName().getNameText());

        return new Class("NotKnownType");
    }

    private Class searchPreviousDeclaredFieldType(String instance, MessageOccurrenceSpecification owner,
            Lifeline lifeline) {
        for (MessageOccurrenceSpecification message : owner.getMessages())
            if (message.getName().equals(instance))
                return message.getType();

        for (AttributeGraphic ag : lifeline.getUmlClass().getAttributeGraphics())
            if (ag.getAttribute().getName().getNameText().equals(instance))
                return new Class(ag.getAttribute().getType().getName().getNameText());

        return new Class("NotKnownType");
    }

    /**
     * <p>
     * 汎化関係のクラスをディープコピーします
     * </p>
     *
     * <p>
     * 手法について詳しくは {@link CppTranslator#searchGeneralizationClass(List)} を参照してください。
     * </p>
     *
     * @param cppClasses Cppのクラスリスト
     */
    private void searchGeneralizationClass_Cpp(List<io.github.morichan.retuss.language.cpp.Class> cppClasses) {
        for (int i = 0; i < cppClasses.size(); i++) {
            if (cppClasses.get(i).getExtendsClassName() != null) {
                int finalI = i;
                List<io.github.morichan.retuss.language.uml.Class> oneExtendsClass = classPackage.getClasses().stream()
                        .filter(cp -> cp.getName().equals(cppClasses.get(finalI).getExtendsClassName()))
                        .collect(Collectors.toList());
                try {
                    io.github.morichan.retuss.language.uml.Class oneClass = oneExtendsClass.get(0);
                    classPackage.getClasses().get(finalI).setGeneralizationClass(oneClass);
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("This is Set Error because same class wasn't had, so don't set.");
                }
            }
        }
    }
}
