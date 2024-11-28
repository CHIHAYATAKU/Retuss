package io.github.morichan.retuss.translator.cpp;

import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.model.uml.cpp.utils.Modifier;
import io.github.morichan.retuss.translator.common.UmlToCodeTranslator;
import io.github.morichan.retuss.translator.cpp.util.CppTypeMapper;
import io.github.morichan.retuss.translator.cpp.util.CppVisibilityMapper;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UmlToCppTranslator {
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;

    public UmlToCppTranslator() {
        this.typeMapper = new CppTypeMapper();
        this.visibilityMapper = new CppVisibilityMapper();
    }

    public String addAttribute(String existingCode, CppHeaderClass cls, Attribute attribute) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            Visibility targetVisibility = attribute.getVisibility();
            int insertPosition = findInsertPositionForAttribute(lines, targetVisibility);

            if (insertPosition == -1) {
                insertPosition = findClassEndPosition(lines) - 1;
                lines.add(insertPosition, "");
                lines.add(insertPosition, visibilityMapper.toSourceCode(targetVisibility) + ":");
                insertPosition++;
            }

            String attributeDeclaration = "    " + translateAttribute(attribute, cls) + ";";
            lines.add(insertPosition + 1, attributeDeclaration);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add attribute: " + e.getMessage());
            return existingCode;
        }
    }

    private String translateAttribute(Attribute attribute, CppHeaderClass cls) {
        StringBuilder builder = new StringBuilder();

        // 修飾子の追加
        Set<Modifier> modifiers = cls.getModifiers(attribute.getName().getNameText());
        for (Modifier modifier : modifiers) {
            builder.append(modifier.getCppText(false)).append(" ");
        }

        // 型と名前
        String type = typeMapper.mapType(attribute.getType().toString());
        builder.append(type).append(" ").append(attribute.getName().getNameText());

        // 初期値の処理
        try {
            if (attribute.getDefaultValue() != null) {
                String defaultValue = attribute.getDefaultValue().toString();
                // 文字列の場合
                if (type.contains("string") && !defaultValue.startsWith("\"")) {
                    defaultValue = "\"" + defaultValue + "\"";
                }
                builder.append(" = ").append(defaultValue);
            }
        } catch (IllegalStateException ignored) {
        }

        return builder.toString();
    }

    public String addOperation(String existingCode, CppHeaderClass cls, Operation operation) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            addRequiredIncludes(lines, operation.getReturnType().toString());
            for (io.github.morichan.fescue.feature.parameter.Parameter param : operation.getParameters()) {
                addRequiredIncludes(lines, param.getType().toString());
            }

            Visibility targetVisibility = operation.getVisibility();
            int insertPosition = findInsertPositionForOperation(lines, targetVisibility);

            if (insertPosition == -1) {
                insertPosition = findClassEndPosition(lines) - 1;
                lines.add(insertPosition, "");
                lines.add(insertPosition, visibilityMapper.toSourceCode(targetVisibility) + ":");
                insertPosition++;
            }

            String operationDeclaration = "    " + translateOperation(operation, cls) + ";";
            lines.add(insertPosition + 1, operationDeclaration);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
            return existingCode;
        }
    }

    private String translateOperation(Operation operation, CppHeaderClass cls) {
        StringBuilder builder = new StringBuilder();

        Set<Modifier> modifiers = cls.getModifiers(operation.getName().getNameText());

        // virtual修飾子
        if (cls.getAbstruct() || modifiers.contains(Modifier.VIRTUAL) ||
                modifiers.contains(Modifier.OVERRIDE)) {
            builder.append("virtual ");
        }

        // static修飾子
        if (modifiers.contains(Modifier.STATIC)) {
            builder.append("static ");
        }

        // 戻り値の型とメソッド名
        if (!operation.getName().getNameText().contains("~") &&
                !operation.getName().getNameText().equals(cls.getName())) {
            String returnType = typeMapper.mapType(operation.getReturnType().toString());
            builder.append(returnType).append(" ");
        }
        builder.append(operation.getName().getNameText())
                .append("(");

        // パラメータ
        List<String> params = new ArrayList<>();
        for (io.github.morichan.fescue.feature.parameter.Parameter param : operation.getParameters()) {
            String paramType = typeMapper.mapType(param.getType().toString());
            // デフォルト値の処理
            String paramStr = paramType + " " + param.getName().getNameText();
            if (param.getDefaultValue() != null) {
                paramStr += " = " + param.getDefaultValue();
            }
            params.add(paramStr);
        }
        builder.append(String.join(", ", params)).append(")");

        // const修飾子
        if (modifiers.contains(Modifier.CONST)) {
            builder.append(" const");
        }

        // 純粋仮想関数
        if (modifiers.contains(Modifier.ABSTRACT)) {
            builder.append(" = 0");
        }

        // override指定子
        if (modifiers.contains(Modifier.OVERRIDE)) {
            builder.append(" override");
        }

        return builder.toString();
    }

    public String addInheritance(String existingCode, String derivedClassName, String baseClassName) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));

            int classDefLine = -1;
            Pattern classDefPattern = Pattern.compile("class\\s+" + derivedClassName + "\\s*(?::|\\{)");

            for (int i = 0; i < lines.size(); i++) {
                if (classDefPattern.matcher(lines.get(i)).find()) {
                    classDefLine = i;
                    break;
                }
            }

            if (classDefLine == -1)
                return existingCode;

            String includeStatement = "#include \"" + baseClassName + ".h\"";
            if (!existingCode.contains(includeStatement)) {
                int includePos = findLastIncludePosition(lines);
                lines.add(includePos + 1, includeStatement);
            }

            String currentLine = lines.get(classDefLine);
            if (currentLine.contains(":")) {
                int colonPos = currentLine.indexOf(":");
                String beforeColon = currentLine.substring(0, colonPos + 1);
                String afterColon = currentLine.substring(colonPos + 1);
                lines.set(classDefLine, beforeColon + " public " + baseClassName + "," + afterColon);
            } else {
                String newLine = currentLine.replace("{", ": public " + baseClassName + " {");
                lines.set(classDefLine, newLine);
            }

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add inheritance: " + e.getMessage());
            return existingCode;
        }
    }

    public String addAssociation(String existingCode, String targetClassName,
            String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            String declaration = "    " + targetClassName + "* " + memberName + ";";
            lines.add(insertPosition + 1, declaration);

            addRequiredIncludes(lines, targetClassName + ".h");
            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add association: " + e.getMessage());
            return existingCode;
        }
    }

    public String addComposition(String existingCode, String componentName, String memberName, Visibility visibility) {
        try {
            // コンポジション用の属性作成（メンバ変数として保持）
            Attribute attribute = new Attribute(new Name(memberName));
            attribute.setType(new Type(componentName));
            attribute.setVisibility(visibility);

            // インクルードの追加とコードへの反映
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            addRequiredIncludes(lines, componentName);
            String code = String.join("\n", lines);

            return addAttribute(code, null, attribute);
        } catch (Exception e) {
            System.err.println("Failed to add composition: " + e.getMessage());
            return existingCode;
        }
    }

    public String addCompositionWithAnnotation(String existingCode, String componentName,
            String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            // アノテーションの追加
            lines.add(insertPosition + 1, "    // @relationship composition");
            // メンバ変数の追加
            String declaration = "    " + componentName + "* " + memberName + ";";
            lines.add(insertPosition + 2, declaration);

            addRequiredIncludes(lines, componentName + ".h");
            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add composition with annotation: " + e.getMessage());
            return existingCode;
        }
    }

    public String addAggregation(String existingCode, String componentName,
            String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            String declaration = "    std::shared_ptr<" + componentName + "> " + memberName + ";";
            lines.add(insertPosition + 1, declaration);

            addRequiredIncludes(lines, componentName + ".h");
            lines.add(0, "#include <memory>");
            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add aggregation: " + e.getMessage());
            return existingCode;
        }
    }

    public String addAggregationWithAnnotation(String existingCode, String componentName,
            String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            // アノテーションの追加
            lines.add(insertPosition + 1, "    // @relationship aggregation");
            // メンバ変数の追加（shared_ptr使用）
            String declaration = "    std::shared_ptr<" + componentName + "> " + memberName + ";";
            lines.add(insertPosition + 2, declaration);

            addRequiredIncludes(lines, componentName + ".h");
            lines.add(0, "#include <memory>"); // shared_ptr用
            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add aggregation with annotation: " + e.getMessage());
            return existingCode;
        }
    }

    public String removeOperation(String existingCode, Operation operation) {
        List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
        String opName = operation.getName().getNameText();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.contains(opName) && line.contains("(") && line.endsWith(";")) {
                lines.remove(i);
                break;
            }
        }
        return String.join("\n", lines);
    }

    public String removeAttribute(String existingCode, Attribute attribute) {
        List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
        String attrName = attribute.getName().getNameText();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.contains(attrName) && line.endsWith(";") && !line.contains("(")) {
                lines.remove(i);
                break;
            }
        }
        return String.join("\n", lines);
    }

    public String addRealization(String existingCode, String interfaceName) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));

            // クラス定義行を見つける
            Pattern classDefPattern = Pattern.compile("class\\s+\\w+\\s*(?::|\\{)");
            int classDefLine = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (classDefPattern.matcher(lines.get(i)).find()) {
                    classDefLine = i;
                    break;
                }
            }

            if (classDefLine == -1)
                return existingCode;

            // インクルードの追加
            addRequiredIncludes(lines, interfaceName + ".h");

            // 継承関係の追加
            String currentLine = lines.get(classDefLine);
            if (currentLine.contains(":")) {
                int colonPos = currentLine.indexOf(":");
                String beforeColon = currentLine.substring(0, colonPos + 1);
                String afterColon = currentLine.substring(colonPos + 1);
                lines.set(classDefLine, beforeColon + " public " + interfaceName + ", " + afterColon);
            } else {
                String newLine = currentLine.replace("{", ": public " + interfaceName + " {");
                lines.set(classDefLine, newLine);
            }

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add realization: " + e.getMessage());
            return existingCode;
        }
    }

    // このクラスで使用する補助メソッド
    private void addRequiredIncludes(List<String> lines, String type) {
        Set<String> includes = new HashSet<>();

        // コレクション型のインクルード
        if (type.contains("vector"))
            includes.add("<vector>");
        if (type.contains("array"))
            includes.add("<array>");
        if (type.contains("shared_ptr") || type.contains("unique_ptr"))
            includes.add("<memory>");

        // ユーザー定義型のインクルード（テンプレート引数内も考慮）
        Pattern classPattern = Pattern.compile("\\b([A-Z]\\w*)\\b");
        Matcher matcher = classPattern.matcher(type);
        while (matcher.find()) {
            String className = matcher.group(1);
            if (!isBuiltInType(className)) {
                includes.add("\"" + className + ".h\"");
            }
        }

        // インクルードの追加
        int insertPos = findLastIncludePosition(lines);
        for (String include : includes) {
            if (!containsInclude(lines, include)) {
                lines.add(insertPos + 1, "#include " + include);
                insertPos++;
            }
        }
    }

    private boolean isBuiltInType(String type) {
        return Arrays.asList("int", "char", "bool", "float", "double", "void", "long")
                .contains(type.toLowerCase());
    }

    private boolean containsInclude(List<String> lines, String include) {
        return lines.stream().anyMatch(line -> line.trim().equals(include));
    }

    private int findInsertPositionForAttribute(List<String> lines, Visibility visibility) {
        String visibilityStr = visibilityMapper.toSourceCode(visibility) + ":";
        boolean inTargetSection = false;
        int lastAttributePos = -1;
        int methodStartPos = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.equals(visibilityStr)) {
                inTargetSection = true;
                lastAttributePos = i;
            } else if (inTargetSection) {
                if (line.matches("^(public|protected|private):.*")) {
                    break;
                }
                if (line.contains("(")) { // メソッド定義開始
                    methodStartPos = i;
                    break;
                }
                if (!line.isEmpty() && !line.startsWith("//") && !line.contains("(")) {
                    lastAttributePos = i;
                }
            }
        }

        // メソッドの前に挿入
        return methodStartPos != -1 ? methodStartPos - 1 : lastAttributePos;
    }

    private int findInsertPositionForOperation(List<String> lines, Visibility visibility) {
        String visibilityStr = visibilityMapper.toSourceCode(visibility) + ":";
        boolean inTargetSection = false;
        int lastOperationPos = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.equals(visibilityStr)) {
                inTargetSection = true;
                lastOperationPos = i;
            } else if (inTargetSection) {
                if (line.matches("^(public|protected|private):.*")) {
                    break;
                }
                if (!line.isEmpty() && !line.startsWith("//")) {
                    lastOperationPos = i;
                }
            }
        }

        return lastOperationPos;
    }

    private int findLastIncludePosition(List<String> lines) {
        int lastInclude = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("#include")) {
                lastInclude = i;
            }
        }
        return lastInclude;
    }

    private int findClassEndPosition(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).trim().equals("};")) {
                return i;
            }
        }
        return lines.size() - 1;
    }

    private String processCollectionType(String type, String memberName) {
        // コレクション型のパターンマッチ
        Pattern vectorPattern = Pattern.compile("vector<(.+)>");
        Pattern arrayPattern = Pattern.compile("array<(.+),\\s*(\\d+)>");
        Pattern smartPtrPattern = Pattern.compile("(shared_ptr|unique_ptr)<(.+)>");

        Matcher vectorMatcher = vectorPattern.matcher(type);
        Matcher arrayMatcher = arrayPattern.matcher(type);

        if (vectorMatcher.find()) {
            String innerType = vectorMatcher.group(1).trim();
            // ポインタ型のコレクション
            if (innerType.endsWith("*")) {
                String baseType = innerType.substring(0, innerType.length() - 1).trim();
                return "std::vector<" + baseType + "*> " + memberName;
            }
            // スマートポインタのコレクション
            Matcher ptrMatcher = smartPtrPattern.matcher(innerType);
            if (ptrMatcher.find()) {
                String ptrType = ptrMatcher.group(1);
                String baseType = ptrMatcher.group(2);
                return "std::vector<std::" + ptrType + "<" + baseType + ">> " + memberName;
            }
            // 値型のコレクション
            return "std::vector<" + innerType + "> " + memberName;
        } else if (arrayMatcher.find()) {
            String innerType = arrayMatcher.group(1).trim();
            String size = arrayMatcher.group(2);
            // 配列サイズ付きの宣言
            return "std::array<" + innerType + ", " + size + "> " + memberName;
        }
        return type + " " + memberName;
    }
}