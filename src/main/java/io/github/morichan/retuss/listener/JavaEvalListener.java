package io.github.morichan.retuss.listener;

import io.github.morichan.retuss.language.java.*;
import io.github.morichan.retuss.language.java.Class;
import io.github.morichan.retuss.language.java.If;
import io.github.morichan.retuss.parser.java.JavaParser;
import io.github.morichan.retuss.parser.java.JavaParserBaseListener;

import java.util.ArrayList;
import java.util.List;

// 後で消す
import java.io.PrintStream;

/**
 * Javaソースコードのパーサを利用したコンテキストの抽出クラス
 * <p>
 * ANTLRに依存する。
 */
public class JavaEvalListener extends JavaParserBaseListener {
    private JavaParser.PackageDeclarationContext packageDeclaration = null;
    private List<JavaParser.ImportDeclarationContext> importDeclarations = new ArrayList<>();
    private List<JavaParser.TypeDeclarationContext> typeDeclarations = new ArrayList<>();
    private Java java = new Java();
    private AccessModifier accessModifier = null;
    private MethodBody methodBody;
    private boolean isAbstractMethod = false;
    private boolean hasAbstractMethods = false;
    // 追加分
    private int test;
    private If ifClass;
    private While whileClass;
    private For forClass;

    /**
     * <p>
     * 構文木のルートノードに入った際の操作を行います
     * </p>
     *
     * @param ctx 構文木のルートノードのコンテキスト
     */
    @Override
    public void enterCompilationUnit(JavaParser.CompilationUnitContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof JavaParser.PackageDeclarationContext) {
                packageDeclaration = (JavaParser.PackageDeclarationContext) ctx.getChild(i);

            } else if (ctx.getChild(i) instanceof JavaParser.ImportDeclarationContext) {
                importDeclarations.add((JavaParser.ImportDeclarationContext) ctx.getChild(i));

            } else if (ctx.getChild(i) instanceof JavaParser.TypeDeclarationContext) {
                typeDeclarations.add((JavaParser.TypeDeclarationContext) ctx.getChild(i));
            }
        }
    }

    @Override
    public void enterTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {
        hasAbstractMethods = false;
        Class javaClass = new Class();

        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof JavaParser.ClassDeclarationContext) {
                javaClass.setName(ctx.getChild(i).getChild(1).getText());
                javaClass.setExtendsClass(searchExtendsClass((JavaParser.ClassDeclarationContext) ctx.getChild(i)));
            }
        }

        java.addClass(javaClass);
    }

    @Override
    public void exitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {
        java.getClasses().get(java.getClasses().size() - 1).setAbstract(hasAbstractMethods);
    }

    @Override
    public void enterClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
        accessModifier = null;
        boolean isAlreadySearchedAccessModifier = false;
        isAbstractMethod = false;

        if (ctx.getChildCount() >= 2
                && ctx.getChild(ctx.getChildCount() - 1) instanceof JavaParser.MemberDeclarationContext) {
            for (int i = 0; i < ctx.getChildCount() - 1; i++) {
                if (ctx.getChild(i).getChild(0) instanceof JavaParser.ClassOrInterfaceModifierContext) {
                    try {
                        accessModifier = AccessModifier.choose(ctx.getChild(i).getChild(0).getChild(0).getText());
                        isAlreadySearchedAccessModifier = true;
                    } catch (IllegalArgumentException e) {
                        // static, abstract, final, strictfp or Annotation
                        if (ctx.getChild(i).getChild(0).getChild(0).getText().equals("abstract")) {
                            isAbstractMethod = true;
                            hasAbstractMethods = true;
                        }
                    }
                }
            }
            if (!isAlreadySearchedAccessModifier) {
                accessModifier = AccessModifier.Package;
            }
        } else if (ctx.getChild(0) instanceof JavaParser.MemberDeclarationContext) {
            accessModifier = AccessModifier.Package;
        }
    }

    @Override
    public void enterFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
        Field field = new Field();

        if (accessModifier != null) {
            field.setAccessModifier(accessModifier);
            accessModifier = null;
        }

        if (ctx.getChild(0).getChild(0) instanceof JavaParser.AnnotationContext) {
            field.setType(new Type(ctx.getChild(0).getChild(1).getText()));
        } else {
            field.setType(new Type(ctx.getChild(0).getChild(0).getText()));
        }

        field.setName(ctx.getChild(1).getChild(0).getChild(0).getChild(0).getText());

        if (ctx.getChild(1).getChild(0).getChildCount() > 1
                && ctx.getChild(1).getChild(0).getChild(2).getChild(0) instanceof JavaParser.ExpressionContext) {
            if (ctx.getChild(1).getChild(0).getChild(2).getChild(0).getChildCount() == 2
                    && ctx.getChild(1).getChild(0).getChild(2).getChild(0)
                            .getChild(1) instanceof JavaParser.CreatorContext
                    && ctx.getChild(1).getChild(0).getChild(2).getChild(0).getChild(1).getChildCount() == 2
                    && ctx.getChild(1).getChild(0).getChild(2).getChild(0).getChild(1)
                            .getChild(1) instanceof JavaParser.ArrayCreatorRestContext
                    && !ctx.getChild(1).getChild(0).getChild(2).getChild(0).getChild(1).getChild(1).getChild(1)
                            .getText().equals("]")
                    && ctx.getChild(1).getChild(0).getChild(2).getChild(0).getChild(1).getChild(1)
                            .getChild(1) instanceof JavaParser.ExpressionContext) {
                // 既定値での配列宣言文
                field.setArrayLength(new ArrayLength(Integer.parseInt(ctx.getChild(1).getChild(0).getChild(2)
                        .getChild(0).getChild(1).getChild(1).getChild(1).getText())));
            } else {
                // 既定値の式
                field.setValue(ctx.getChild(1).getChild(0).getChild(2).getChild(0).getText());
            }
        }

        java.getClasses().get(java.getClasses().size() - 1).addField(field);
    }

    @Override
    public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        Method method = new Method();
        methodBody = new MethodBody();

        if (accessModifier != null) {
            method.setAccessModifier(accessModifier);
            accessModifier = null;
        }

        if (isAbstractMethod)
            method.setAbstract(true);

        if (ctx.getChild(0).getChild(0) instanceof JavaParser.TypeTypeContext) {
            if (ctx.getChild(0).getChild(0).getChild(0) instanceof JavaParser.AnnotationContext) {
                method.setType(new Type(ctx.getChild(0).getChild(0).getChild(1).getText()));
            } else {
                method.setType(new Type(ctx.getChild(0).getChild(0).getChild(0).getText()));
            }
        } else {
            method.setType(new Type(ctx.getChild(0).getChild(0).getText()));
        }

        method.setName(ctx.getChild(1).getText());

        java.getClasses().get(java.getClasses().size() - 1).addMethod(method);
    }

    @Override
    public void exitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        int lastClass = java.getClasses().size() - 1;
        int lastMethod = java.getClasses().get(lastClass).getMethods().size() - 1;

        java.getClasses().get(lastClass).getMethods().get(lastMethod).setMethodBody(methodBody);
    }

    @Override
    public void enterFormalParameter(JavaParser.FormalParameterContext ctx) {
        if (!(ctx.getParent().getParent().getParent() instanceof JavaParser.MethodDeclarationContext))
            return;

        Argument argument = new Argument();

        if (ctx.getChild(0).getChild(0) instanceof JavaParser.AnnotationContext) {
            argument.setType(new Type(ctx.getChild(0).getChild(1).getText()));
        } else {
            argument.setType(new Type(ctx.getChild(0).getChild(0).getText()));
        }

        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof JavaParser.VariableDeclaratorIdContext) {
                argument.setName(ctx.getChild(i).getChild(0).getText());
            }
        }

        int lastClass = java.getClasses().size() - 1;
        int lastMethod = java.getClasses().get(lastClass).getMethods().size() - 1;

        java.getClasses().get(lastClass).getMethods().get(lastMethod).addArgument(argument);
    }

    @Override
    public void enterBlockStatement(JavaParser.BlockStatementContext ctx) {
        if (ctx.getParent().getParent() instanceof JavaParser.MethodBodyContext) {

            if (ctx.getChild(0) instanceof JavaParser.LocalVariableDeclarationContext) {
                // ローカル変数宣言
                for (int i = 0; i < ctx.getChild(0).getChildCount(); i++) {
                    if (ctx.getChild(0).getChild(i) instanceof JavaParser.VariableModifierContext)
                        continue;
                        methodBody.addStatement(createLocalVariableDeclaration(new Type(ctx.getChild(0).getChild(i).getText()),
                            (JavaParser.VariableDeclaratorsContext) ctx.getChild(0).getChild(i + 1)));
                    return;
                }
            } else if (ctx.getChild(0) instanceof JavaParser.StatementContext) {
                if (ctx.getChild(0).getChild(0) instanceof JavaParser.ExpressionContext) {
                    if (ctx.getChild(0).getChild(0).getChildCount() == 3) {
                        if (ctx.getChild(0).getChild(0).getChild(0) instanceof JavaParser.ExpressionContext
                                && ctx.getChild(0).getChild(0).getChild(2) instanceof JavaParser.ExpressionContext
                                && ctx.getChild(0).getChild(0).getChild(1).getText().equals("=")) {
                            // 代入文
                            methodBody.addStatement(createAssignment((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0)));
                        } else if ((ctx.getChild(0).getChild(0).getChild(1).getText().equals("(")
                                && ctx.getChild(0).getChild(0).getChild(2).getText().equals(")"))) {
                            // 引数なしのメソッド呼び出し
                            Method method = createMethod((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0));
                            methodBody.addStatement(method);
                        }
                    } else if (ctx.getChild(0).getChild(0).getChildCount() == 4) {
                         if ((ctx.getChild(0).getChild(0).getChild(1).getText().equals("(") && ctx.getChild(0).getChild(0).getChild(2) instanceof JavaParser.ExpressionListContext
                                && ctx.getChild(0).getChild(0).getChild(3).getText().equals(")"))) {
                             // 引数ありのメソッド呼び出し
                             Method method = createMethod((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0));
                             methodBody.addStatement(method);
                        }
                    }
                }
            }
        } else if (ctx.getParent().getParent().getParent().getChild(0).getText().equals("if")) {
            // if文の中にある処理はifClass.statementsに格納する
            if (ctx.getChild(0) instanceof JavaParser.LocalVariableDeclarationContext) {
                // ローカル変数宣言
                for (int i = 0; i < ctx.getChild(0).getChildCount(); i++) {
                    if (ctx.getChild(0).getChild(i) instanceof JavaParser.VariableModifierContext)
                        continue;
                    ifClass.addStatement(createLocalVariableDeclaration(new Type(ctx.getChild(0).getChild(i).getText()),
                            (JavaParser.VariableDeclaratorsContext) ctx.getChild(0).getChild(i + 1)));
                    return;
                }

            } else if (ctx.getChild(0) instanceof JavaParser.StatementContext) {
                if (ctx.getChild(0).getChild(0) instanceof JavaParser.ExpressionContext) {
                    if (ctx.getChild(0).getChild(0).getChildCount() == 3) {
                        if (ctx.getChild(0).getChild(0).getChild(0) instanceof JavaParser.ExpressionContext
                                && ctx.getChild(0).getChild(0).getChild(2) instanceof JavaParser.ExpressionContext
                                && ctx.getChild(0).getChild(0).getChild(1).getText().equals("=")) {
                            // 代入文
                            ifClass.addStatement(createAssignment((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0)));
                        } else if (ctx.getChild(0).getChild(0).getChild(1).getText().equals("(")
                                && ctx.getChild(0).getChild(0).getChild(2).getText().equals(")")) {
                            // 引数なしのメソッド呼び出し
                            Method method = createMethod((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0));
                            ifClass.addStatement(method);
                        }
                    } else if (ctx.getChild(0).getChild(0).getChildCount() == 4) {
                        if ((ctx.getChild(0).getChild(0).getChild(1).getText().equals("(") && ctx.getChild(0).getChild(0).getChild(2) instanceof JavaParser.ExpressionListContext
                                && ctx.getChild(0).getChild(0).getChild(3).getText().equals(")"))) {
                            // 引数ありのメソッド呼び出し
                            Method method = createMethod((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0));
                            ifClass.addStatement(method);
                        }
                    }
                }
            }
        } else if (ctx.getParent().getParent().getParent().getChild(0).getText().equals("while")){
            // while文の中にある処理はwhileClass.statementsに格納する
            if (ctx.getChild(0) instanceof JavaParser.LocalVariableDeclarationContext) {
                // ローカル変数宣言
                for (int i = 0; i < ctx.getChild(0).getChildCount(); i++) {
                    if (ctx.getChild(0).getChild(i) instanceof JavaParser.VariableModifierContext)
                        continue;
                    whileClass.addStatement(createLocalVariableDeclaration(new Type(ctx.getChild(0).getChild(i).getText()),
                            (JavaParser.VariableDeclaratorsContext) ctx.getChild(0).getChild(i + 1)));
                    return;
                }

            } else if (ctx.getChild(0) instanceof JavaParser.StatementContext) {
                if (ctx.getChild(0).getChild(0) instanceof JavaParser.ExpressionContext) {
                    if (ctx.getChild(0).getChild(0).getChildCount() == 3) {
                        if (ctx.getChild(0).getChild(0).getChild(0) instanceof JavaParser.ExpressionContext
                                && ctx.getChild(0).getChild(0).getChild(2) instanceof JavaParser.ExpressionContext
                                && ctx.getChild(0).getChild(0).getChild(1).getText().equals("=")) {
                            // 代入文
                            whileClass.addStatement(createAssignment((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0)));
                        } else if (ctx.getChild(0).getChild(0).getChild(1).getText().equals("(")
                                && ctx.getChild(0).getChild(0).getChild(2).getText().equals(")")) {
                            // 引数なしのメソッド呼び出し
                            Method method = createMethod((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0));
                            whileClass.addStatement(method);
                        }
                    } else if (ctx.getChild(0).getChild(0).getChildCount() == 4) {
                        if ((ctx.getChild(0).getChild(0).getChild(1).getText().equals("(") && ctx.getChild(0).getChild(0).getChild(2) instanceof JavaParser.ExpressionListContext
                                && ctx.getChild(0).getChild(0).getChild(3).getText().equals(")"))) {
                            // 引数ありのメソッド呼び出し
                            Method method = createMethod((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0));
                            whileClass.addStatement(method);
                        }
                    }
                }
            }
        } else if (ctx.getParent().getParent().getParent().getChild(0).getText().equals("for") && ctx.getParent().getParent().getParent().getChild(2).getChild(1).getText().equals(";") && ctx.getParent().getParent().getParent().getChild(2).getChild(3).getText().equals(";")){
            // for文の基本形の中にある処理forClass.statementsに格納する for(i=0;i<10;i++)のような基本形のみ対応
            if (ctx.getChild(0) instanceof JavaParser.LocalVariableDeclarationContext) {
                // ローカル変数宣言
                for (int i = 0; i < ctx.getChild(0).getChildCount(); i++) {
                    if (ctx.getChild(0).getChild(i) instanceof JavaParser.VariableModifierContext)
                        continue;
                    forClass.addStatement(createLocalVariableDeclaration(new Type(ctx.getChild(0).getChild(i).getText()),
                            (JavaParser.VariableDeclaratorsContext) ctx.getChild(0).getChild(i + 1)));
                    return;
                }

            } else if (ctx.getChild(0) instanceof JavaParser.StatementContext) {
                if (ctx.getChild(0).getChild(0) instanceof JavaParser.ExpressionContext) {
                    if (ctx.getChild(0).getChild(0).getChildCount() == 3) {
                        if (ctx.getChild(0).getChild(0).getChild(0) instanceof JavaParser.ExpressionContext
                                && ctx.getChild(0).getChild(0).getChild(2) instanceof JavaParser.ExpressionContext
                                && ctx.getChild(0).getChild(0).getChild(1).getText().equals("=")) {
                            // 代入文
                            forClass.addStatement(createAssignment((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0)));
                        } else if (ctx.getChild(0).getChild(0).getChild(1).getText().equals("(")
                                && ctx.getChild(0).getChild(0).getChild(2).getText().equals(")")) {
                            // 引数なしのメソッド呼び出し
                            Method method = createMethod((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0));
                            forClass.addStatement(method);
                        }
                    } else if (ctx.getChild(0).getChild(0).getChildCount() == 4) {
                        if ((ctx.getChild(0).getChild(0).getChild(1).getText().equals("(") && ctx.getChild(0).getChild(0).getChild(2) instanceof JavaParser.ExpressionListContext
                                && ctx.getChild(0).getChild(0).getChild(3).getText().equals(")"))) {
                            // 引数ありのメソッド呼び出し
                            Method method = createMethod((JavaParser.ExpressionContext) ctx.getChild(0).getChild(0));
                            forClass.addStatement(method);
                        }
                    }
                }
            }
        }

        // 仮
        // System.out.println("******************************");
        // for (int i = 0; i < ctx.getChildCount(); i++) {
        // System.out.println(ctx.getChild(i).getText());
        // System.out.println(ctx.getParent().getParent().getParent().getChild(0).getText());
        // System.out.println(ctx.getChild(i) instanceof JavaParser.StatementContext);
        // }
        // System.out.println(ctx.getChild(0).getChild(0).getText());
        // System.out.println("******************************");
    }

    @Override
    public void enterStatement(JavaParser.StatementContext ctx) {
        // if文を見つけた場合、Ifクラスに条件文を格納する
        // IfクラスをJava > Class > Method > MethodBodyに格納する
        if (ctx.getChild(0).getText().equals("if")) {
            ifClass = new If();
            ifClass.setCondition(ctx.getChild(1).getText());
        } else if (ctx.getChild(0).getText().equals("while")) {
            whileClass = new While();
            whileClass.setCondition(ctx.getChild(1).getText());
        } else if (ctx.getChild(0).getText().equals("for")) {
            // for文の基本形のみ対応 ex) for(int i=0; i<10; i++)

            String initVarName, initOperator, initValue;
            String expressionVarName, expressionOperator, expressionValue;
            String updateVarName, updateVarOperator;

            if (ctx.getChild(2).getChild(0).getChild(0).getChildCount() == 2) {
                // 初期化式に型名が含まれる場合 ex) for(int i=0; i<10; i++)
                initVarName = ctx.getChild(2).getChild(0).getChild(0).getChild(1).getChild(0).getChild(0).getText();
                initOperator = ctx.getChild(2).getChild(0).getChild(0).getChild(1).getChild(0).getChild(1).getText();
                initValue = ctx.getChild(2).getChild(0).getChild(0).getChild(1).getChild(0).getChild(2).getText();
            } else if (ctx.getChild(2).getChild(0).getChild(0).getChildCount() == 1) {
                // 初期化式に型名が含まれない場合 ex) for(i=0; i<10; i++)
                initVarName = ctx.getChild(2).getChild(0).getChild(0).getChild(0).getChild(0).getText();
                initOperator = ctx.getChild(2).getChild(0).getChild(0).getChild(0).getChild(1).getText();
                initValue = ctx.getChild(2).getChild(0).getChild(0).getChild(0).getChild(2).getText();
            } else {
                return;
            }

            if (ctx.getChild(2).getChild(2).getChildCount() == 3) {
                // 継続条件式 ex) i<10;
                expressionVarName = ctx.getChild(2).getChild(2).getChild(0).getText();
                expressionOperator = ctx.getChild(2).getChild(2).getChild(1).getText();
                expressionValue = ctx.getChild(2).getChild(2).getChild(2).getText();
            } else {
                return;
            }

            if (ctx.getChild(2).getChild(4).getChild(0).getChildCount() == 2) {
                // 更新式 ex) i++
                updateVarName = ctx.getChild(2).getChild(4).getChild(0).getChild(0).getText();
                updateVarOperator = ctx.getChild(2).getChild(4).getChild(0).getChild(1).getText();
            } else {
                return;
            }

            if (initVarName.equals(expressionVarName) && initVarName.equals(updateVarName) && initOperator.equals("=")) {
                int initValueInt, expressionValueInt;
                int numLoop = 0;

                forClass = new For();
                forClass.setForInit(ctx.getChild(2).getChild(0).getText());
                forClass.setExpression(ctx.getChild(2).getChild(2).getText());
                forClass.setForUpdate(ctx.getChild(2).getChild(4).getText());

                // initValue, expressionValueが整数であるかのチェック
                try {
                    initValueInt = Integer.parseInt(initValue);
                    expressionValueInt = Integer.parseInt(expressionValue);
                } catch (Exception e) {
                    return;
                }

                if (expressionOperator.equals("<") && updateVarOperator.equals("++")) {
                    numLoop = expressionValueInt - initValueInt;
                } else if (expressionOperator.equals("<=") && updateVarOperator.equals("++")) {
                    numLoop = expressionValueInt - initValueInt + 1;
                } else if (expressionOperator.equals(">") && updateVarOperator.equals("--")) {
                    numLoop = initValueInt - expressionValueInt;
                } else if (expressionOperator.equals(">=") && updateVarOperator.equals("--")) {
                    numLoop = initValueInt - expressionValueInt + 1;
                }

                if (numLoop > 0) {
                    forClass.setNumLoop(String.valueOf(numLoop));
                }
            }
        }
    }

    @Override
    public void exitStatement(JavaParser.StatementContext ctx) {
        if (ctx.getChild(0).getText().equals("if")) {
            // if文から抜ける時、methodBodyにifClassを追加する
            methodBody.addStatement(ifClass);
        } else if(ctx.getChild(0).getText().equals("while")){
            // while文から抜ける時、methodBodyにwhileClassを追加する
            methodBody.addStatement(whileClass);
        } else if (ctx.getChild(0).getText().equals("for")) {
            if (ctx.getChild(2).getChild(1).getText().equals(";") && ctx.getChild(2).getChild(3).getText().equals(";")) {
                // for(i=0;i<10;i++)のような基本形の場合
                methodBody.addStatement(forClass);
            }
        }
        // 確認
//        System.out.println(methodBody.getStatements());
//        System.out.println("******************************");
//        While tmp = (While) methodBody.getStatements().get(3);
//        System.out.println(tmp.getCondition());
//        System.out.println(tmp.getStatements());
//        System.out.println("------------------------------");

    }

    // あとで消す
    public int getTest() {
        return test;
    }

    private LocalVariableDeclaration createLocalVariableDeclaration(Type type, JavaParser.VariableDeclaratorsContext ctx){
        LocalVariableDeclaration local = new LocalVariableDeclaration();
        local.setType(type);
        local.setName(ctx.getChild(0).getText());

        if (ctx.getChildCount() == 3) {
            // 初期値ありの場合
            local.setValue(ctx.getChild(2).getText());
        }
        return local;
    }

    private Assignment createAssignment(JavaParser.ExpressionContext assignmentCtx) {
        return new Assignment(assignmentCtx.getChild(0).getText(), assignmentCtx.getChild(2).getText());
    }

    private Method createMethod(JavaParser.ExpressionContext ctx) {
        List<Argument> argumentList = new ArrayList<Argument>();
        Method method = new Method();

        if (ctx.getChild(0).getChildCount() == 1) {
            // 自クラス内のメソッド呼び出しの場合 method();
            method.setType(new Type("TmpType"));
            method.setName(ctx.getChild(0).getText());
        } else if (ctx.getChild(0).getChildCount() == 3 && ctx.getChild(0).getChild(1).getText().equals(".")) {
            // 他クラスのメソッド呼び出しの場合 a.method();
            method.setType(new Type(ctx.getChild(0).getChild(0).getText()));
            method.setName(ctx.getChild(0).getChild(2).getText());
        }

        if (ctx.getChildCount() == 4){
            // 引数ありのメソッド呼び出しの場合
            for (int i = 0; i < ctx.getChild(2).getChildCount(); i++){
                if (!(ctx.getChild(2).getChild(i).getText().equals(","))){
                    Argument argument = new Argument(new Type("TmpType"), ctx.getChild(2).getChild(i).getText());
                    argumentList.add(argument);
                }
            }
            method.setArguments(argumentList);
        }

        return method;
    }

    private Class searchExtendsClass(JavaParser.ClassDeclarationContext ctx) {

        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof JavaParser.TypeTypeContext) {
                return new Class(ctx.getChild(i).getText());
            }
        }

        return null;
    }

    public JavaParser.PackageDeclarationContext getPackageDeclaration() throws NullPointerException {
        return packageDeclaration;
    }

    public List<JavaParser.ImportDeclarationContext> getImportDeclarations() {
        return importDeclarations;
    }

    public List<JavaParser.TypeDeclarationContext> getTypeDeclarations() {
        return typeDeclarations;
    }

    public Java getJava() {
        return java;
    }
}
