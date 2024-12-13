package io.github.morichan.retuss.translator.cpp.header.util;

import io.github.morichan.fescue.feature.visibility.Visibility;

public class CppVisibilityMapper {
    public String toSourceCode(Visibility visibility) {
        switch (visibility) {
            case Public:
                return "public";
            case Protected:
                return "protected";
            case Private:
                return "private";
            default:
                return "private";
        }
    }

    public String toSymbol(Visibility visibility) {
        switch (visibility.toString().toUpperCase()) {
            case "PUBLIC":
                return "+";
            case "PROTECTED":
                return "#";
            case "PRIVATE":
                return "-";
            default:
                return "-";
        }
    }
}