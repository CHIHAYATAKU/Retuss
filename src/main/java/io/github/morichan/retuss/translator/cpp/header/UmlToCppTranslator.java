package io.github.morichan.retuss.translator.cpp.header;

import io.github.morichan.retuss.model.CppModel;
import io.github.morichan.retuss.model.uml.cpp.CppHeaderClass;
import io.github.morichan.retuss.model.uml.cpp.utils.Modifier;
import io.github.morichan.retuss.translator.cpp.header.util.CppTypeMapper;
import io.github.morichan.retuss.translator.cpp.header.util.CppVisibilityMapper;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.visibility.Visibility;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if (modifiers.contains(Modifier.STATIC)) {
            builder.append("static ");
        }
        if (modifiers.contains(Modifier.READONLY)) {
            builder.append(Modifier.READONLY.getCppText(false) + " ");
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
            if (operation.getParameters() != null && !operation.getParameters().isEmpty()) {
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
                                .append(arraySize);
                    } else {
                        paramBuilder.append(baseType)
                                .append(" ")
                                .append(param.getName().getNameText());
                    }
                    params.add(paramBuilder.toString());
                }
            }
        } catch (IllegalStateException e) {
            System.out.println("No parameters for method: " + operation.getName().getNameText());
        }

        builder.append(String.join(", ", params));
        builder.append(")");

        // 修飾子の処理
        Set<Modifier> modifiers = cls.getModifiers(operation.getName().getNameText());
        if (modifiers.contains(Modifier.QUERY)) {
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

    public String addRealization(String existingCode, String derivedClassName, String interfaceName) {
        try {
            return addGeneralization(existingCode, derivedClassName, derivedClassName);
            // List<String> lines = new
            // ArrayList<>(Arrays.asList(existingCode.split("\n")));

            // // クラス定義行を見つける
            // int classDefLine = -1;
            // String className = ""; // クラス名は既存のコードから抽出
            // Pattern classPattern = Pattern.compile("class\\s+(\\w+)");

            // for (int i = 0; i < lines.size(); i++) {
            // Matcher matcher = classPattern.matcher(lines.get(i));
            // if (matcher.find()) {
            // className = matcher.group(1);
            // classDefLine = i;
            // break;
            // }
            // }

            // if (classDefLine == -1)
            // return existingCode;
            // // 継承関係の追加
            // String currentLine = lines.get(classDefLine);
            // if (currentLine.contains(":")) {
            // int colonPos = currentLine.indexOf(":");
            // String beforeColon = currentLine.substring(0, colonPos + 1);
            // String afterColon = currentLine.substring(colonPos + 1);
            // lines.set(classDefLine, beforeColon + " public " + interfaceName + "," +
            // afterColon);
            // } else {
            // lines.set(classDefLine, currentLine.replace("{", ": public " + interfaceName
            // + " {"));
            // }

            // // インターフェースのメソッドを取得して追加
            // Optional<CppHeaderClass> interfaceClass =
            // CppModel.getInstance().findClass(interfaceName);
            // if (interfaceClass.isPresent()) {
            // // publicセクションを探す
            // int publicSection = findInsertPositionForOperation(lines, Visibility.Public);

            // // 各メソッドを追加
            // for (Operation op : interfaceClass.get().getOperationList()) {
            // StringBuilder methodBuilder = new StringBuilder();
            // methodBuilder.append(" ").append(op.getReturnType()).append(" ");
            // methodBuilder.append(op.getName().getNameText()).append("(");

            // // パラメータリストの構築
            // List<String> params = new ArrayList<>();
            // for (Parameter param : op.getParameters()) {
            // params.add(param.getType() + " " + param.getName().getNameText());
            // }
            // methodBuilder.append(String.join(", ", params));
            // methodBuilder.append(") override;");

            // lines.add(publicSection + 1, methodBuilder.toString());
            // }
            // }

            // return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add realization: " + e.getMessage());
            return existingCode;
        }
    }

    private boolean hasInheritance(List<String> lines, String className, String baseClassName) {
        Pattern classPattern = Pattern.compile("class\\s+" + className + "\\s*:([^{]+)\\{");

        for (String line : lines) {
            Matcher matcher = classPattern.matcher(line);
            if (matcher.find()) {
                String inheritanceList = matcher.group(1);
                // public や private などの修飾子を含めてチェック
                if (inheritanceList.contains(baseClassName)) {
                    System.out.println("Found existing inheritance: " + inheritanceList.trim());
                    return true;
                }
            }
        }
        return false;
    }

    public String addGeneralization(String existingCode, String derivedClassName, String baseClassName) {
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

            // 既に継承関係が存在するかチェック
            if (hasInheritance(lines, derivedClassName, baseClassName)) {
                System.out.println("Inheritance already exists, skipping addition");
                return existingCode;
            }

            // String includeStatement = "#include \"" + baseClassName + ".h\"";
            // if (!existingCode.contains(includeStatement)) {
            // int includePos = findLastIncludePosition(lines);
            // lines.add(includePos + 1, includeStatement);
            // }

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

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add association: " + e.getMessage());
            return existingCode;
        }
    }

    public String addComposition(String existingCode, String componentName, String memberName, Visibility visibility) {
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));
            int insertPosition = findInsertPositionForAttribute(lines, visibility);

            // コンポジション用のメンバ変数を追加
            String declaration = "    " + componentName + " " + memberName + ";";
            lines.add(insertPosition + 1, declaration);

            return String.join("\n", lines);
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

            return String.join("\n", lines);
        } catch (Exception e) {
            System.err.println("Failed to add composition with annotation: " + e.getMessage());
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
            String declaration = "    " + componentName + "* " + memberName + ";";
            lines.add(insertPosition + 2, declaration);

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

    public String removeInheritance(String existingCode, String baseClassName) {
        System.out.println("Removing inheritance/realization for base class: " + baseClassName);
        try {
            List<String> lines = new ArrayList<>(Arrays.asList(existingCode.split("\n")));

            // クラス定義行を見つける
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("class ") && line.contains(": ")) {
                    System.out.println("Found class definition line: " + line);

                    // 継承リストを処理
                    int colonPos = line.indexOf(":");
                    String beforeColon = line.substring(0, colonPos);
                    String afterColon = line.substring(colonPos + 1);

                    // 継承リストをカンマで分割して処理
                    String[] inheritances = afterColon.split(",");
                    List<String> remainingInheritances = new ArrayList<>();

                    for (String inheritance : inheritances) {
                        String trimmed = inheritance.trim();
                        // public や private のスコープも含めてチェック
                        if (!trimmed.contains(baseClassName)) {
                            remainingInheritances.add(trimmed);
                        } else {
                            System.out.println("Removing inheritance: " + trimmed);
                        }
                    }

                    // 継承リストを再構築
                    if (remainingInheritances.isEmpty()) {
                        String newLine = beforeColon + " {";
                        System.out.println("Updated line (no inheritance): " + newLine);
                        lines.set(i, newLine);
                    } else {
                        String newLine = beforeColon + ": " + String.join(", ", remainingInheritances) + " {";
                        System.out.println("Updated line (with remaining inheritance): " + newLine);
                        lines.set(i, newLine);
                    }
                    break;
                }
            }

            // // インクルードの削除
            // // まずインクルードを見つける
            // for (int i = lines.size() - 1; i >= 0; i--) {
            // String line = lines.get(i).trim();
            // if (line.equals("#include \"" + baseClassName + ".h\"")) {
            // System.out.println("Removing include line: " + line);
            // lines.remove(i);
            // break;
            // }
            // }

            String result = String.join("\n", lines);
            System.out.println("Inheritance removal completed");
            return result;
        } catch (Exception e) {
            System.err.println("Failed to remove inheritance: " + e.getMessage());
            e.printStackTrace();
            return existingCode;
        }
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
}