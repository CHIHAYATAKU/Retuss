package io.github.morichan.retuss.translator.cpp;

import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.model.uml.cpp.utils.Modifier;
import io.github.morichan.retuss.translator.common.UmlToCodeTranslator;
import io.github.morichan.retuss.translator.cpp.util.CppTypeMapper;
import io.github.morichan.retuss.translator.cpp.util.CppVisibilityMapper;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
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

    // private String translateAttribute(Attribute attribute, CppHeaderClass cls) {
    // StringBuilder builder = new StringBuilder();

    // // 修飾子の追加 (constは型の前、staticは最初)
    // Set<Modifier> modifiers =
    // cls.getModifiers(attribute.getName().getNameText());
    // if (modifiers.contains(Modifier.STATIC)) {
    // builder.append("static ");
    // }
    // if (modifiers.contains(Modifier.CONST)) {
    // builder.append("const ");
    // }

    // // 型の処理
    // String type = attribute.getType().toString();
    // String processedType = processType(type);
    // builder.append(processedType).append(" ");

    // // 名前
    // builder.append(attribute.getName().getNameText());

    // // 配列サイズの処理
    // if (type.matches(".*\\[\\d+\\]")) {
    // String size = type.replaceAll(".*\\[(\\d+)\\].*", "[$1]");
    // builder.append(size);
    // }

    // // 初期値の処理
    // try {
    // if (attribute.getDefaultValue() != null) {
    // String defaultValue = attribute.getDefaultValue().toString();
    // // 文字列の場合
    // if (processedType.contains("string") && !defaultValue.startsWith("\"")) {
    // defaultValue = "\"" + defaultValue + "\"";
    // }
    // builder.append(" = ").append(defaultValue);
    // }
    // } catch (IllegalStateException ignored) {
    // }

    // return builder.toString();
    // }
    private String translateAttribute(Attribute attribute, CppHeaderClass cls) {
        StringBuilder builder = new StringBuilder();

        // 修飾子の追加
        Set<Modifier> modifiers = cls.getModifiers(attribute.getName().getNameText());
        if (modifiers.contains(Modifier.STATIC)) {
            builder.append("static ");
        }
        if (modifiers.contains(Modifier.CONST)) {
            builder.append("const ");
        }
        if (modifiers.contains(Modifier.VOLATILE)) {
            builder.append("volatile ");
        }
        if (modifiers.contains(Modifier.MUTABLE)) {
            builder.append("mutable ");
        }

        // 型の処理
        String type = attribute.getType().toString();
        String processedType = processType(type);
        builder.append(processedType);

        // 名前の処理
        String name = attribute.getName().getNameText();
        builder.append(" ").append(name);

        // 配列サイズの処理
        if (type.contains("[")) {
            String arraySize = type.substring(type.indexOf("["));
            arraySize = arraySize.replaceAll("\\s+", "");
            builder.append(arraySize);
        }

        // デフォルト値の処理
        try {
            if (attribute.getDefaultValue() != null &&
                    !attribute.getDefaultValue().toString().isEmpty()) {
                builder.append(" = ").append(attribute.getDefaultValue());
            }
        } catch (IllegalStateException ignored) {
        }

        return builder.toString();
    }

    private String extractFullType(String originalStr) {
        // 可視性と名前を除いた型の部分を抽出
        String[] parts = originalStr.split(":");
        if (parts.length <= 1)
            return "";

        String typePart = parts[1].trim();

        // テンプレート部分を含めて抽出
        if (typePart.contains("<") && typePart.contains(">")) {
            int start = typePart.indexOf("<");
            int end = typePart.lastIndexOf(">");
            String baseType = typePart.substring(0, start).trim();
            String templatePart = typePart.substring(start, end + 1);
            return baseType + templatePart;
        }

        // 配列部分を含めて抽出
        if (typePart.contains("[")) {
            return typePart;
        }

        // 基本型の抽出
        return typePart.split("\\s+")[0];
    }

    private String extractPointerRef(String originalStr) {
        StringBuilder symbols = new StringBuilder();
        // *や&を探す
        for (char c : originalStr.toCharArray()) {
            if (c == '*' || c == '&') {
                symbols.append(c);
            }
        }
        return symbols.toString();
    }

    // private String processType(String fullType) {
    // System.out.println("=== Start Type Processing ===");
    // System.out.println("Input full type: " + fullType);
    // String result = "";

    // // テンプレート型の処理
    // if (fullType.contains("<") && fullType.contains(">")) {
    // String baseType = fullType.substring(0,
    // fullType.indexOf("<")).trim().toLowerCase();
    // String templatePart = fullType.substring(
    // fullType.indexOf("<"),
    // fullType.lastIndexOf(">") + 1);
    // System.out.println("Base type: " + baseType);
    // System.out.println("Template part: " + templatePart);

    // switch (baseType) {
    // case "vector":
    // case "set":
    // case "deque":
    // case "list":
    // case "stack":
    // case "queue":
    // result = "std::" + baseType + templatePart;
    // break;
    // case "map":
    // result = "std::map" + templatePart;
    // break;
    // case "shared_ptr":
    // case "unique_ptr":
    // case "weak_ptr":
    // result = "std::" + baseType + templatePart;
    // break;
    // case "array":
    // result = "std::array" + templatePart;
    // break;
    // default:
    // result = baseType + templatePart;
    // break;
    // }
    // }

    // if (fullType.contains("[")) {
    // int bracketIndex = fullType.indexOf("[");
    // result = fullType.substring(0, bracketIndex).trim();
    // System.out.println("Array type detected. Base type: " + result);
    // }

    // System.out.println("Final processed type: " + result);
    // System.out.println("=== End Type Processing ===");
    // return result;
    // }

    private String processType(String fullType) {
        System.out.println("=== Start Type Processing ===");
        System.out.println("Input full type: " + fullType);

        String result;

        // 基本型の変換
        switch (fullType.trim()) {
            case "String":
                result = "std::string";
                break;
            case "Integer":
                result = "int";
                break;
            case "Boolean":
                result = "bool";
                break;
            case "Float":
                result = "float";
                break;
            case "Double":
                result = "double";
                break;
            case "Byte":
                result = "char";
                break;
            case "Character":
                result = "char";
                break;
            default:
                // テンプレート型の処理
                if (fullType.contains("<") && fullType.contains(">")) {
                    String baseType = fullType.substring(0, fullType.indexOf("<")).trim().toLowerCase();
                    String templatePart = fullType.substring(
                            fullType.indexOf("<"),
                            fullType.lastIndexOf(">") + 1);
                    System.out.println("Base type: " + baseType);
                    System.out.println("Template part: " + templatePart);

                    if (isStlContainer(baseType)) {
                        result = "std::" + baseType + templatePart;
                    } else {
                        result = baseType + templatePart;
                    }
                }
                // 配列型の処理
                else if (fullType.contains("[")) {
                    int bracketIndex = fullType.indexOf("[");
                    result = fullType.substring(0, bracketIndex).trim();
                    System.out.println("Array type detected. Base type: " + result);
                }
                // その他の型
                else {
                    result = fullType;
                }
                break;
        }

        System.out.println("Final processed type: " + result);
        System.out.println("=== End Type Processing ===");
        return result;
    }

    private boolean isStlContainer(String type) {
        String lowerType = type.toLowerCase();
        return lowerType.equals("vector") ||
                lowerType.equals("set") ||
                lowerType.equals("map") ||
                lowerType.equals("array") ||
                lowerType.equals("deque") ||
                lowerType.equals("list") ||
                lowerType.equals("stack") ||
                lowerType.equals("queue") ||
                lowerType.equals("shared_ptr") ||
                lowerType.equals("unique_ptr") ||
                lowerType.equals("weak_ptr");
    }

    public String addOperation(String existingCode, CppHeaderClass cls, Operation operation) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            Visibility targetVisibility = operation.getVisibility();
            int insertPosition = findInsertPositionForOperation(lines, targetVisibility);

            if (insertPosition == -1) {
                // 適切なセクションが見つからない場合、新しいセクションを作成
                insertPosition = findClassEndPosition(lines) - 1;
                lines.add(insertPosition, "");
                lines.add(insertPosition, visibilityMapper.toSourceCode(targetVisibility) + ":");
                insertPosition++;
            }

            String operationDeclaration = "    " + translateOperation(operation, cls) + ";";
            lines.add(insertPosition + 1, operationDeclaration);

            // デバッグ出力
            System.out.println("Generated operation declaration: " + operationDeclaration);
            System.out.println("Inserting at position: " + insertPosition);

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
            e.printStackTrace();
            return existingCode; // エラー時は元のコードを返す
        }
    }

    private String translateOperation(Operation operation, CppHeaderClass cls) {
        StringBuilder builder = new StringBuilder();
        System.out.println("Translating operation: " + operation.toString());

        // メソッド名とパラメータを処理
        if (!operation.getName().getNameText().contains("~") &&
                !operation.getName().getNameText().equals(cls.getName())) {
            // 戻り値の型
            String returnType = processType(operation.getReturnType().toString());
            builder.append(returnType).append(" ");
        }

        // メソッド名
        builder.append(operation.getName().getNameText());

        // パラメータリスト
        builder.append("(");
        List<String> params = new ArrayList<>();
        try {
            for (Parameter param : operation.getParameters()) {
                StringBuilder paramBuilder = new StringBuilder();
                String paramType = param.getType().toString();
                String baseType = processType(paramType);

                // 配列の処理
                if (paramType.contains("[")) {
                    String arraySize = paramType.substring(paramType.indexOf("["));
                    paramBuilder.append(baseType)
                            .append(" ")
                            .append(param.getName().getNameText())
                            .append(arraySize); // 配列サイズを名前の後ろに追加
                } else {
                    paramBuilder.append(baseType)
                            .append(" ")
                            .append(param.getName().getNameText());
                }
                params.add(paramBuilder.toString());
            }
        } catch (IllegalStateException e) {
            System.out.println("No parameters found or parameters not initialized");
        }

        builder.append(String.join(", ", params));
        builder.append(")");

        // 修飾子の処理
        Set<Modifier> modifiers = cls.getModifiers(operation.getName().getNameText());
        if (modifiers.contains(Modifier.CONST)) {
            builder.append(" const");
        }
        if (modifiers.contains(Modifier.OVERRIDE)) {
            builder.append(" override");
        }
        if (modifiers.contains(Modifier.ABSTRACT)) {
            builder.append(" = 0");
        }

        System.out.println("Translated operation: " + builder.toString());
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
        int classEnd = findClassEndPosition(lines);
        int lastVisibilityPos = -1;
        int currentSectionEnd = -1;
        int appropriatePos = -1;

        // クラス内を前から探索
        for (int i = 0; i < classEnd; i++) {
            String line = lines.get(i).trim();

            // 可視性セクションの開始を見つけた
            if (line.matches("^(public|protected|private):.*")) {
                if (line.equals(visibilityStr)) {
                    lastVisibilityPos = i;
                    currentSectionEnd = i;
                    continue;
                }
                // 異なる可視性セクションを見つけた
                if (lastVisibilityPos != -1) {
                    break;
                }
            }

            // 現在のセクション内の最後の要素を追跡
            if (lastVisibilityPos != -1 && !line.isEmpty() && !line.startsWith("//")) {
                currentSectionEnd = i;
            }
        }

        // 適切な挿入位置の決定
        if (lastVisibilityPos != -1) {
            // 既存のセクションの最後に追加
            appropriatePos = currentSectionEnd;
        } else {
            // 新しいセクションを作成（クラスの終わりの前）
            appropriatePos = classEnd - 1;
            lines.add(appropriatePos, "");
            lines.add(appropriatePos, visibilityStr);
            appropriatePos++;
        }

        return appropriatePos;
    }

    private int findInsertPositionForOperation(List<String> lines, Visibility visibility) {
        String visibilityStr = visibilityMapper.toSourceCode(visibility) + ":";
        int sectionStart = -1;
        int insertPos = -1;

        // 可視性セクションを探す
        for (int i = 0; i < findClassEndPosition(lines); i++) {
            String line = lines.get(i).trim();
            if (line.equals(visibilityStr)) {
                sectionStart = i;
                insertPos = i;
            } else if (sectionStart != -1) {
                // 次の可視性セクションが始まったら終了
                if (line.matches("^(public|protected|private):.*")) {
                    break;
                }
                // コメントでない行なら、それをinsertPosとする
                if (!line.isEmpty() && !line.startsWith("//")) {
                    insertPos = i;
                }
            }
        }

        // 可視性セクションが見つからない場合は作成
        if (sectionStart == -1) {
            // クラスの終わりの前に挿入
            int classEnd = findClassEndPosition(lines);
            sectionStart = classEnd;
            lines.add(sectionStart, "");
            lines.add(sectionStart, visibilityStr);
            insertPos = sectionStart;
        }

        return insertPos;
    }

    private int findClassEndPosition(List<String> lines) {
        // 後ろから検索してクラスの終了位置を見つける
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).trim().equals("};")) {
                return i;
            }
        }
        return lines.size() - 1;
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