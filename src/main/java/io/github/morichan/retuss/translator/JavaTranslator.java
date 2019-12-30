package io.github.morichan.retuss.translator;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.direction.In;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.language.java.*;
import io.github.morichan.retuss.language.java.Class;
import io.github.morichan.retuss.language.uml.Package;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import io.github.morichan.retuss.window.diagram.sequence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p> Java翻訳者クラス </p>
 */
public class JavaTranslator {

    private Java java;

    /**
     * <p> クラス図のパッケージからJavaに翻訳します </p>
     *
     * @param classPackage クラス図のクラスリスト
     * @return Javaソースコード
     */
    public Java translate(Package classPackage) {
        java = new Java();

        for (io.github.morichan.retuss.language.uml.Class cc : classPackage.getClasses()) {
            java.addClass(createJavaClass(cc));
        }

        searchGeneralizationClass(classPackage.getClasses());

        return java;
    }

    private Class createJavaClass(io.github.morichan.retuss.language.uml.Class classClass) {
        Class javaClass = new Class(classClass.getName());

        for (Attribute attribute : classClass.extractAttributes()) {
            Field field = new Field(new Type(attribute.getType().toString()), attribute.getName().toString());
            try {
                field.setAccessModifier(convert(attribute.getVisibility()));
            } catch (IllegalStateException e) {
                field.setAccessModifier(AccessModifier.Private);
            }
            try {
                field.setValue(attribute.getDefaultValue().toString());
            } catch (IllegalStateException e) {
                field.setValue(null);
            }
            try {
                field.setArrayLength(new ArrayLength(Integer.parseInt(attribute.getMultiplicityRange().toString())));
            } catch (IllegalStateException e) {
                field.setArrayLength(null);
            }
            javaClass.addField(field);
        }

        for (Attribute relation : classClass.extractRelations()) {
            Field field = new Field(new Type(relation.getType().toString()), relation.getName().toString());
            try {
                field.setAccessModifier(convert(relation.getVisibility()));
            } catch (IllegalStateException e) {
                field.setAccessModifier(AccessModifier.Private);
            }
            try {
                field.setArrayLength(new ArrayLength(Integer.parseInt(relation.getMultiplicityRange().toString())));
            } catch (IllegalStateException e) {
                field.setArrayLength(null);
                field.setValue("new " + relation.getType().toString());
            }
            javaClass.addField(field);
        }

        for (OperationGraphic operationGraphic : classClass.getOperationGraphics()) {
            Operation operation = operationGraphic.getOperation();
            Method method = new Method(new Type(operation.getReturnType().toString()), operation.getName().toString());
            if (operationGraphic.isAbstract()) {
                method.setAbstract(true);
                javaClass.setAbstract(true);
            }
            try {
                method.setAccessModifier(convert(operation.getVisibility()));
            } catch (IllegalStateException e) {
                method.setAccessModifier(AccessModifier.Public);
            }
            try {
                for (Parameter param : operation.getParameters()) {
                    method.addArgument(new Argument(new Type(param.getType().toString()), param.getName().toString()));
                }
            } catch (IllegalStateException e) {
                method.emptyArguments();
            }

            if (operationGraphic.getInteraction() != null) {
                MethodBody body = new MethodBody();
                Pattern p = Pattern.compile(" ([^(]*)[(]");

                for (InteractionFragment interactionFragment : operationGraphic.getInteraction().getMessage().getInteractionFragmentList()) {
                    BlockStatement blockStatement = convertInteractionFragmentToBlockStatement(interactionFragment);
                    if (blockStatement != null) {
                        body.addStatement(blockStatement);
                    }
                }

//                for (MessageOccurrenceSpecification message : operationGraphic.getInteraction().getMessage().getMessages()) {
//                    if (message.getMessageType() == MessageType.Declaration) {
//                        LocalVariableDeclaration local = new LocalVariableDeclaration(new Type(message.getType().getName()), message.getName());
//                        if (message.getValue() != null) local.setValue(message.getValue());
//                        body.addStatement(local);
//                    } else if (message.getMessageType() == MessageType.Assignment) {
//                        Assignment assignment = new Assignment(message.getName());
//                        if (message.getValue() != null) assignment.setValue(message.getValue());
//                        body.addStatement(assignment);
//                    } else if (message.getMessageType() == MessageType.Method) {
//                        Matcher m = p.matcher(message.getName());
//                        if (!m.find()) continue;
//                        Method methodSyntax = new Method(new Type(message.getType().getName()), m.group(1));
//                        body.addStatement(methodSyntax);
//                    }
//                }

                method.setMethodBody(body);
            }

            javaClass.addMethod(method);
        }

        return javaClass;
    }

    private AccessModifier convert(Visibility visibility) {
        if (visibility == Visibility.Public) {
            return AccessModifier.Public;
        } else if (visibility == Visibility.Protected) {
            return AccessModifier.Protected;
        } else if (visibility == Visibility.Package) {
            return AccessModifier.Package;
        } else {
            return AccessModifier.Private;
        }
    }

    /**
     * <p> 汎化関係にあたるクラスを {@link #java} から探して {@link #java} の各クラスに格納します </p>
     *
     * <p>
     *     {@link #translate(Package)} 内の最後で用いています。
     * </p>
     *
     * <p>
     *     例えば、 {@code A -|> B -|> C} のような関係になっているとします。
     *     なお、 {@code -|>} は汎化関係にあたります。
     * </p>
     *
     * <p>
     *     このメソッドを利用する前には {@link #translate(Package)} 内で、
     *     {@link #java} にそれぞれクラスA、クラスB、クラスCが継承関係なしの状態で格納されています。
     * </p>
     *
     * <pre>
     *     {@code
     *     {"java":
     *         [
     *             {"A": {"extends": null}},
     *             {"B": {"extends": null}},
     *             {"C": {"extends": null}}
     *         ]
     *     }
     *     }
     * </pre>
     *
     * <p>
     *     まず、クラスAを見ると、クラスBが汎化関係にあることが {@code classClasses} からわかります。
     *     そこで、クラスBという名前と一致する {@link #java} 内におけるクラスBを抽出します
     *     （これを {@code java.getClasses.stream().filter(...).collection(Collectors.toList())} によるラムダ式で表現しています）。
     *     名前が一致するクラスBは唯一のはずですから、そのまま {@code oneGeneralizationJavaClass} リストに存在する0番目の要素を抽出し、
     *     {@link #java} のクラスAの継承クラスに格納します。
     *     参照渡しのため、{@code java.A.extends == java.B} です。
     * </p>
     *
     * <pre>
     *     {@code
     *     {"java":
     *         [
     *             {"A": {"extends": {"B": {"extends": null}}}},
     *             {"B": {"extends": null}},
     *             {"C": {"extends": null}}
     *         ]
     *     }
     *     }
     * </pre>
     *
     * <p>
     *     次に、クラスBを見ると、クラスCが汎化関係にあることが {@code classClasses} からわかります。
     *     そこで、クラスCという名前と一致する {@link #java} 内におけるクラスCを抽出します（同上）。
     *     名前が一致するクラスCもやはり唯一のはずですから、そのまま {@code oneGeneralizationJavaClass} リストに存在する0番目の要素を抽出し、
     *     {@link #java} のクラスBの継承クラスに格納します。
     *     参照渡しのため、 {@code java.B.extends == java.C} です。
     * </p>
     *
     * <pre>
     *     {@code
     *     {"java":
     *         [
     *             {"A": {"extends": {"B": {"extends": null}}}},
     *             {"B": {"extends": {"C": {"extends": null}}}},
     *             {"C": {"extends": null}}
     *         ]
     *     }
     *     }
     * </pre>
     *
     * <p>
     *     さて、どちらも参照渡しですから、 {@code java.B.extends} に {@code java.C} の参照が入った瞬間、なんと {@code java.A.extends.B.extends} にも参照渡しが入ります。
     * </p>
     *
     * <pre>
     *     {@code
     *     {"java":
     *         [
     *             {"A": {"extends": {"B": {"extends": {"C": {"extends": null}}}}}},
     *             {"B": {"extends": {"C": {"extends": null}}}},
     *             {"C": {"extends": null}}
     *         ]
     *     }
     *     }
     * </pre>
     *
     * <p>
     *     これにより、どのクラスから見ても継承関係がわかるような構造になりました。
     *     この手法は、どのクラスがどのような順序で格納していたとしても上手く処理できます。
     * </p>
     *
     * @param classClasses クラス図のクラスのリスト
     */
    private void searchGeneralizationClass(List<io.github.morichan.retuss.language.uml.Class> classClasses) {
        for (int i = 0; i < classClasses.size(); i++) {
            if (classClasses.get(i).getGeneralizationClass() != null) {
                int finalI = i;
                List<io.github.morichan.retuss.language.java.Class> oneGeneralizationJavaClass =
                        java.getClasses().stream().filter(
                                jc -> jc.getName().equals(classClasses.get(finalI).getGeneralizationClass().getName())
                        ).collect(Collectors.toList());
                java.getClasses().get(finalI).setExtendsClass(oneGeneralizationJavaClass.get(0));
            }
        }
    }

    /**
     *
     */
    private BlockStatement convertInteractionFragmentToBlockStatement(InteractionFragment interactionFragment) {
        if (interactionFragment instanceof CombinedFragment) {
            CombinedFragment cf = (CombinedFragment) interactionFragment;
            if (cf.getInteractionOperandKind() == InteractionOperandKind.opt || cf.getInteractionOperandKind() == InteractionOperandKind.alt) {
                ArrayList<If> ifClassList = new ArrayList<If>();

                for (InteractionOperand io : cf.getInteractionOperandList()) {
                    If ifClass = new If();

                    if (io.getGuard().equals("else")) {
                        // elseだったら、前のifClassのelseStatementに追加
                        ifClass = ifClassList.get(ifClassList.size() - 1);
                        for (InteractionFragment interactionFragmentInIo : io.getInteractionFragmentList()) {
                            ifClass.addElseStatement(convertInteractionFragmentToBlockStatement(interactionFragmentInIo));
                        }
                    } else {
                        ifClass.setCondition(io.getGuard());
                        for (InteractionFragment interactionFragmentInIo : io.getInteractionFragmentList()) {
                            ifClass.addStatement(convertInteractionFragmentToBlockStatement(interactionFragmentInIo));
                        }
                        ifClassList.add(ifClass);
                    }

                }

                for (int i = ifClassList.size() - 1; i > 0; i--) {
                    ifClassList.get(i-1).addElseStatement(ifClassList.get(i));
                }

                return ifClassList.get(0);
            } else if (cf.getInteractionOperandKind() == InteractionOperandKind.loop) {
                if (cf.getCodeText().isEmpty()) {
                    // while文
                    While whileClass = new While();
                    whileClass.setCondition(cf.getInteractionOperandList().get(0).getGuard());
                    for (InteractionFragment interactionFragmentInAlt : cf.getInteractionOperandList().get(0).getInteractionFragmentList()) {
                        whileClass.addStatement(convertInteractionFragmentToBlockStatement(interactionFragmentInAlt));
                    }
                    return whileClass;
                } else {
                    // for文
                    For forClass = new For();
                    String[] forExpression = cf.getCodeText().split(";");
                    forClass.setForInit(forExpression[0]);
                    forClass.setExpression(forExpression[1]);
                    forClass.setForUpdate(forExpression[2]);
                    return forClass;
                }
            }
        } else {
            MessageOccurrenceSpecification message = interactionFragment.getMessage();
            if (message.getMessageType() == MessageType.Declaration) {
                LocalVariableDeclaration local = new LocalVariableDeclaration(new Type(message.getType().getName()), message.getName());
                if (message.getValue() != null) local.setValue(message.getValue());
                return local;
            } else if (message.getMessageType() == MessageType.Assignment) {
                Assignment assignment = new Assignment(message.getName());
                if (message.getValue() != null) assignment.setValue(message.getValue());
                return assignment;
            } else if (message.getMessageType() == MessageType.Method) {
                Pattern p = Pattern.compile(" ([^(]*)[(]");
                Matcher m = p.matcher(message.getName());
                Method method = new Method();
                method.setType(new Type(message.getType().getName()));
                if (!m.find()) {
                    method.setName(message.getName());
                } else {
                    method.setName(m.group(1));
                }

                if (!(message.getValue() == null || message.getValue().isEmpty())) {
                    String[] parameterNameList = message.getValue().split(",");
                    for (String parameterName : parameterNameList) {
                        // メソッド呼び出しの場合、messageが引数の型名を保存していない
                        method.addArgument(new Argument(new Type("Tmp"), parameterName));
                    }
                }
                return method;
            }
        }
        return null;
    }
}
