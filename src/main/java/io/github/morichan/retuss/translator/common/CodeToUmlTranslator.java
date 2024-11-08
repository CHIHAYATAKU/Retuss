package io.github.morichan.retuss.translator.common;

import io.github.morichan.retuss.model.uml.Class;
import java.util.List;

public interface CodeToUmlTranslator {
    List<Class> translate(String code);
}