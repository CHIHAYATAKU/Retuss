package io.github.morichan.retuss.model;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.translator.cpp.CppTranslator;

import java.util.*;

public class CppModel {
    private static final CppModel model = new CppModel();
    private final Map<String, CppFile> headerFiles = new HashMap<>();
    private final Map<String, CppFile> implFiles = new HashMap<>();
    private final CppTranslator translator;
    private CodeController codeController;
    private UmlController umlController;
    private final List<ModelChangeListener> changeListeners = new ArrayList<>();

    private CppModel() {
        this.translator = createTranslator();
    }

    private CppTranslator createTranslator() {
        return new CppTranslator();
    }

    public static CppModel getInstance() {
        return model;
    }

    public void setCodeController(CodeController controller) {
        this.codeController = controller;
    }

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
        for (CppFile file : headerFiles.values()) {
            file.setUmlController(controller);
        }
        for (CppFile file : implFiles.values()) {
            file.setUmlController(controller);
        }
    }

    public void addNewFile(String fileName) {
        String baseName = fileName.replaceAll("\\.(h|hpp|cpp)$", "");
        createCppFiles(baseName, Optional.empty());
    }

    public void addNewFileFromUml(CppHeaderClass headerClass) {
        createCppFiles(headerClass.getName(), Optional.of(headerClass));
    }

    private void createCppFiles(String baseName, Optional<CppHeaderClass> headerClass) {
        // ヘッダーファイル作成
        CppFile headerFile = new CppFile(baseName + ".h", true);
        if (headerClass.isPresent()) {
            String headerCode = headerFile.getCode();
            headerFile.updateCode(headerCode);
        }

        CppFile implFile = new CppFile(baseName + ".cpp", false);

        if (umlController != null) {
            headerFile.setUmlController(umlController);
            implFile.setUmlController(umlController);
        }

        headerFile.addChangeListener(new CppFile.FileChangeListener() {
            @Override
            public void onFileChanged(CppFile file) {
                updateImplFileForHeaderChange(file);
                notifyModelChanged();
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                String oldBaseName = oldName.replace(".h", "");
                String newBaseName = newName.replace(".h", "");

                // ヘッダーファイル更新
                String headerCode = headerFile.getCode();
                headerCode = headerCode
                        .replace(oldBaseName.toUpperCase() + "_H", newBaseName.toUpperCase() + "_H")
                        .replaceAll("(?<![a-zA-Z0-9_])" + oldBaseName + "(\\s*\\([^)]*\\)\\s*;)", newBaseName + "$1")
                        .replaceAll("(?<![a-zA-Z0-9_])~" + oldBaseName + "(\\s*\\([^)]*\\)\\s*;)",
                                "~" + newBaseName + "$1");
                headerFile.updateCode(headerCode);

                CppFile implFile = implFiles.get(oldBaseName);
                if (implFile != null) {
                    implFile.updateFileName(newBaseName + ".cpp");

                    String implCode = implFile.getCode();
                    implCode = implCode
                            .replace("#include \"" + oldName + "\"", "#include \"" + newName + "\"")
                            .replace(oldBaseName + "::", newBaseName + "::")
                            .replaceAll("(?<![a-zA-Z0-9_])" + oldBaseName + "(\\s*\\([^)]*\\)\\s*\\{)",
                                    newBaseName + "$1")
                            .replaceAll("(?<![a-zA-Z0-9_])~" + oldBaseName + "(\\s*\\([^)]*\\)\\s*\\{)",
                                    "~" + newBaseName + "$1");

                    implFile.updateCode(implCode);
                }
            }
        });

        headerFiles.put(baseName, headerFile);
        implFiles.put(baseName, implFile);

        if (codeController != null) {
            codeController.updateCodeTab(headerFile);
            codeController.updateCodeTab(implFile);
        }
        if (umlController != null) {
            umlController.updateDiagram(headerFile);
        }
    }

    public void updateCode(CppFile file, String code) {
        String baseName = file.getBaseName();

        if (file.isHeader()) {
            updateHeaderFile(file, code, baseName);
            // // 図の更新をトリガー
            if (umlController != null) {
                umlController.updateDiagram(file);
            }
        } else {
            // updateImplementationFile(file, code);
            // CppFile headerFile = headerFiles.get(baseName);
            // if (headerFile != null && !headerFile.getHeaderClasses().isEmpty()) {
            // // analyzeImplementationRelationships(headerFile, file);
            // // 図の更新をトリガー
            // if (umlController != null) {
            // umlController.updateDiagram(headerFile);
            // }
            // }
        }

        notifyModelChanged();
    }

    private void updateHeaderFile(CppFile headerFile, String code, String baseName) {
        System.out.println("DEBUG: Updating header file");
        System.out.println("DEBUG: Current classes before update: " + headerFiles.size());
        // 古いクラス名を保存
        String oldClassName = headerFile.getFileName().replace(".h", "");

        // コードを更新
        headerFile.updateCode(code);

        List<CppHeaderClass> classes = headerFile.getHeaderClasses();
        System.out.println("DEBUG: Classes after parsing: " + (classes != null ? classes.size() : "null"));
        for (CppHeaderClass cls : classes) {
            System.out.println("DEBUG: Class: " + cls.getName());
            System.out.println("DEBUG: Operations: " + cls.getOperationList().size());
        }

        // 新しいクラス名を取得
        Optional<String> newClassName = translator.extractClassName(code);
        if (newClassName.isPresent()) {
            String newName = newClassName.get();
            System.out.println("DEBUG: Class name change detected: " + oldClassName +
                    " -> " + newName);

            if (!newName.equals(oldClassName)) {
                // マップの更新
                headerFiles.remove(oldClassName);
                headerFiles.put(newName, headerFile);

                // 実装ファイルの更新
                updateImplFileForClassNameChange(oldClassName, newName);

                System.out.println("DEBUG: 実装ファイル更新あと！");

                // コントローラーに通知（既存の処理を維持）
                if (codeController != null) {
                    codeController.updateCodeTab(headerFile);
                }
            }
        }
        // // 実装ファイルの関係解析
        // CppFile implFile = implFiles.get(baseName);
        // if (implFile != null) {
        // analyzeImplementationRelationships(headerFile, implFile);
        // }
    }

    private void updateImplFileForClassNameChange(String oldClassName, String newClassName) {
        CppFile implFile = implFiles.remove(oldClassName);
        if (implFile != null) {
            String newImplName = newClassName + ".cpp";
            String oldImplName = implFile.getFileName();

            // ヘッダーファイルのインクルードを更新
            String currentCode = implFile.getCode();
            String oldInclude = "#include \"" + oldClassName + ".h\"";
            String newInclude = "#include \"" + newClassName + ".h\"";
            String newCode = currentCode.replace(oldInclude, newInclude);

            // ファイル名と内容を更新
            implFile.updateFileName(newImplName);
            implFile.updateCode(newCode);
            implFiles.put(newClassName, implFile);

            // コントローラーに通知
            if (codeController != null) {
                codeController.updateCodeTab(implFile);
            }

            System.out.println("DEBUG: Updated implementation file name from " +
                    oldImplName + " to " + newImplName);
        }
    }

    private void updateImplementationFile(CppFile implFile, String code) {
        String baseName = implFile.getBaseName();
        CppFile headerFile = headerFiles.get(baseName);

        implFile.updateCode(code);

        // ヘッダーファイルが存在する場合は関係を解析
        if (headerFile != null && !headerFile.getHeaderClasses().isEmpty()) {
            // analyzeImplementationRelationships(headerFile, implFile);
        }

        // 既存の通知処理を維持
        if (codeController != null) {
            codeController.updateCodeTab(implFile);
        }
    }

    // private void analyzeImplementationRelationships(CppFile headerFile, CppFile
    // implFile) {
    // if (!headerFile.getHeaderClasses().isEmpty()) {
    // Class umlClass = headerFile.getUmlClassList().get(0);
    // try {
    // CharStream input = CharStreams.fromString(implFile.getCode());
    // CPP14Lexer lexer = new CPP14Lexer(input);
    // CommonTokenStream tokens = new CommonTokenStream(lexer);
    // CPP14Parser parser = new CPP14Parser(tokens);

    // CppMethodAnalyzer analyzer = new CppMethodAnalyzer(umlClass);
    // ParseTreeWalker walker = new ParseTreeWalker();
    // walker.walk(analyzer, parser.translationUnit());

    // System.out.println("DEBUG: Analyzed implementation relationships for " +
    // implFile.getFileName());
    // } catch (Exception e) {
    // System.err.println("Error analyzing implementation relationships: " +
    // e.getMessage());
    // }
    // }
    // }

    public void addAttribute(String className, Attribute attribute) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty())
            return;

        CppFile headerFile = headerFileOpt.get();
        try {
            if (!headerFile.getHeaderClasses().isEmpty()) {
                CppHeaderClass targetClass = headerFile.getHeaderClasses().get(0);

                String currentCode = headerFile.getCode();
                String newCode = translator.addAttribute(currentCode, targetClass, attribute);
                headerFile.updateCode(newCode);

                if (umlController != null) {
                    umlController.updateDiagram(headerFile);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to add attribute: " + e.getMessage());
        }
    }

    public void addOperation(String className, Operation operation) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty())
            return;

        try {
            CppFile headerFile = headerFileOpt.get();
            CppHeaderClass targetClass = headerFile.getHeaderClasses().get(0);

            String currentCode = headerFile.getCode();
            String newCode = addRequiredIncludes(currentCode, operation.getReturnType().toString());

            // パラメータの型のインクルードを追加（null チェック付き）
            List<Parameter> parameters = new ArrayList<>();
            try {
                parameters = operation.getParameters();
            } catch (IllegalStateException e) {
                System.out.println("No parameters found, using empty list");
            }

            for (Parameter param : parameters) {
                newCode = addRequiredIncludes(newCode, param.getType().toString());
            }

            System.err.println("トランスレータ―まえ: \n" + newCode);
            newCode = translator.addOperation(newCode, targetClass, operation);
            System.err.println("トランスレータ―あと: \n" + newCode);
            headerFile.updateCode(newCode);
            notifyModelChanged();
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void delete(String className) {
        System.out.println("Attempting to delete class: " + className);

        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty()) {
            System.out.println("Header file not found for class: " + className);
            return;
        }

        try {
            System.out.println("Found header file, proceeding with deletion");
            CppFile headerFile = headerFileOpt.get();

            // ヘッダーファイルのクラスリストをチェック
            System.out.println("Header classes count: " + headerFile.getHeaderClasses().size());

            if (!headerFile.getHeaderClasses().isEmpty()) {
                System.out.println("Removing class from header file");
                headerFile.removeClass(headerFile.getHeaderClasses().get(0));
            }

            // 実装ファイルの削除
            CppFile implFile = findImplFile(className);
            if (implFile != null) {
                System.out.println("Removing implementation file");
                implFiles.remove(className);
            }

            // ヘッダーファイルの削除
            System.out.println("Removing header file");
            headerFiles.remove(className);

            // コントローラーへの通知
            if (umlController != null) {
                System.out.println("Notifying UML controller");
                umlController.onClassDeleted(className);
            }
            if (codeController != null) {
                System.out.println("Notifying code controller");
                codeController.onClassDeleted(className);
            }

            System.out.println("Notifying model change");
            notifyModelChanged();

        } catch (Exception e) {
            System.err.println("Error during class deletion:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
            throw e; // エラーを再スローして上位で処理できるようにする
        }
    }

    public void delete(String className, Attribute attribute) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty())
            return;

        try {
            CppFile headerFile = headerFileOpt.get();
            CppHeaderClass targetClass = headerFile.getHeaderClasses().get(0);

            // データ構造から属性を削除
            targetClass.removeAttribute(attribute);

            // コードから属性を削除
            String currentCode = headerFile.getCode();
            List<String> lines = new ArrayList<>(Arrays.asList(currentCode.split("\n")));
            String attrName = attribute.getName().getNameText();

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.contains(attrName) && line.endsWith(";") && !line.contains("(")) {
                    lines.remove(i);
                    if (i > 0 && lines.get(i - 1).trim().startsWith("//")) {
                        // 関連するコメント（アノテーション）も削除
                        lines.remove(i - 1);
                    }
                    break;
                }
            }

            headerFile.updateCode(String.join("\n", lines));
        } catch (Exception e) {
            System.err.println("Failed to delete attribute: " + e.getMessage());
        }
    }

    public void delete(String className, Operation operation) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty())
            return;

        try {
            CppFile headerFile = headerFileOpt.get();
            CppHeaderClass targetClass = headerFile.getHeaderClasses().get(0);

            // データ構造から操作を削除
            targetClass.removeOperation(operation);

            // コードから操作を削除
            String currentCode = headerFile.getCode();
            List<String> lines = new ArrayList<>(Arrays.asList(currentCode.split("\n")));
            String opName = operation.getName().getNameText();

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.contains(opName) && line.contains("(") && line.endsWith(";")) {
                    lines.remove(i);
                    break;
                }
            }

            headerFile.updateCode(String.join("\n", lines));
        } catch (Exception e) {
            System.err.println("Failed to delete operation: " + e.getMessage());
        }
    }

    private String addRequiredIncludes(String code, String type) {
        if (type == null || type.isEmpty())
            return code;

        StringBuilder sb = new StringBuilder(code);
        int insertPos = findIncludeInsertPosition(code);

        // STL
        if (type.contains("string"))
            addInclude(sb, insertPos, "<string>");
        if (type.contains("vector"))
            addInclude(sb, insertPos, "<vector>");
        if (type.contains("map"))
            addInclude(sb, insertPos, "<map>");
        if (type.contains("set"))
            addInclude(sb, insertPos, "<set>");
        if (type.contains("shared_ptr"))
            addInclude(sb, insertPos, "<memory>");

        // ユーザー定義型
        String baseType = type.replaceAll("<.*>", "").trim();
        if (findHeaderFileByClassName(baseType).isPresent()) {
            addInclude(sb, insertPos, "\"" + baseType + ".h\"");
        }

        return sb.toString();
    }

    private void addInclude(StringBuilder code, int position, String include) {
        String includeStatement = "#include " + include + "\n";
        if (!code.toString().contains(includeStatement)) {
            code.insert(position, includeStatement);
        }
    }

    public void addInheritance(String derivedClassName, String baseClassName) {
        Optional<CppFile> derivedFileOpt = findHeaderFileByClassName(derivedClassName);
        Optional<CppFile> baseFileOpt = findHeaderFileByClassName(baseClassName);

        if (derivedFileOpt.isEmpty() || baseFileOpt.isEmpty())
            return;

        try {
            CppFile derivedFile = derivedFileOpt.get();
            CppHeaderClass derivedClass = derivedFile.getHeaderClasses().get(0);
            CppHeaderClass baseClass = baseFileOpt.get().getHeaderClasses().get(0);

            derivedClass.setSuperClass(baseClass);
            String newCode = translator.addInheritance(derivedFile.getCode(), derivedClassName, baseClassName);
            derivedFile.updateCode(newCode);

            if (umlController != null) {
                umlController.updateDiagram(derivedFile);
            }
        } catch (Exception e) {
            System.err.println("Failed to add inheritance: " + e.getMessage());
        }
    }

    public void addComposition(String ownerClassName, String componentClassName, Visibility visibility) {
        Optional<CppFile> ownerFileOpt = findHeaderFileByClassName(ownerClassName);
        if (ownerFileOpt.isEmpty())
            return;

        try {
            CppFile ownerFile = ownerFileOpt.get();
            CppHeaderClass ownerClass = ownerFile.getHeaderClasses().get(0);

            String memberName = componentClassName.toLowerCase();
            String newCode = translator.addComposition(
                    ownerFile.getCode(),
                    componentClassName,
                    memberName,
                    visibility);

            ownerClass.getRelationshipManager().addComposition(
                    componentClassName,
                    memberName,
                    "1",
                    visibility);

            ownerFile.updateCode(newCode);
            if (umlController != null) {
                umlController.updateDiagram(ownerFile);
            }
        } catch (Exception e) {
            System.err.println("Failed to add composition: " + e.getMessage());
        }
    }

    public void addCompositionWithAnnotation(String ownerClassName, String componentClassName, Visibility visibility) {
        Optional<CppFile> ownerFileOpt = findHeaderFileByClassName(ownerClassName);
        if (ownerFileOpt.isEmpty())
            return;

        try {
            CppFile ownerFile = ownerFileOpt.get();
            CppHeaderClass ownerClass = ownerFile.getHeaderClasses().get(0);

            String memberName = componentClassName.toLowerCase() + "Ptr";
            String newCode = translator.addCompositionWithAnnotation(
                    ownerFile.getCode(),
                    componentClassName,
                    memberName,
                    visibility);

            ownerClass.getRelationshipManager().addComposition(
                    componentClassName,
                    memberName,
                    "1",
                    visibility);

            ownerFile.updateCode(newCode);
            if (umlController != null) {
                umlController.updateDiagram(ownerFile);
            }
        } catch (Exception e) {
            System.err.println("Failed to add annotated composition: " + e.getMessage());
        }
    }

    public void addAggregation(String ownerClassName, String componentClassName, Visibility visibility) {
        Optional<CppFile> ownerFileOpt = findHeaderFileByClassName(ownerClassName);
        if (ownerFileOpt.isEmpty())
            return;

        try {
            CppFile ownerFile = ownerFileOpt.get();
            CppHeaderClass ownerClass = ownerFile.getHeaderClasses().get(0);

            String memberName = componentClassName.toLowerCase() + "Ptr";
            String newCode = translator.addAggregation(
                    ownerFile.getCode(),
                    componentClassName,
                    memberName,
                    visibility);

            ownerClass.getRelationshipManager().addAggregation(
                    componentClassName,
                    memberName,
                    "1",
                    visibility);

            ownerFile.updateCode(newCode);
            if (umlController != null) {
                umlController.updateDiagram(ownerFile);
            }
        } catch (Exception e) {
            System.err.println("Failed to add aggregation: " + e.getMessage());
        }
    }

    public void addAggregationWithAnnotation(String ownerClassName, String componentClassName, Visibility visibility) {
        Optional<CppFile> ownerFileOpt = findHeaderFileByClassName(ownerClassName);
        if (ownerFileOpt.isEmpty())
            return;

        try {
            CppFile ownerFile = ownerFileOpt.get();
            CppHeaderClass ownerClass = ownerFile.getHeaderClasses().get(0);

            String memberName = componentClassName.toLowerCase() + "Ptr";
            String newCode = translator.addAggregationWithAnnotation(
                    ownerFile.getCode(),
                    componentClassName,
                    memberName,
                    visibility);

            ownerClass.getRelationshipManager().addAggregation(
                    componentClassName,
                    memberName,
                    "1",
                    visibility);

            ownerFile.updateCode(newCode);
            if (umlController != null) {
                umlController.updateDiagram(ownerFile);
            }
        } catch (Exception e) {
            System.err.println("Failed to add annotated aggregation: " + e.getMessage());
        }
    }

    public void addAssociation(String sourceClassName, String targetClassName, Visibility visibility) {
        Optional<CppFile> sourceFileOpt = findHeaderFileByClassName(sourceClassName);
        if (sourceFileOpt.isEmpty())
            return;

        try {
            CppFile sourceFile = sourceFileOpt.get();
            CppHeaderClass sourceClass = sourceFile.getHeaderClasses().get(0);

            String memberName = targetClassName.toLowerCase() + "Ptr";
            String newCode = translator.addAssociation(
                    sourceFile.getCode(),
                    targetClassName,
                    memberName,
                    visibility);

            sourceClass.getRelationshipManager().addAssociation(
                    targetClassName,
                    memberName,
                    "1",
                    visibility);

            sourceFile.updateCode(newCode);
            if (umlController != null) {
                umlController.updateDiagram(sourceFile);
            }
        } catch (Exception e) {
            System.err.println("Failed to add association: " + e.getMessage());
        }
    }

    public void addRealization(String sourceClassName, String interfaceName) {
        Optional<CppFile> sourceFileOpt = findHeaderFileByClassName(sourceClassName);
        if (sourceFileOpt.isEmpty())
            return;

        try {
            CppFile sourceFile = sourceFileOpt.get();
            CppHeaderClass sourceClass = sourceFile.getHeaderClasses().get(0);

            String newCode = translator.addRealization(
                    sourceFile.getCode(),
                    interfaceName);

            sourceClass.getRelationshipManager().addRealization(interfaceName);

            sourceFile.updateCode(newCode);
            if (umlController != null) {
                umlController.updateDiagram(sourceFile);
            }
        } catch (Exception e) {
            System.err.println("Failed to add realization: " + e.getMessage());
        }
    }

    private int findIncludeInsertPosition(String code) {
        int lastInclude = code.lastIndexOf("#include");
        if (lastInclude == -1)
            return 0;

        int endOfLine = code.indexOf('\n', lastInclude);
        return endOfLine == -1 ? code.length() : endOfLine + 1;
    }
    // public void addAttribute(String className, Attribute attribute) {
    // CppFile headerFile = headerFiles.get(className);
    // if (headerFile == null)
    // return;

    // try {
    // // 型に基づいて必要なヘッダーを追加
    // addRequiredIncludes(headerFile, attribute.getType().toString());

    // // UMLクラスに属性を追加
    // List<Class> umlClasses = headerFile.getUmlClassList();
    // if (!umlClasses.isEmpty()) {
    // Class targetClass = umlClasses.get(0);
    // targetClass.addAttribute(attribute);

    // // ヘッダーファイルを更新
    // String headerCode =
    // translator.translateUmlToCode(Collections.singletonList(targetClass));
    // headerFile.updateCode(headerCode);

    // if (umlController != null) {
    // umlController.updateDiagram(headerFile);
    // }
    // if (codeController != null) {
    // codeController.updateCodeTab(headerFile);
    // }
    // }
    // } catch (Exception e) {
    // System.err.println("Failed to add attribute: " + e.getMessage());
    // }
    // }

    // public void addOperation(String className, Operation operation) {
    // CppFile headerFile = headerFiles.get(className);
    // CppFile implFile = implFiles.get(className);
    // if (headerFile == null || implFile == null)
    // return;

    // try {
    // // UMLクラスに操作を追加
    // List<Class> umlClasses = headerFile.getUmlClassList();
    // if (!umlClasses.isEmpty()) {
    // Class targetClass = umlClasses.get(0);
    // targetClass.addOperation(operation);

    // // ヘッダーファイルを更新
    // String headerCode =
    // translator.translateUmlToCode(Collections.singletonList(targetClass));
    // headerFile.updateCode(headerCode);

    // // 実装ファイルにメソッドの骨組みを追加
    // StringBuilder implCodeBuilder = new StringBuilder();
    // implCodeBuilder.append(toSourceCodeType(operation.getReturnType()))
    // .append(" ")
    // .append(targetClass.getName())
    // .append("::")
    // .append(operation.getName())
    // .append("(");

    // // パラメータの追加
    // List<String> params = new ArrayList<>();
    // operation.getParameters().forEach(param -> {
    // params.add(String.format("%s %s",
    // toSourceCodeType(param.getType()),
    // param.getName()));
    // });
    // implCodeBuilder.append(String.join(", ", params))
    // .append(") {\n // TODO: Implement this method\n}\n\n");

    // String currentImplCode = implFile.getCode();
    // implFile.updateCode(currentImplCode + implCodeBuilder.toString());

    // // コントローラーに通知
    // if (umlController != null) {
    // umlController.updateDiagram(headerFile);
    // }
    // if (codeController != null) {
    // codeController.updateCodeTab(headerFile);
    // codeController.updateCodeTab(implFile);
    // }
    // }
    // } catch (Exception e) {
    // System.err.println("Failed to add operation: " + e.getMessage());
    // }
    // }

    private void updateImplFileForHeaderChange(CppFile headerFile) {
        String baseName = getBaseName(headerFile.getFileName());
        CppFile implFile = implFiles.get(baseName);
        if (implFile != null) {
            System.out.println("DEBUG: Updating implementation file for header change: " +
                    headerFile.getFileName());

            String currentCode = implFile.getCode();
            String expectedInclude = "#include \"" + headerFile.getFileName() + "\"";
            String oldIncludePattern = "#include \".*?.h\"";
            String newCode = currentCode;

            if (!currentCode.contains(expectedInclude)) {
                if (currentCode.matches("(?s).*" + oldIncludePattern + ".*")) {
                    newCode = currentCode.replaceFirst(oldIncludePattern, expectedInclude);
                } else {
                    newCode = expectedInclude + "\n\n" + currentCode;
                }
                implFile.updateCode(newCode);
            }
        }
    }

    public List<CppHeaderClass> getHeaderClasses() {
        List<CppHeaderClass> allClasses = new ArrayList<>();
        for (CppFile headerFile : headerFiles.values()) {
            allClasses.addAll(headerFile.getHeaderClasses());
        }
        System.out.println("CppModel classes: " + allClasses.size());
        for (CppHeaderClass cls : allClasses) {
            CppHeaderClass cppHeaderClass = cls;
            System.out.println("  Class: " + cppHeaderClass.getName());
            System.out.println("  Attributes: " + cls.getAttributeList());
            System.out.println("  Operations: " + cls.getOperationList());
            System.out.println("  Relations: ");
            for (RelationshipInfo relation : cppHeaderClass.getRelationships()) {
                System.out.println("    " + relation.getTargetClass() +
                        " (" + relation.getType() + ")");
                for (RelationshipElement elem : relation.getElements()) {
                    System.out.println("      - " + elem.getName() +
                            " [" + elem.getMultiplicity() + "]");
                }
            }
        }
        return Collections.unmodifiableList(allClasses);
    }

    // public void addGeneralization(String className, String superClassName) {
    // CppFile childFile = headerFiles.get(getBaseName(className));
    // CppFile parentFile = headerFiles.get(getBaseName(superClassName));
    // if (childFile == null || parentFile == null)
    // return;

    // try {
    // List<Class> childClasses = childFile.getUmlClassList();
    // List<Class> parentClasses = parentFile.getUmlClassList();

    // if (!childClasses.isEmpty() && !parentClasses.isEmpty()) {
    // // 継承関係を設定
    // Class childClass = childClasses.get(0);
    // childClass.setSuperClass(parentClasses.get(0));

    // // 親クラスのヘッダーをインクルード
    // String includeStatement = "#include \"" + superClassName + ".h\"";
    // String currentCode = childFile.getCode();
    // if (!currentCode.contains(includeStatement)) {
    // int insertPos = currentCode.indexOf("\n\nclass");
    // if (insertPos != -1) {
    // String newCode = currentCode.substring(0, insertPos) +
    // "\n" + includeStatement +
    // currentCode.substring(insertPos);
    // childFile.updateCode(newCode);
    // }
    // }

    // // 更新されたUMLクラスリストからコードを生成
    // String headerCode =
    // translator.translateUmlToCode(Collections.singletonList(childClass));
    // childFile.updateCode(headerCode);

    // if (umlController != null) {
    // umlController.updateDiagram(childFile);
    // }
    // if (codeController != null) {
    // codeController.updateCodeTab(childFile);
    // }
    // }
    // } catch (Exception e) {
    // System.err.println("Failed to add generalization: " + e.getMessage());
    // }
    // }

    // private void addRequiredIncludes(CppFile file, String type) {
    // Map<String, String> standardIncludes = Map.of(
    // "string", "<string>",
    // "vector", "<vector>",
    // "map", "<map>",
    // "set", "<set>");

    // // テンプレート型の解析（例：vector<string>）
    // if (type.contains("<")) {
    // String baseType = type.substring(0, type.indexOf("<"));
    // if (standardIncludes.containsKey(baseType.toLowerCase())) {
    // addInclude(file, standardIncludes.get(baseType.toLowerCase()));
    // }
    // // 内部の型も再帰的にチェック
    // String innerType = type.substring(type.indexOf("<") + 1,
    // type.lastIndexOf(">"));
    // addRequiredIncludes(file, innerType);
    // } else {
    // if (standardIncludes.containsKey(type.toLowerCase())) {
    // addInclude(file, standardIncludes.get(type.toLowerCase()));
    // }
    // }
    // }

    // private void addInclude(CppFile file, String include) {
    // String currentCode = file.getCode();
    // String includeStatement = "#include " + include;
    // if (!currentCode.contains(includeStatement)) {
    // int insertPos = currentCode.indexOf("\n\nclass");
    // if (insertPos != -1) {
    // String newCode = currentCode.substring(0, insertPos) +
    // "\n" + includeStatement +
    // currentCode.substring(insertPos);
    // file.updateCode(newCode);
    // }
    // }
    // }

    public interface ModelChangeListener {
        void onModelChanged();
    }

    public void addChangeListener(ModelChangeListener listener) {
        changeListeners.add(listener);
    }

    private void notifyModelChanged() {
        for (ModelChangeListener listener : changeListeners) {
            try {
                listener.onModelChanged();
            } catch (Exception e) {
                System.err.println("Error notifying model change: " + e.getMessage());
            }
        }
    }

    // private String toSourceCodeType(Type type) {
    // return translator.translateType(type);
    // }

    public void updateCodeFile(CppFile changedCodeFile, String code) {
        try {
            CppFile file = changedCodeFile;
            System.out.println("DEBUG: Updating code for file: " + file.getFileName());

            updateCode(file, code);

            // ヘッダーファイルでない場合、対応するヘッダーファイルの関係を再解析
            // if (!file.isHeader()) {
            // String baseName = file.getBaseName();
            // CppFile headerFile = headerFiles.get(baseName);
            // if (headerFile != null && !headerFile.getHeaderClasses().isEmpty()) {
            // // 実装ファイルからの関係を解析
            // // analyzeImplementationRelationships(headerFile, file);

            // // 図の更新をトリガー
            // if (umlController != null) {
            // umlController.updateDiagram(headerFile);
            // }
            // }
            // }

        } catch (Exception e) {
            System.err.println("Failed to update code: " + e.getMessage());
        }
    }

    public Optional<CppHeaderClass> findClass(String className) {
        String baseName = getBaseName(className);
        CppFile headerFile = headerFiles.get(baseName);
        if (headerFile != null) {
            List<CppHeaderClass> classes = headerFile.getHeaderClasses();
            if (!classes.isEmpty()) {
                return Optional.of(classes.get(0));
            }
        }
        return Optional.empty();
    }

    public Optional<CppFile> findHeaderFileByClassName(String className) {
        String baseName = getBaseName(className);
        return Optional.ofNullable(headerFiles.get(baseName));
    }

    private String getBaseName(String fileName) {
        return fileName.replaceAll("\\.(h|hpp|cpp)$", "");
    }

    /**
     * 指定されたクラスのメソッドのシーケンス図を生成
     */
    // public String generateSequenceDiagram(String className, String methodName) {
    // Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
    // Optional<CppFile> implFileOpt = Optional.ofNullable(findImplFile(className));

    // if (headerFileOpt.isPresent() && implFileOpt.isPresent()) {
    // CppFile headerFile = headerFileOpt.get();
    // CppFile implFile = implFileOpt.get();

    // // デバッグ出力
    // System.out.println("Generating sequence diagram for " + className + "::" +
    // methodName);
    // System.out.println("Header file: " + headerFile.getFileName());
    // System.out.println("Implementation file: " + implFile.getFileName());

    // return translator.generateSequenceDiagram(
    // headerFile.getCode(),
    // implFile.getCode(),
    // methodName);
    // }

    // System.err.println("Could not find necessary files for " + className);
    // return "";
    // }

    /**
     * 実装ファイルを取得する
     *
     * @param className 拡張子を除いたファイル名
     * @return 実装ファイル、存在しない場合はnull
     */
    public CppFile findImplFile(String className) {
        return implFiles.get(className);
    }

    /**
     * ヘッダーファイルを取得する
     *
     * @param baseName 拡張子を除いたファイル名
     * @return ヘッダーファイル、存在しない場合はnull
     */
    public CppFile findHeaderFile(String baseName) {
        return headerFiles.get(baseName);
    }

}