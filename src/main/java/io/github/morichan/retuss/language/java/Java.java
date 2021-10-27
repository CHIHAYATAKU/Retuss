package io.github.morichan.retuss.language.java;

import java.util.ArrayList;
import java.util.List;

/**
 * <p> Javaクラス </p>
 */
public class Java {

    private List<Class> classes = new ArrayList<>();

    /**
     * <p> 同名のクラスを上書きします。同名のクラスがない場合は、何もしない。 </p>
     */
    public void updateClass(Class javaClass) {
        for (int i=0; i<classes.size(); i++) {
            if (javaClass.getName().equals(classes.get(i).getName())) {
                classes.set(i, javaClass);
                break;
            }
        }
        // TODO: 変更対象クラスが見つからない場合の処理
    }

    /**
     * <p> クラスのリストにクラスを追加します </p>
     *
     * <p>
     * {@code null} を追加しようとしても反映しません。
     * </p>
     *
     * @param javaClass クラス <br> {@code null} 無視
     */
    public void addClass(Class javaClass) {
        if (javaClass != null) classes.add(javaClass);
    }

    /**
     * <p> クラスのリストを設定します </p>
     *
     * <p>
     * リスト内に {@code null} を含んでいた場合はその要素を無視します。
     * また、 {@code null} を設定しようとした場合はリストを空にします。
     * </p>
     *
     * @param classes クラスのリスト
     */
    public void setClasses(List<Class> classes) {
        if (classes != null) for (Class javaClass : classes) addClass(javaClass);
        else this.classes.clear();
    }

    /**
     * <p> クラスのリストを取得します </p>
     *
     * @return クラスのリスト <br> 要素数0の可能性あり
     */
    public List<Class> getClasses() {
        return classes;
    }

    /**
     * <p> クラスのリストを空にします </p>
     *
     * <p>
     * {@link #setClasses(List)} に{@code null} を設定しています。
     * </p>
     */
    public void emptyClasses() {
        setClasses(null);
    }
}
