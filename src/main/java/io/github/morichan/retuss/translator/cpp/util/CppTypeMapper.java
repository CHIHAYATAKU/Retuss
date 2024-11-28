package io.github.morichan.retuss.translator.cpp.util;

import java.util.HashMap;
import java.util.Map;

public class CppTypeMapper {
    private final Map<String, String> typeMap;

    public CppTypeMapper() {
        typeMap = new HashMap<>();
        typeMap.put("int", "int");
        typeMap.put("float", "float");
        typeMap.put("double", "double");
        typeMap.put("boolean", "bool");
        typeMap.put("String", "std::string");
        typeMap.put("void", "void");
        typeMap.put("long", "long");
        typeMap.put("char", "char");
        typeMap.put("byte", "unsigned char");
        typeMap.put("short", "short");
    }

    public String mapType(String umlType) {
        if (umlType == null || umlType.isEmpty())
            return "void";

        String baseType = umlType.replaceAll("<.*>", "").trim();
        if (typeMap.containsKey(baseType))
            return typeMap.get(baseType);

        if (umlType.contains("<"))
            return handleTemplateType(umlType);

        return umlType;
    }

    private String handleTemplateType(String type) {
        if (type.startsWith("List<")) {
            String innerType = type.replaceAll("List<(.*)>", "$1");
            return "std::vector<" + mapType(innerType) + ">";
        }
        if (type.startsWith("Set<")) {
            String innerType = type.replaceAll("Set<(.*)>", "$1");
            return "std::set<" + mapType(innerType) + ">";
        }
        if (type.startsWith("Map<")) {
            String[] innerTypes = type.replaceAll("Map<(.*)>", "$1").split(",");
            return "std::map<" + mapType(innerTypes[0].trim()) + ", " +
                    mapType(innerTypes[1].trim()) + ">";
        }
        return type;
    }
}