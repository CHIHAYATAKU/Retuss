package io.github.morichan.retuss.translator;

import io.github.morichan.retuss.language.Model;
import io.github.morichan.retuss.language.java.Java;
import io.github.morichan.retuss.language.cpp.Cpp;
import io.github.morichan.retuss.language.uml.Package;

/**
 * <p> 翻訳者クラス </p>
 */
public class Translator {
    private Model model;
    private JavaTranslator javaTranslator;
    private CppTranslator cppTranslator;
    private UMLTranslator umlTranslator;

    public Translator(Model model) {
        this.model = model;
        this.javaTranslator = new JavaTranslator(model);
        this.cppTranslator = new CppTranslator(model);
        this.umlTranslator = new UMLTranslator(model);
    }

    /**
     * <p> UMLからJavaへの変換 </p>
     *
     * @param uml クラス図のパッケージ
     */
    public Java translateToJava(Package uml) {
        return javaTranslator.translate(uml);
    }

    /**
     * <p> UMLからC++への変換 </p>
     *
     * @param uml クラス図のパッケージ
     */
    public Cpp translateToCpp(Package uml) {
        return cppTranslator.translate(uml);
    }

    /**
     * <p> JavaからUMLへの変換 </p>
     *
     * @param java Javaソースコード
     */
    public Package translateToUML(Java java) {
        return umlTranslator.translate(java);
    }

    /**
     * <p> C++からUMLへの変換 </p>
     *
     * @param cpp C++ソースコード
     */
    public Package translateToUML(Cpp cpp) {
        return umlTranslator.translate(cpp);
    }
}
