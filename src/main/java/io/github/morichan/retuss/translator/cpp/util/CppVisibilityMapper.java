package io.github.morichan.retuss.translator.cpp.util;

import io.github.morichan.fescue.feature.visibility.Visibility;

public class CppVisibilityMapper {
    public String toSourceCode(Visibility visibility) {
        if (visibility == null)
            return "private";

        switch (visibility.toString().toUpperCase()) {
            case "PUBLIC":
                return "public";
            case "PROTECTED":
                return "protected";
            case "PRIVATE":
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