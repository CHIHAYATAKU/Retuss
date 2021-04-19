package io.github.morichan.retuss.language;

import io.github.morichan.retuss.language.cpp.Cpp;
import io.github.morichan.retuss.language.java.Java;
import io.github.morichan.retuss.language.uml.Package;

/**
 * <p> RETUSSの内部データクラス </p>
 *
 * <p>
 *     RETUSSの内部データであるJava、C++、UMLの保管クラスを持ちます。
 * </p>
 */
public class Model {
    private Java java = new Java();
    private Cpp cpp = new Cpp();
    private Package uml = new Package();

    public void setJava(Java java) {
        this.java = java;
    }

    public void setUml(Package uml) {
        this.uml = uml;
    }

    public void setCpp(Cpp cpp) {
        this.cpp = cpp;
    }

    public Java getJava() {
        return java;
    }

    public Package getUml() {
        return uml;
    }

    public Cpp getCpp() {
        return cpp;
    }
}
