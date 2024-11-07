package io.github.morichan.retuss.translator.common;

import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;

import java.util.List;

public abstract class AbstractTranslator {
    /**
     * ソースコードをUMLクラスのリストに変換する
     *
     * @param code ソースコード文字列
     * @return UMLクラスのリスト
     */
    public abstract List<Class> translateCodeToUml(String code);

    /**
     * UMLクラスのリストをソースコードに変換する
     *
     * @param classList UMLクラスのリスト
     * @return 言語固有の構文木（戻り値の型は実装クラスで指定）
     */
    public abstract Object translateUmlToCode(List<Class> classList);

    /**
     * 属性をソースコードの形式に変換する
     *
     * @param attribute UML属性
     * @return 言語固有の属性表現
     */
    public abstract Object translateAttribute(Attribute attribute);

    /**
     * 操作をソースコードの形式に変換する
     *
     * @param operation UML操作
     * @return 言語固有のメソッド表現
     */
    public abstract Object translateOperation(Operation operation);

    /**
     * アクセス修飾子をソースコードの形式に変換する
     *
     * @param visibility UMLの可視性
     * @return 言語固有のアクセス修飾子表現
     */
    protected abstract Object toSourceCodeVisibility(Visibility visibility);

    /**
     * UMLの型を言語固有の型に変換する
     *
     * @param umlType UMLの型
     * @return 言語固有の型表現
     */
    protected abstract Object toSourceCodeType(Type umlType);
}