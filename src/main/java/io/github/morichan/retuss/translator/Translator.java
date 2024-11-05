package io.github.morichan.retuss.translator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.VoidType;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.JavaModel;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Translator {
    /**
     * <p>
     * ソースコードのASTをUMLデータに変換する。
     * </p>
     */
    public List<Class> translateCodeToUml(CompilationUnit compilationUnit) {

        List<Class> umlClassList = new ArrayList<>();

        List<ClassOrInterfaceDeclaration> classList = compilationUnit.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration codeClass : classList) {
            // クラスの生成
            Class umlClass = new Class(codeClass.getNameAsString());
            umlClass.setAbstruct(codeClass.isAbstract());

            // 属性の生成 (汎化関係は属性を元に判断するが、特別な処理はしない)
            List<FieldDeclaration> fieldDeclarationList = codeClass.findAll(FieldDeclaration.class);
            for (FieldDeclaration fieldDeclaration : fieldDeclarationList) {
                for (VariableDeclarator variableDeclarator : fieldDeclaration.getVariables()) {
                    Attribute attribute = new Attribute(new Name(variableDeclarator.getNameAsString()));
                    attribute.setVisibility(toVisibility(fieldDeclaration.getModifiers()));
                    attribute.setType(new Type(variableDeclarator.getTypeAsString()));
                    // 初期値を持っていたら、初期値を文字列として取得する
                    if (variableDeclarator.getInitializer().isPresent()) {
                        attribute.setDefaultValue(new DefaultValue(
                                new OneIdentifier(variableDeclarator.getInitializer().get().toString())));
                    }
                    umlClass.addAttribute(attribute);
                }
            }

            // 操作の生成
            List<MethodDeclaration> methodDeclarationList = codeClass.findAll(MethodDeclaration.class);
            for (MethodDeclaration methodDeclaration : methodDeclarationList) {
                Operation operation = new Operation(new Name(methodDeclaration.getNameAsString()));
                operation.setVisibility(toVisibility(methodDeclaration.getModifiers()));
                operation.setReturnType(new Type(methodDeclaration.getType().asString()));
                for (Parameter codeParameter : methodDeclaration.getParameters()) {
                    io.github.morichan.fescue.feature.parameter.Parameter umlParameter = new io.github.morichan.fescue.feature.parameter.Parameter(
                            new Name(codeParameter.getNameAsString()));
                    umlParameter.setType(new Type(codeParameter.getTypeAsString()));
                    operation.addParameter(umlParameter);
                }
                // シーケンス図のInteractionの作成
                Interaction interaction = new Interaction(operation, operation.toString());
                NodeList statements = methodDeclaration.getBody().get().getStatements();
                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = (Statement) statements.get(i);
                    Optional<InteractionFragment> interactionFragmentOptional = toInteractionFragment(umlClass,
                            statement);
                    if (interactionFragmentOptional.isPresent()) {
                        interaction.getInteractionFragmentList().add(interactionFragmentOptional.get());
                    }
                }

                umlClass.addOperation(operation, interaction);
            }

            // 汎化関係
            if (codeClass.getExtendedTypes().getFirst().isPresent()) {
                String superClassName = codeClass.getExtendedTypes().getFirst().get().getNameAsString();
                // model上で親クラスが見つからない場合は、仮のクラスを作成する
                // 同じファイルに親クラスが宣言されている場合は、考慮しない
                umlClass.setSuperClass(
                        JavaModel.getInstance().findClass(superClassName).orElse(new Class(superClassName, false)));
            }

            umlClassList.add(umlClass);
        }

        return umlClassList;
    }

    /**
     * <p>
     * UMLデータをソースコードのASTに変換する。
     * </p>
     */
    public CompilationUnit translateUmlToCode(List<Class> classList) {
        System.out.println("\n=== Starting UML to Code Translation ===");
        CompilationUnit newCU = new CompilationUnit();

        System.out.println("Number of classes to process: " + classList.size());
        for (Class umlClass : classList) {
            System.out.println("\nProcessing class: " + umlClass.getName());

            // クラス宣言の作成
            ClassOrInterfaceDeclaration classDeclaration = newCU.addClass(umlClass.getName());

            // 操作の追加
            List<Operation> operations = umlClass.getOperationList();
            System.out.println("Number of operations: " + operations.size());

            for (Operation operation : operations) {
                System.out.println("\nProcessing operation: " + operation.toString());
                MethodDeclaration methodDecl = translateOperation(operation);

                // メソッド本体の生成
                BlockStmt body = new BlockStmt();

                // Interactionからメソッド本体を生成
                Optional<Interaction> interactionOpt = umlClass.findInteraction(operation);
                System.out.println("Found interaction: " + interactionOpt.isPresent());

                if (interactionOpt.isPresent()) {
                    Interaction interaction = interactionOpt.get();
                    List<InteractionFragment> fragments = interaction.getInteractionFragmentList();
                    System.out.println("Number of interaction fragments: " + fragments.size());

                    for (InteractionFragment fragment : fragments) {
                        System.out.println("\nProcessing fragment type: " + fragment.getClass().getSimpleName());

                        if (fragment instanceof OccurenceSpecification) {
                            OccurenceSpecification occurence = (OccurenceSpecification) fragment;
                            Message message = occurence.getMessage();
                            System.out.println("Message type: " + message.getMessageSort());
                            System.out.println("Message name: " + message.getName());

                            try {
                                if (message.getMessageSort() == MessageSort.synchCall) {
                                    MethodCallExpr methodCall = new MethodCallExpr();
                                    methodCall.setName(message.getName());

                                    System.out.println("Parameters: " + message.getParameterList().size());
                                    NodeList<Expression> arguments = new NodeList<>();
                                    for (io.github.morichan.fescue.feature.parameter.Parameter param : message
                                            .getParameterList()) {
                                        System.out.println("Adding parameter: " + param.getName().getNameText());
                                        arguments.add(new NameExpr(param.getName().getNameText()));
                                    }
                                    methodCall.setArguments(arguments);

                                    body.addStatement(new ExpressionStmt(methodCall));
                                    System.out.println("Added method call statement");
                                }
                            } catch (Exception e) {
                                System.out.println("Error processing message: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }

                methodDecl.setBody(body);
                classDeclaration.addMember(methodDecl);
                System.out.println("Added method to class declaration");
            }

            // 継承関係の設定
            if (umlClass.getSuperClass().isPresent()) {
                System.out.println("Adding superclass: " + umlClass.getSuperClass().get().getName());
                ClassOrInterfaceType superClassType = new ClassOrInterfaceType();
                superClassType.setName(umlClass.getSuperClass().get().getName());
                classDeclaration.addExtendedType(superClassType);
            }
        }

        System.out.println("\n=== Completed UML to Code Translation ===");
        return newCU;
    }

    public FieldDeclaration translateAttribute(Attribute attribute) {
        NodeList<Modifier> modifiers = new NodeList<>();
        toModifierKeyword(attribute.getVisibility())
                .ifPresent(modifierKeyword -> modifiers.add(new Modifier(modifierKeyword)));

        com.github.javaparser.ast.type.Type type = toJavaType(attribute.getType());
        String name = attribute.getName().getNameText();
        // 初期値を持つFieldDeclarationの作り方がわからず、現状は初期値も変数名が持っている
        try {
            name += " = " + attribute.getDefaultValue();
        } catch (IllegalStateException e) {

        }
        return new FieldDeclaration(modifiers, type, name);
    }

    public MethodDeclaration translateOperation(Operation operation) {
        NodeList<Modifier> modifiers = new NodeList<>();
        toModifierKeyword(operation.getVisibility())
                .ifPresent(modifierKeyword -> modifiers.add(new Modifier(modifierKeyword)));

        String name = operation.getName().getNameText();

        com.github.javaparser.ast.type.Type type = toJavaType(operation.getReturnType());

        NodeList<Parameter> parameters = new NodeList<>();
        try {
            for (io.github.morichan.fescue.feature.parameter.Parameter umlParameter : operation.getParameters()) {
                Parameter javaParameter = new Parameter(toJavaType(umlParameter.getType()),
                        umlParameter.getName().getNameText());
                parameters.add(javaParameter);
            }
        } catch (IllegalStateException e) {

        }

        return new MethodDeclaration(modifiers, name, type, parameters);
    }

    public ExpressionStmt occurenceSpeccificationToExpressionStmt(OccurenceSpecification occurenceSpecification) {
        ExpressionStmt expressionStmt = null;
        MessageSort messageSort = occurenceSpecification.getMessage().getMessageSort();
        if (messageSort == MessageSort.synchCall) {
            MethodCallExpr methodCallExpr = occurenceSpecificationToMethodCallExpr(occurenceSpecification);
            expressionStmt = new ExpressionStmt(methodCallExpr);
        } else if (messageSort == MessageSort.createMessage) {
            VariableDeclarationExpr variableDeclarationExpr = occurenceSpecificationToVariableDeclarationExpr(
                    occurenceSpecification);
            expressionStmt = new ExpressionStmt(variableDeclarationExpr);
        }

        return expressionStmt;
    }

    private MethodCallExpr occurenceSpecificationToMethodCallExpr(OccurenceSpecification occurenceSpecification) {
        // scopeの作成
        Lifeline startLifeline = occurenceSpecification.getLifeline();
        Lifeline endLifeline = occurenceSpecification.getMessage().getMessageEnd().getLifeline();
        Expression scope;
        if (startLifeline.getSignature().equals(endLifeline.getSignature())) {
            scope = new ThisExpr();
        } else if (endLifeline.getName().isEmpty()) {
            scope = new NameExpr(endLifeline.getType());
        } else {
            scope = new NameExpr(endLifeline.getName());
        }

        // nameの作成
        String name = occurenceSpecification.getMessage().getName();

        // 引数の作成
        NodeList<Expression> arguments = new NodeList<>();
        for (io.github.morichan.fescue.feature.parameter.Parameter parameter : occurenceSpecification.getMessage()
                .getParameterList()) {
            arguments.add(new NameExpr(parameter.getName().getNameText()));
        }

        MethodCallExpr methodCallExpr = new MethodCallExpr(scope, name, arguments);
        return methodCallExpr;
    }

    /**
     * 生成メッセージからインスタンス生成式への変換
     *
     * @param occurenceSpecification
     * @return
     */
    private VariableDeclarationExpr occurenceSpecificationToVariableDeclarationExpr(
            OccurenceSpecification occurenceSpecification) {
        Lifeline endLifeline = occurenceSpecification.getMessage().getMessageEnd().getLifeline();
        ArrayList<io.github.morichan.fescue.feature.parameter.Parameter> messageParameterList = occurenceSpecification
                .getMessage().getParameterList();

        // ObjectCreationExprの作成
        ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr();
        objectCreationExpr.setType(new ClassOrInterfaceType(endLifeline.getType()));
        // 引数の設定
        NodeList<Expression> arguments = new NodeList<>();
        for (io.github.morichan.fescue.feature.parameter.Parameter parameter : occurenceSpecification.getMessage()
                .getParameterList()) {
            arguments.add(new NameExpr(parameter.getName().getNameText()));
        }
        objectCreationExpr.setArguments(arguments);

        VariableDeclarator variableDeclarator = new VariableDeclarator(new ClassOrInterfaceType(endLifeline.getType()),
                endLifeline.getName(), objectCreationExpr);
        VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(variableDeclarator);
        return variableDeclarationExpr;
    }

    public Statement translateCombinedFragment(CombinedFragment combinedFragment) {
        Statement statement = null;

        if (combinedFragment.getKind() == InteractionOperandKind.opt) {
            IfStmt ifStmt = new IfStmt();
            NameExpr condition = new NameExpr(combinedFragment.getInteractionOperandList().get(0).getGuard());
            BlockStmt thenStmt = new BlockStmt();
            ifStmt.setCondition(condition);
            ifStmt.setThenStmt(thenStmt);
            statement = ifStmt;

        } else if (combinedFragment.getKind() == InteractionOperandKind.alt) {
            IfStmt firstIfStmt = new IfStmt();
            firstIfStmt.setCondition(new NameExpr(combinedFragment.getInteractionOperandList().get(0).getGuard()));
            firstIfStmt.setThenStmt(new BlockStmt());
            IfStmt preIfStmt = firstIfStmt;

            // 2つ目以降のif文
            for (int i = 1; i < combinedFragment.getInteractionOperandList().size(); i++) {
                InteractionOperand interactionOperand = combinedFragment.getInteractionOperandList().get(i);

                if (interactionOperand.getGuard().equals("else")) {
                    BlockStmt elseStmt = new BlockStmt();
                    preIfStmt.setElseStmt(elseStmt);
                } else {
                    IfStmt elseIfStmt = new IfStmt();
                    elseIfStmt
                            .setCondition(new NameExpr(combinedFragment.getInteractionOperandList().get(i).getGuard()));
                    elseIfStmt.setThenStmt(new BlockStmt());
                    if (i == 0) {
                        preIfStmt = elseIfStmt;
                    } else {
                        preIfStmt.setElseStmt(elseIfStmt);
                        preIfStmt = elseIfStmt;
                    }
                }
            }
            statement = firstIfStmt;

        } else if (combinedFragment.getKind() == InteractionOperandKind.loop) {
            WhileStmt whileStmt = new WhileStmt();
            whileStmt.setCondition(new NameExpr(combinedFragment.getInteractionOperandList().get(0).getGuard()));
            whileStmt.setBody(new BlockStmt());
            statement = whileStmt;

        } else if (combinedFragment.getKind() == InteractionOperandKind.BREAK) {
            IfStmt ifStmt = new IfStmt();
            NameExpr condition = new NameExpr(combinedFragment.getInteractionOperandList().get(0).getGuard());
            BlockStmt thenStmt = new BlockStmt();
            thenStmt.getStatements().add(new ReturnStmt());
            ifStmt.setCondition(condition);
            ifStmt.setThenStmt(thenStmt);
            statement = ifStmt;

        }

        return statement;
    }

    private Optional<InteractionFragment> toInteractionFragment(Class umlClass, Statement statement) {
        if (statement instanceof ExpressionStmt) {
            ExpressionStmt expressionStmt = (ExpressionStmt) statement;
            if (expressionStmt.getExpression() instanceof MethodCallExpr) {
                // メソッド呼び出し式の場合
                return Optional.of(toOccurenceSpecification(umlClass, (MethodCallExpr) expressionStmt.getExpression()));
            } else if (expressionStmt.getExpression() instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr variableDeclarationExpr = (VariableDeclarationExpr) expressionStmt
                        .getExpression();
                for (VariableDeclarator variableDeclarator : variableDeclarationExpr.getVariables()) {
                    if (variableDeclarator.getInitializer().isPresent()
                            && variableDeclarator.getInitializer().get() instanceof ObjectCreationExpr) {
                        // インスタンス化を行う変数宣言の場合
                        return Optional.of(toOccurenceSpecification(umlClass,
                                (ObjectCreationExpr) variableDeclarator.getInitializer().get()));
                    }
                }
            }
        } else if (statement instanceof IfStmt) {
            return Optional.of(toCombinedFragment(umlClass, (IfStmt) statement));
        } else if (statement instanceof WhileStmt) {
            return Optional.of(toCombinedFragment(umlClass, (WhileStmt) statement));
        } else if (statement instanceof ForStmt) {
            return Optional.of(toCombinedFragment(umlClass, (ForStmt) statement));
        }

        return Optional.empty();
    }

    /**
     * メソッド呼び出し式から同期Methodメッセージへの変換
     *
     * @param umlClass
     * @param methodCallExpr
     * @return
     */
    private OccurenceSpecification toOccurenceSpecification(Class umlClass, MethodCallExpr methodCallExpr) {
        // メッセージ開始点の作成
        OccurenceSpecification messageStart = new OccurenceSpecification(new Lifeline("", umlClass.getName()));
        messageStart.setStatement((Statement) methodCallExpr.getParentNode().get());

        // メッセージ終点と相互作用オカレンスの作成
        Lifeline endLifeline;
        InteractionUse interactionUse;
        if (methodCallExpr.getScope().isEmpty() || methodCallExpr.getScope().get() instanceof ThisExpr) {
            // 自ライフラインへのメッセージの場合
            endLifeline = new Lifeline("", umlClass.getName());
            interactionUse = new InteractionUse(endLifeline, methodCallExpr.getNameAsString());
        } else {
            // 他ライフラインへのメッセージの場合
            String lifelineType = findInstanceType(methodCallExpr, methodCallExpr.getScope().get().toString());
            endLifeline = new Lifeline(methodCallExpr.getScope().get().toString(), lifelineType);
            interactionUse = new InteractionUse(endLifeline, methodCallExpr.getNameAsString());
            interactionUse.setCollaborationUse(methodCallExpr.getScope().get().toString());
        }
        OccurenceSpecification messageEnd = new OccurenceSpecification(endLifeline);
        messageEnd.getInteractionFragmentList().add(interactionUse);

        // メッセージの作成
        Message message = new Message(methodCallExpr.getNameAsString(), messageEnd);
        NodeList arguments = methodCallExpr.getArguments();
        // メッセージの引数を設定
        for (int i = 0; i < arguments.size(); i++) {
            Expression expression = (Expression) arguments.get(i);
            // TODO:引数にメソッド呼び出し等がある場合の対応
            io.github.morichan.fescue.feature.parameter.Parameter parameter = new io.github.morichan.fescue.feature.parameter.Parameter(
                    new Name(expression.toString()));
            message.getParameterList().add(parameter);
            interactionUse.getParameterList().add(parameter);
        }
        messageStart.setMessage(message);

        return messageStart;
    }

    /**
     * インスタンス生成式から生成メッセージへの変換
     *
     * @param umlClass
     * @param objectCreationExpr
     * @return
     */
    private OccurenceSpecification toOccurenceSpecification(Class umlClass, ObjectCreationExpr objectCreationExpr) {
        VariableDeclarator variableDeclarator = (VariableDeclarator) objectCreationExpr.getParentNode().get();

        // メッセージ開始点の作成
        OccurenceSpecification messageStart = new OccurenceSpecification(new Lifeline("", umlClass.getName()));
        messageStart.setStatement((Statement) variableDeclarator.getParentNode().get().getParentNode().get());

        // メッセージ終点の作成
        OccurenceSpecification messageEnd;
        messageEnd = new OccurenceSpecification(
                new Lifeline(variableDeclarator.getNameAsString(), objectCreationExpr.getTypeAsString()));

        // メッセージの作成
        Message message = new Message("create", messageEnd);
        message.setMessageSort(MessageSort.createMessage);
        NodeList arguments = objectCreationExpr.getArguments();
        // メッセージの引数を設定
        for (int i = 0; i < arguments.size(); i++) {
            Expression expression = (Expression) arguments.get(i);
            // TODO:引数にメソッド呼び出し等がある場合の対応
            io.github.morichan.fescue.feature.parameter.Parameter parameter = new io.github.morichan.fescue.feature.parameter.Parameter(
                    new Name(expression.toString()));
            message.getParameterList().add(parameter);
        }
        // メッセージの戻り値の型を設定

        messageStart.setMessage(message);
        return messageStart;
    }

    private CombinedFragment toCombinedFragment(Class umlClass, IfStmt ifStmt) {
        Lifeline lifeline = new Lifeline("", umlClass.getName());
        Optional<Statement> elseStmtOptional = ifStmt.getElseStmt();

        InteractionOperandKind interactionOperandKind;
        if (elseStmtOptional.isPresent()) {
            interactionOperandKind = InteractionOperandKind.alt;
        } else if (isBreakCF(ifStmt)) {
            interactionOperandKind = InteractionOperandKind.BREAK;
        } else {
            interactionOperandKind = InteractionOperandKind.opt;
        }

        CombinedFragment combinedFragment = new CombinedFragment(lifeline, interactionOperandKind);
        combinedFragment.setStatement(ifStmt);
        combinedFragment.getInteractionOperandList().addAll(ifStmtToInteractionOperand(umlClass, ifStmt));

        return combinedFragment;
    }

    private Boolean isBreakCF(IfStmt ifStmt) {
        // thenStmtにbreak文を含む、かつ、while文またはfor文の中にあるif文である場合は、複合フラグメントbreakとする
        if (ifStmt.getThenStmt().findFirst(BreakStmt.class).isPresent()) {
            try {
                Node parentParentNode = ifStmt.getParentNode().get().getParentNode().get();
                if (parentParentNode instanceof WhileStmt || parentParentNode instanceof ForStmt) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        // thenStmtにreturn文を含む、かつ、メソッド内に直接書かれているif文である (while文等の中ではない)
        if (ifStmt.getThenStmt().findFirst(ReturnStmt.class).isPresent()) {
            try {
                Node parentParentNode = ifStmt.getParentNode().get().getParentNode().get();
                if (parentParentNode instanceof MethodDeclaration) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    private CombinedFragment toCombinedFragment(Class umlClass, WhileStmt whileStmt) {
        Lifeline lifeline = new Lifeline("", umlClass.getName());
        CombinedFragment combinedFragment = new CombinedFragment(lifeline, InteractionOperandKind.loop);
        combinedFragment.setStatement(whileStmt);

        InteractionOperand interactionOperand = new InteractionOperand(lifeline, whileStmt.getCondition().toString());
        NodeList statements = whileStmt.getBody().asBlockStmt().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = (Statement) statements.get(i);
            Optional<InteractionFragment> interactionFragmentOptional = toInteractionFragment(umlClass, statement);
            if (interactionFragmentOptional.isPresent()) {
                interactionOperand.getInteractionFragmentList().add(interactionFragmentOptional.get());
            }
        }
        combinedFragment.getInteractionOperandList().add(interactionOperand);

        return combinedFragment;
    }

    private CombinedFragment toCombinedFragment(Class umlClass, ForStmt forStmt) {
        Lifeline lifeline = new Lifeline("", umlClass.getName());
        CombinedFragment combinedFragment = new CombinedFragment(lifeline, InteractionOperandKind.loop);
        combinedFragment.setStatement(forStmt);

        String guard = "";
        if (forStmt.getCompare().isPresent()) {
            guard = forStmt.getCompare().get().toString();
        }
        InteractionOperand interactionOperand = new InteractionOperand(lifeline, guard);
        NodeList statements = forStmt.getBody().asBlockStmt().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = (Statement) statements.get(i);
            Optional<InteractionFragment> interactionFragmentOptional = toInteractionFragment(umlClass, statement);
            if (interactionFragmentOptional.isPresent()) {
                interactionOperand.getInteractionFragmentList().add(interactionFragmentOptional.get());
            }
        }
        combinedFragment.getInteractionOperandList().add(interactionOperand);

        return combinedFragment;
    }

    private ArrayList<InteractionOperand> ifStmtToInteractionOperand(Class umlClass, IfStmt ifStmt) {
        ArrayList<InteractionOperand> interactionOperands = new ArrayList<>();

        // thenのステートメント
        Lifeline lifeline = new Lifeline("", umlClass.getName());
        InteractionOperand thenInteractionOperand = new InteractionOperand(lifeline, ifStmt.getCondition().toString());
        NodeList thenStatements = ifStmt.getThenStmt().asBlockStmt().getStatements();
        for (int i = 0; i < thenStatements.size(); i++) {
            Statement statement = (Statement) thenStatements.get(i);
            Optional<InteractionFragment> interactionFragmentOptional = toInteractionFragment(umlClass, statement);
            if (interactionFragmentOptional.isPresent()) {
                thenInteractionOperand.getInteractionFragmentList().add(interactionFragmentOptional.get());
            }
        }
        interactionOperands.add(thenInteractionOperand);

        Optional<Statement> elseStmtOptional = ifStmt.getElseStmt();

        // elseステートメント
        if (elseStmtOptional.isEmpty()) {
            return interactionOperands;
        }

        String elseGuard = "";
        NodeList elseStatements;
        if (elseStmtOptional.get() instanceof IfStmt) {
            IfStmt elseIfStmt = (IfStmt) elseStmtOptional.get();
            interactionOperands.addAll(ifStmtToInteractionOperand(umlClass, elseIfStmt));
        } else {
            elseStatements = elseStmtOptional.get().asBlockStmt().getStatements();
            InteractionOperand elseInteractionOperand = new InteractionOperand(lifeline, "else");
            for (int i = 0; i < elseStatements.size(); i++) {
                Statement statement = (Statement) elseStatements.get(i);
                Optional<InteractionFragment> interactionFragmentOptional = toInteractionFragment(umlClass, statement);
                if (interactionFragmentOptional.isPresent()) {
                    elseInteractionOperand.getInteractionFragmentList().add(interactionFragmentOptional.get());
                }
            }
            interactionOperands.add(elseInteractionOperand);
        }

        return interactionOperands;
    }

    private Visibility toVisibility(List<Modifier> modifierList) {
        for (Modifier modifier : modifierList) {
            Modifier.Keyword keyword = modifier.getKeyword();
            if (keyword == Modifier.Keyword.PUBLIC) {
                return Visibility.Public;
            } else if (keyword == Modifier.Keyword.PROTECTED) {
                return Visibility.Protected;
            } else if (keyword == Modifier.Keyword.PRIVATE) {
                return Visibility.Private;
            }
        }
        // (独自仕様) Javaで上記のいずれも記述されていない場合は、UMLでpackageとして扱う
        return Visibility.Package;
    }

    private Optional<Modifier.Keyword> toModifierKeyword(Visibility visibility) {
        if (visibility == Visibility.Public) {
            return Optional.of(Modifier.Keyword.PUBLIC);
        } else if (visibility == Visibility.Protected) {
            return Optional.of(Modifier.Keyword.PROTECTED);
        } else if (visibility == Visibility.Private) {
            return Optional.of(Modifier.Keyword.PRIVATE);
        } else {
            // (独自仕様) UMLのPackageは、Javaでアクセス修飾子なしとして扱う
            return Optional.empty();
        }
    }

    private com.github.javaparser.ast.type.Type toJavaType(Type umlType) {
        String typeName = umlType.getName().getNameText();
        if (typeName.equals("void")) {
            return new VoidType();
        }

        try {
            return new PrimitiveType(PrimitiveType.Primitive.valueOf(typeName));
        } catch (IllegalArgumentException e) {
            ClassOrInterfaceType classOrInterfaceType = new ClassOrInterfaceType();
            classOrInterfaceType.setName(new SimpleName(typeName));
            return classOrInterfaceType;
        }
    }

    /**
     * nodeが属するメソッド内の変数宣言と、nodeが属するクラスのフィールド宣言から、instanceNameの型名を見つける
     * ただし、メソッド内の変数のスコープは考慮していないため、実際は使えない変数宣言から型名を取得する場合がある
     *
     * @param node
     * @param instanceName
     * @return
     */
    private String findInstanceType(Node node, String instanceName) {
        // methodDeclarationの探索
        MethodDeclaration methodDeclaration = null;
        Optional<Node> parentOptional = node.getParentNode();
        while (true) {
            if (parentOptional.isEmpty()) {
                break;
            }
            if (parentOptional.get() instanceof MethodDeclaration) {
                methodDeclaration = (MethodDeclaration) parentOptional.get();
                break;
            }
            parentOptional = parentOptional.get().getParentNode();
        }

        // methodDeclaration内の変数宣言を取得
        List<VariableDeclarator> variableDeclaratorList = new ArrayList<>();
        if (Objects.nonNull(methodDeclaration)) {
            variableDeclaratorList.addAll(methodDeclaration.getBody().get().findAll(VariableDeclarator.class));
        }

        // クラス内のフィールド宣言を取得
        Node root = node.findRootNode();
        List<FieldDeclaration> fieldDeclarationList = root.findAll(FieldDeclaration.class);
        for (FieldDeclaration fieldDeclaration : fieldDeclarationList) {
            variableDeclaratorList.addAll(fieldDeclaration.getVariables());
        }

        // instanceNameが変数名となっている変数宣言orフィールド宣言を探索
        for (VariableDeclarator variableDeclarator : variableDeclaratorList) {
            if (variableDeclarator.getNameAsString().equals(instanceName)) {
                return variableDeclarator.getTypeAsString();
            }
        }

        return "";
    }

}
