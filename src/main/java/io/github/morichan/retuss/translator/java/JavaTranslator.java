package io.github.morichan.retuss.translator.java;

import com.github.javaparser.StaticJavaParser;
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
import io.github.morichan.retuss.translator.common.AbstractTranslator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class JavaTranslator extends AbstractTranslator {
    /**
     * <p>
     * ソースコードのASTをUMLデータに変換する。
     * </p>
     */
    @Override
    public List<Class> translateCodeToUml(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Code cannot be null or empty");
        }

        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(code);
            return translateCodeToUml(compilationUnit);
        } catch (com.github.javaparser.ParseProblemException e) {
            throw new IllegalStateException("Failed to parse Java code: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected error during translation", e);
        }
    }

    private List<Class> translateCodeToUml(CompilationUnit compilationUnit) {

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
    @Override
    public CompilationUnit translateUmlToCode(List<Class> classList) {
        System.out.println("\n=== Starting UML to Code Translation ===");
        CompilationUnit newCU = new CompilationUnit();

        System.out.println("Number of classes to process: " + classList.size());

        // 既存のクラスをクリア
        newCU.getTypes().clear();

        // 重複チェック用のSet
        Set<String> processedClassNames = new HashSet<>();

        for (Class umlClass : classList) {
            String className = umlClass.getName();

            // 重複チェック
            if (processedClassNames.contains(className)) {
                System.out.println("Skipping duplicate class: " + className);
                continue;
            }

            System.out.println("\nProcessing class: " + className);
            processedClassNames.add(className);

            // クラス宣言の作成
            ClassOrInterfaceDeclaration classDeclaration = newCU.addClass(className);

            // 操作の追加
            addOperationsToClass(umlClass, classDeclaration);

            // 継承関係の設定
            setInheritance(umlClass, classDeclaration);
        }

        System.out.println("\n=== Completed UML to Code Translation ===");
        return newCU;
    }

    private void addOperationsToClass(Class umlClass, ClassOrInterfaceDeclaration classDeclaration) {
        List<Operation> operations = umlClass.getOperationList();
        System.out.println("Adding " + operations.size() + " operations");

        for (Operation operation : operations) {
            System.out.println("Processing operation: " + operation.toString());
            MethodDeclaration methodDecl = translateOperation(operation);

            // メソッド本体の生成
            BlockStmt body = new BlockStmt();

            // Interactionからメソッド本体を生成
            Optional<Interaction> interactionOpt = umlClass.findInteraction(operation);
            if (interactionOpt.isPresent()) {
                addInteractionToMethod(interactionOpt.get(), body);
            }

            methodDecl.setBody(body);
            classDeclaration.addMember(methodDecl);
        }
    }

    private void addInteractionToMethod(Interaction interaction, BlockStmt body) {
        List<InteractionFragment> fragments = interaction.getInteractionFragmentList();
        System.out.println("Processing " + fragments.size() + " interaction fragments");

        for (InteractionFragment fragment : fragments) {
            if (fragment instanceof OccurenceSpecification) {
                addOccurrenceToBody((OccurenceSpecification) fragment, body);
            }
        }
    }

    private void addOccurrenceToBody(OccurenceSpecification occurrence, BlockStmt body) {
        Message message = occurrence.getMessage();
        if (message.getMessageSort() == MessageSort.synchCall) {
            try {
                MethodCallExpr methodCall = new MethodCallExpr();
                methodCall.setName(message.getName());

                NodeList<Expression> arguments = new NodeList<>();
                for (io.github.morichan.fescue.feature.parameter.Parameter param : message.getParameterList()) {
                    arguments.add(new NameExpr(param.getName().getNameText()));
                }
                methodCall.setArguments(arguments);

                body.addStatement(new ExpressionStmt(methodCall));
            } catch (Exception e) {
                System.err.println("Error processing message: " + e.getMessage());
            }
        }
    }

    private void setInheritance(Class umlClass, ClassOrInterfaceDeclaration classDeclaration) {
        if (umlClass.getSuperClass().isPresent()) {
            System.out.println("Setting superclass: " + umlClass.getSuperClass().get().getName());
            ClassOrInterfaceType superClassType = new ClassOrInterfaceType();
            superClassType.setName(umlClass.getSuperClass().get().getName());
            classDeclaration.addExtendedType(superClassType);
        }
    }

    @Override
    public FieldDeclaration translateAttribute(Attribute attribute) {
        NodeList<Modifier> modifiers = new NodeList<>();
        toModifierKeyword(attribute.getVisibility())
                .ifPresent(modifierKeyword -> modifiers.add(new Modifier(modifierKeyword)));

        com.github.javaparser.ast.type.Type type = toSourceCodeType(attribute.getType());
        String name = attribute.getName().getNameText();
        // 初期値を持つFieldDeclarationの作り方がわからず、現状は初期値も変数名が持っている
        try {
            name += " = " + attribute.getDefaultValue();
        } catch (IllegalStateException e) {

        }
        return new FieldDeclaration(modifiers, type, name);
    }

    @Override
    public MethodDeclaration translateOperation(Operation operation) {
        NodeList<Modifier> modifiers = new NodeList<>();
        toModifierKeyword(operation.getVisibility())
                .ifPresent(modifierKeyword -> modifiers.add(new Modifier(modifierKeyword)));

        String name = operation.getName().getNameText();

        com.github.javaparser.ast.type.Type type = toSourceCodeType(operation.getReturnType());

        NodeList<Parameter> parameters = new NodeList<>();
        try {
            for (io.github.morichan.fescue.feature.parameter.Parameter umlParameter : operation.getParameters()) {
                Parameter javaParameter = new Parameter(toSourceCodeType(umlParameter.getType()),
                        umlParameter.getName().getNameText());
                parameters.add(javaParameter);
            }
        } catch (IllegalStateException e) {

        }

        return new MethodDeclaration(modifiers, name, type, parameters);
    }

    @Override
    protected Optional<Modifier.Keyword> toSourceCodeVisibility(Visibility visibility) {
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

    @Override
    protected com.github.javaparser.ast.type.Type toSourceCodeType(Type umlType) {
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
        // logger.debug("Processing if statement conversion to interaction operand");

        try {
            // thenのステートメント
            Lifeline lifeline = new Lifeline("", umlClass.getName());
            InteractionOperand thenInteractionOperand = new InteractionOperand(lifeline,
                    ifStmt.getCondition().toString());

            // BlockStmtの処理
            Statement thenStmt = ifStmt.getThenStmt();
            if (thenStmt instanceof BlockStmt) {
                NodeList<Statement> thenStatements = ((BlockStmt) thenStmt).getStatements();
                processStatements(umlClass, thenStatements, thenInteractionOperand);
            } else {
                // 単一のステートメントの場合（BlockStmtでない場合）
                processStatement(umlClass, thenStmt, thenInteractionOperand);
            }

            interactionOperands.add(thenInteractionOperand);

            // elseステートメントの処理
            Optional<Statement> elseStmtOptional = ifStmt.getElseStmt();
            if (elseStmtOptional.isPresent()) {
                Statement elseStmt = elseStmtOptional.get();

                if (elseStmt instanceof IfStmt) {
                    // else ifの場合
                    interactionOperands.addAll(ifStmtToInteractionOperand(umlClass, (IfStmt) elseStmt));
                } else {
                    // 通常のelseの場合
                    InteractionOperand elseInteractionOperand = new InteractionOperand(lifeline, "else");

                    if (elseStmt instanceof BlockStmt) {
                        NodeList<Statement> elseStatements = ((BlockStmt) elseStmt).getStatements();
                        processStatements(umlClass, elseStatements, elseInteractionOperand);
                    } else {
                        processStatement(umlClass, elseStmt, elseInteractionOperand);
                    }

                    interactionOperands.add(elseInteractionOperand);
                }
            }

            return interactionOperands;

        } catch (Exception e) {
            // logger.error("Error processing if statement to interaction operand", e);
            throw new TranslationException("Failed to convert if statement to interaction operand", e);
        }
    }

    private void processStatements(Class umlClass, NodeList<Statement> statements,
            InteractionOperand interactionOperand) {
        for (Statement statement : statements) {
            processStatement(umlClass, statement, interactionOperand);
        }
    }

    /**
     * 単一のステートメントを処理する
     */
    private void processStatement(Class umlClass, Statement statement, InteractionOperand interactionOperand) {
        if (statement instanceof ReturnStmt) {
            // ReturnStmtの特別処理
            handleReturnStatement((ReturnStmt) statement, interactionOperand);
        } else if (statement instanceof BreakStmt) {
            // BreakStmtの特別処理
            handleBreakStatement((BreakStmt) statement, interactionOperand);
        } else {
            // 通常のステートメント処理
            Optional<InteractionFragment> interactionFragmentOptional = toInteractionFragment(umlClass, statement);
            if (interactionFragmentOptional.isPresent()) {
                interactionOperand.getInteractionFragmentList().add(interactionFragmentOptional.get());
            }
        }
    }

    /**
     * ReturnStmtの処理
     */
    private void handleReturnStatement(ReturnStmt returnStmt, InteractionOperand interactionOperand) {
        // ReturnStmtを特別な形式のInteractionFragmentとして処理
        // 必要に応じて特別な処理を追加
        // logger.debug("Processing return statement");
        // 例: 特別なフラグを設定したり、終了メッセージを追加したりする
    }

    /**
     * BreakStmtの処理
     */
    private void handleBreakStatement(BreakStmt breakStmt, InteractionOperand interactionOperand) {
        // BreakStmtを特別な形式のInteractionFragmentとして処理
        // logger.debug("Processing break statement");
        // 例: ループ終了のマーカーを追加する
    }

    /**
     * CombinedFragmentの処理を改善
     */
    private CombinedFragment toCombinedFragment(Class umlClass, IfStmt ifStmt) {
        Lifeline lifeline = new Lifeline("", umlClass.getName());

        // Break条件の判定を改善
        InteractionOperandKind interactionOperandKind = determineOperandKind(ifStmt);

        CombinedFragment combinedFragment = new CombinedFragment(lifeline, interactionOperandKind);
        combinedFragment.setStatement(ifStmt);

        try {
            combinedFragment.getInteractionOperandList().addAll(ifStmtToInteractionOperand(umlClass, ifStmt));
        } catch (Exception e) {
            // logger.error("Error creating combined fragment", e);
            throw new TranslationException("Failed to create combined fragment", e);
        }

        return combinedFragment;
    }

    /**
     * InteractionOperandKindの判定を改善
     */
    private InteractionOperandKind determineOperandKind(IfStmt ifStmt) {
        if (isBreakCF(ifStmt)) {
            return InteractionOperandKind.BREAK;
        }

        return ifStmt.getElseStmt().isPresent() ? InteractionOperandKind.alt : InteractionOperandKind.opt;
    }

    /**
     * Break条件の判定を改善
     */
    private Boolean isBreakCF(IfStmt ifStmt) {
        Statement thenStmt = ifStmt.getThenStmt();

        // BlockStmtの場合は中身を確認
        if (thenStmt instanceof BlockStmt) {
            BlockStmt blockStmt = (BlockStmt) thenStmt;
            if (containsBreakOrReturn(blockStmt)) {
                return isInLoopOrMethod(ifStmt);
            }
        } else if (thenStmt instanceof ReturnStmt || thenStmt instanceof BreakStmt) {
            return isInLoopOrMethod(ifStmt);
        }

        return false;
    }

    /**
     * BlockStmt内のbreak/return文の存在確認
     */
    private boolean containsBreakOrReturn(BlockStmt blockStmt) {
        return !blockStmt.findAll(BreakStmt.class).isEmpty() ||
                !blockStmt.findAll(ReturnStmt.class).isEmpty();
    }

    /**
     * ステートメントの親コンテキストの確認
     */
    private boolean isInLoopOrMethod(Statement stmt) {
        Optional<Node> parent = stmt.getParentNode();
        while (parent.isPresent()) {
            Node node = parent.get();
            if (node instanceof WhileStmt || node instanceof ForStmt) {
                return true;
            }
            if (node instanceof MethodDeclaration) {
                return true;
            }
            parent = node.getParentNode();
        }
        return false;
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

    public class TranslationException extends RuntimeException {
        public TranslationException(String message) {
            super(message);
        }

        public TranslationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
