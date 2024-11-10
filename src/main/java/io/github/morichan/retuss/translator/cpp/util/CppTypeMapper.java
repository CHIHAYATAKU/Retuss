package io.github.morichan.retuss.translator.cpp.util;

import java.util.HashMap;
import java.util.Map;

public class CppTypeMapper {
    private final Map<String, String> typeMap;

    public CppTypeMapper() {
        typeMap = new HashMap<>();
        initializeTypeMap();
    }

    private void initializeTypeMap() {
        typeMap.put("int", "int");
        typeMap.put("double", "double");
        typeMap.put("float", "float");
        typeMap.put("string", "std::string");
        typeMap.put("boolean", "bool");
        typeMap.put("void", "void");
        typeMap.put("char", "char");
        typeMap.put("long", "long");
    }

    public String mapType(String umlType) {
        return typeMap.getOrDefault(umlType, umlType);
    }
}