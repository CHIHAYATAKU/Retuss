package io.github.morichan.retuss.model;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.common.FileChangeListener;
import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.parser.cpp.CPP14Lexer;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.CppTranslator;
import io.github.morichan.retuss.translator.cpp.listeners.CppImplementationAnalyzer;
import io.github.morichan.retuss.translator.cpp.listeners.CppMethodAnalyzer;

import java.util.*;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

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

        // 既存のファイルチェック
        if (headerFiles.containsKey(baseName) || implFiles.containsKey(baseName)) {
            System.out.println("DEBUG: Files for " + baseName + " already exist");
            return;
        }

        System.out.println("DEBUG: Creating new files for " + baseName);

        // ファイルの作成
        final CppFile headerFile = new CppFile(baseName + ".h", true);
        final CppFile implFile = new CppFile(baseName + ".cpp", false);

        if (umlController != null) {
            headerFile.setUmlController(umlController);
            implFile.setUmlController(umlController);
        }

        // ヘッダーファイルの変更監視
        headerFile.addChangeListener(new CppFile.FileChangeListener() {
            @Override
            public void onFileChanged(CppFile file) {
                System.out.println("DEBUG: Header file changed: " + file.getFileName());
                updateImplFileForHeaderChange(file);
                notifyModelChanged();
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                // ... 名前変更の処理 ...
            }
        });

        // ファイルの登録
        headerFiles.put(baseName, headerFile);
        implFiles.put(baseName, implFile);

        // 初期化
        headerFile.updateCode(generateHeaderTemplate(baseName));
        implFile.updateCode(generateImplTemplate(baseName));

        // コントローラーに通知
        if (codeController != null) {
            codeController.updateCodeTab(headerFile);
            codeController.updateCodeTab(implFile);
        }
        if (umlController != null) {
            umlController.updateDiagram(headerFile);
        }
    }

    private String generateHeaderTemplate(String className) {
        StringBuilder sb = new StringBuilder();
        String guardName = className.toUpperCase() + "_H";

        sb.append("#ifndef ").append(guardName).append("\n");
        sb.append("#define ").append(guardName).append("\n\n");
        sb.append("class ").append(className).append(" {\n");
        sb.append("public:\n");
        sb.append("    ").append(className).append("();\n");
        sb.append("    virtual ~").append(className).append("();\n");
        sb.append("\nprotected:\n");
        sb.append("\nprivate:\n");
        sb.append("};\n\n");
        sb.append("#endif // ").append(guardName).append("\n");

        return sb.toString();
    }

    private String generateImplTemplate(String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("#include \"").append(className).append(".h\"\n\n");
        sb.append(className).append("::").append(className).append("() {\n}\n\n");
        sb.append(className).append("::~").append(className).append("() {\n}\n");
        return sb.toString();
    }

    public void addNewUmlClass(Class umlClass) {
        String baseName = umlClass.getName();
        CppFile headerFile = new CppFile(baseName + ".h", true);
        CppFile implFile = new CppFile(baseName + ".cpp", false);

        // UMLクラスからコードを生成
        // 修正前: String headerCode =
        // translator.translateUmlToCode(Collections.singletonList(umlClass));
        String headerCode = translator.translateUmlToCode(Collections.singletonList(umlClass));
        headerFile.updateCode(headerCode);

        // ファイルの登録
        headerFiles.put(baseName, headerFile);
        implFiles.put(baseName, implFile);

        notifyModelChanged();

        // コントローラーに通知
        if (umlController != null) {
            umlController.updateDiagram(headerFile);
        }
        if (codeController != null) {
            codeController.updateCodeTab(headerFile);
            codeController.updateCodeTab(implFile);
        }
    }

    public void updateCode(CppFile file, String code) {
        String baseName = file.getBaseName();

        if (file.isHeader()) {
            updateHeaderFile(file, code, baseName);
        } else {
            updateImplementationFile(file, code);
        }

        notifyModelChanged();
    }

    private void updateHeaderFile(CppFile headerFile, String code, String baseName) {
        // 古いクラス名を保存
        String oldClassName = headerFile.getFileName().replace(".h", "");

        // コードを更新
        headerFile.updateCode(code);

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

                // コントローラーに通知（既存の処理を維持）
                if (codeController != null) {
                    codeController.updateCodeTab(headerFile);
                }
            }
        }
        // 実装ファイルの関係解析
        CppFile implFile = implFiles.get(baseName);
        if (implFile != null) {
            analyzeImplementationRelationships(headerFile, implFile);
        }
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
        if (headerFile != null && !headerFile.getUmlClassList().isEmpty()) {
            analyzeImplementationRelationships(headerFile, implFile);
        }

        // 既存の通知処理を維持
        if (codeController != null) {
            codeController.updateCodeTab(implFile);
        }
    }

    private void analyzeImplementationRelationships(CppFile headerFile, CppFile implFile) {
        if (!headerFile.getUmlClassList().isEmpty()) {
            Class umlClass = headerFile.getUmlClassList().get(0);
            try {
                CppMethodAnalyzer analyzer = new CppMethodAnalyzer(umlClass);
                CharStream input = CharStreams.fromString(implFile.getCode());
                CPP14Lexer lexer = new CPP14Lexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                CPP14Parser parser = new CPP14Parser(tokens);
                ParseTreeWalker.DEFAULT.walk(analyzer, parser.translationUnit());

                System.out.println("DEBUG: Analyzed relationships in implementation file: " +
                        implFile.getFileName());
            } catch (Exception e) {
                System.err.println("Error analyzing implementation relationships: " + e.getMessage());
            }
        }
    }

    private void updateImplFileName(CppFile implFile, String newName) {
        System.out.println("DEBUG: Updating implementation file name to " + newName);
        String currentCode = implFile.getCode();
        String oldHeaderName = implFile.getFileName().replace(".cpp", ".h");
        String newHeaderName = newName.replace(".cpp", ".h");

        // インクルード文の更新
        String oldInclude = "#include \"" + oldHeaderName + "\"";
        String newInclude = "#include \"" + newHeaderName + "\"";
        String newCode = currentCode.replace(oldInclude, newInclude);

        // ファイル名も更新
        implFile.updateFileName(newName);
        implFile.updateCode(newCode);
    }

    public void addAttribute(String className, Attribute attribute) {
        CppFile headerFile = headerFiles.get(className);
        if (headerFile == null)
            return;

        try {
            // 型に基づいて必要なヘッダーを追加
            addRequiredIncludes(headerFile, attribute.getType().toString());

            // UMLクラスに属性を追加
            List<Class> umlClasses = headerFile.getUmlClassList();
            if (!umlClasses.isEmpty()) {
                Class targetClass = umlClasses.get(0);
                targetClass.addAttribute(attribute);

                // ヘッダーファイルを更新
                String headerCode = translator.translateUmlToCode(Collections.singletonList(targetClass));
                headerFile.updateCode(headerCode);

                if (umlController != null) {
                    umlController.updateDiagram(headerFile);
                }
                if (codeController != null) {
                    codeController.updateCodeTab(headerFile);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to add attribute: " + e.getMessage());
        }
    }

    public void addOperation(String className, Operation operation) {
        CppFile headerFile = headerFiles.get(className);
        CppFile implFile = implFiles.get(className);
        if (headerFile == null || implFile == null)
            return;

        try {
            // UMLクラスに操作を追加
            List<Class> umlClasses = headerFile.getUmlClassList();
            if (!umlClasses.isEmpty()) {
                Class targetClass = umlClasses.get(0);
                targetClass.addOperation(operation);

                // ヘッダーファイルを更新
                String headerCode = translator.translateUmlToCode(Collections.singletonList(targetClass));
                headerFile.updateCode(headerCode);

                // 実装ファイルにメソッドの骨組みを追加
                StringBuilder implCodeBuilder = new StringBuilder();
                implCodeBuilder.append(toSourceCodeType(operation.getReturnType()))
                        .append(" ")
                        .append(targetClass.getName())
                        .append("::")
                        .append(operation.getName())
                        .append("(");

                // パラメータの追加
                List<String> params = new ArrayList<>();
                operation.getParameters().forEach(param -> {
                    params.add(String.format("%s %s",
                            toSourceCodeType(param.getType()),
                            param.getName()));
                });
                implCodeBuilder.append(String.join(", ", params))
                        .append(") {\n    // TODO: Implement this method\n}\n\n");

                String currentImplCode = implFile.getCode();
                implFile.updateCode(currentImplCode + implCodeBuilder.toString());

                // コントローラーに通知
                if (umlController != null) {
                    umlController.updateDiagram(headerFile);
                }
                if (codeController != null) {
                    codeController.updateCodeTab(headerFile);
                    codeController.updateCodeTab(implFile);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
        }
    }

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

    private void updateImplFileContent(CppFile implFile, String newCode) {
        String baseName = getBaseName(implFile.getFileName());
        CppFile existingImpl = implFiles.get(baseName);
        if (existingImpl != null) {
            System.out.println("DEBUG: Updating implementation file content for: " +
                    implFile.getFileName());

            // ヘッダーファイルのインクルードを保持
            String headerInclude = "";
            String currentCode = existingImpl.getCode();
            String includePattern = "#include \".*?.h\"";
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(includePattern).matcher(currentCode);
            if (matcher.find()) {
                headerInclude = matcher.group() + "\n\n";
            }

            // 新しいコードの先頭にインクルード文を追加
            if (!newCode.contains(headerInclude.trim())) {
                newCode = headerInclude + newCode;
            }

            existingImpl.updateCode(newCode);
        }
    }

    public List<Class> getUmlClassList() {
        List<Class> allClasses = new ArrayList<>();
        for (CppFile headerFile : headerFiles.values()) {
            allClasses.addAll(headerFile.getUmlClassList());
        }
        System.out.println("CppModel classes: " + allClasses.size());
        for (Class cls : allClasses) {
            if (cls instanceof CppClass) {
                CppClass cppClass = (CppClass) cls;
                System.out.println("  Class: " + cppClass.getName());
                System.out.println("  Attributes: " + cls.getAttributeList());
                System.out.println("  Operations: " + cls.getOperationList());
                System.out.println("  Dependencies: " + cppClass.getDependencies());
                System.out.println("  Compositions: " + cppClass.getCompositions());
                System.out.println("  Multiplicities: ");
                for (String comp : cppClass.getCompositions()) {
                    System.out.println("    " + comp + ": " + cppClass.getMultiplicity(comp));
                }
            }
        }
        return Collections.unmodifiableList(allClasses);
    }

    public void addGeneralization(String className, String superClassName) {
        CppFile childFile = headerFiles.get(getBaseName(className));
        CppFile parentFile = headerFiles.get(getBaseName(superClassName));
        if (childFile == null || parentFile == null)
            return;

        try {
            List<Class> childClasses = childFile.getUmlClassList();
            List<Class> parentClasses = parentFile.getUmlClassList();

            if (!childClasses.isEmpty() && !parentClasses.isEmpty()) {
                // 継承関係を設定
                Class childClass = childClasses.get(0);
                childClass.setSuperClass(parentClasses.get(0));

                // 親クラスのヘッダーをインクルード
                String includeStatement = "#include \"" + superClassName + ".h\"";
                String currentCode = childFile.getCode();
                if (!currentCode.contains(includeStatement)) {
                    int insertPos = currentCode.indexOf("\n\nclass");
                    if (insertPos != -1) {
                        String newCode = currentCode.substring(0, insertPos) +
                                "\n" + includeStatement +
                                currentCode.substring(insertPos);
                        childFile.updateCode(newCode);
                    }
                }

                // 更新されたUMLクラスリストからコードを生成
                String headerCode = translator.translateUmlToCode(Collections.singletonList(childClass));
                childFile.updateCode(headerCode);

                if (umlController != null) {
                    umlController.updateDiagram(childFile);
                }
                if (codeController != null) {
                    codeController.updateCodeTab(childFile);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to add generalization: " + e.getMessage());
        }
    }

    private void addRequiredIncludes(CppFile file, String type) {
        Map<String, String> standardIncludes = Map.of(
                "string", "<string>",
                "vector", "<vector>",
                "map", "<map>",
                "set", "<set>");

        // テンプレート型の解析（例：vector<string>）
        if (type.contains("<")) {
            String baseType = type.substring(0, type.indexOf("<"));
            if (standardIncludes.containsKey(baseType.toLowerCase())) {
                addInclude(file, standardIncludes.get(baseType.toLowerCase()));
            }
            // 内部の型も再帰的にチェック
            String innerType = type.substring(type.indexOf("<") + 1, type.lastIndexOf(">"));
            addRequiredIncludes(file, innerType);
        } else {
            if (standardIncludes.containsKey(type.toLowerCase())) {
                addInclude(file, standardIncludes.get(type.toLowerCase()));
            }
        }
    }

    private void addInclude(CppFile file, String include) {
        String currentCode = file.getCode();
        String includeStatement = "#include " + include;
        if (!currentCode.contains(includeStatement)) {
            int insertPos = currentCode.indexOf("\n\nclass");
            if (insertPos != -1) {
                String newCode = currentCode.substring(0, insertPos) +
                        "\n" + includeStatement +
                        currentCode.substring(insertPos);
                file.updateCode(newCode);
            }
        }
    }

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

    private String toSourceCodeType(Type type) {
        return translator.translateType(type);
    }

    public void updateCodeFile(ICodeFile changedCodeFile, String code) {
        try {
            if (changedCodeFile instanceof CppFile) {
                CppFile file = (CppFile) changedCodeFile;
                System.out.println("DEBUG: Updating code for file: " + file.getFileName());

                updateCode(file, code);

                // ヘッダーファイルとソースファイルの両方で更新を通知
                if (umlController != null) {
                    umlController.updateDiagram(file);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to update code: " + e.getMessage());
        }
    }

    public Optional<Class> findClass(String className) {
        String baseName = getBaseName(className);
        CppFile headerFile = headerFiles.get(baseName);
        if (headerFile != null) {
            List<Class> classes = headerFile.getUmlClassList();
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
    public String generateSequenceDiagram(String className, String methodName) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        Optional<CppFile> implFileOpt = Optional.ofNullable(findImplFile(className));

        if (headerFileOpt.isPresent() && implFileOpt.isPresent()) {
            CppFile headerFile = headerFileOpt.get();
            CppFile implFile = implFileOpt.get();

            // デバッグ出力
            System.out.println("Generating sequence diagram for " + className + "::" + methodName);
            System.out.println("Header file: " + headerFile.getFileName());
            System.out.println("Implementation file: " + implFile.getFileName());

            return translator.generateSequenceDiagram(
                    headerFile.getCode(),
                    implFile.getCode(),
                    methodName);
        }

        System.err.println("Could not find necessary files for " + className);
        return "";
    }

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