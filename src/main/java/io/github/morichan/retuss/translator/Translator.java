package io.github.morichan.retuss.translator;

import io.github.morichan.retuss.language.java.Java;
import io.github.morichan.retuss.language.cpp.Cpp;
import io.github.morichan.retuss.language.uml.Package;

/**
 * <p> 翻訳者クラス </p>
 */
public class Translator {
    /**
     * <p> UMLからJavaへの変換 </p>
     *
     * @param uml クラス図のパッケージ
     */
    public Java translateToJava(Package uml) {
        JavaTranslator javaTranslator = new JavaTranslator();
        return javaTranslator.translate(uml);
    }

    /**
     * <p> UMLからC++への変換 </p>
     *
     * @param uml クラス図のパッケージ
     */
    public Cpp translateToCpp(Package uml) {
        CppTranslator cppTranslator = new CppTranslator();
        return cppTranslator.translate(uml);
    }

    /**
     * <p> JavaからUMLへの変換 </p>
     *
     * @param java Javaソースコード
     */
    public Package translateToUML(Java java) {
        UMLTranslator umlTranslator = new UMLTranslator();
        return umlTranslator.translate(java);
    }

    /**
     * <p> C++からUMLへの変換 </p>
     *
     * @param cpp C++ソースコード
     */
    public Package translateToUML(Cpp cpp) {
        UMLTranslator umlTranslator = new UMLTranslator();
        return umlTranslator.translate(cpp);
    }
}
