package io.github.morichan.retuss.model;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.translator.CppTranslator;
import java.util.*;

public class CppModel {
    private static final CppModel model = new CppModel();
    private final Map<String, CppFile> headerFiles = new HashMap<>();
    private final Map<String, CppFile> implFiles = new HashMap<>();
    private final CppTranslator translator = new CppTranslator();
    private CodeController codeController;
    private UmlController umlController;
    private final List<ModelChangeListener> changeListeners = new ArrayList<>();

    private CppModel() {
    }

    public static CppModel getInstance() {
        return model;
    }

    public void setCodeController(CodeController controller) {
        this.codeController = controller;
    }

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
    }

    public void addNewFile(String fileName) {
        String baseName = fileName.replaceAll("\\.(hpp|cpp)$", "");

        // ヘッダーファイルの作成
        CppFile headerFile = new CppFile(baseName + ".hpp", true);
        headerFile.addChangeListener(new CppFile.FileChangeListener() {
            @Override
            public void onFileChanged(CppFile file) {
                updateImplFile(file);
                notifyModelChanged();
                if (umlController != null) {
                    umlController.updateDiagram(file);
                }
            }

            @Override
            public void onFileNameChanged(String oldName, String newName) {
                String oldBaseName = oldName.replace(".hpp", "");
                String newBaseName = newName.replace(".hpp", "");
                updateImplFileName(oldBaseName, newBaseName);
            }
        });

        // 実装ファイルの作成
        CppFile implFile = new CppFile(baseName + ".cpp", false);

        // ファイルの登録
        headerFiles.put(baseName, headerFile);
        implFiles.put(baseName, implFile);

        // コントローラーに通知
        if (codeController != null) {
            codeController.updateCodeTab(headerFile);
            codeController.updateCodeTab(implFile);
        }
        if (umlController != null) {
            umlController.updateDiagram(headerFile);
        }
    }

    public void addNewUmlClass(Class umlClass) {
        String baseName = umlClass.getName();
        CppFile headerFile = new CppFile(baseName + ".hpp", true);
        CppFile implFile = new CppFile(baseName + ".cpp", false);

        // UMLクラスからコードを生成
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
        String baseName = getBaseName(file.getFileName());

        if (file.isHeader()) {
            CppFile headerFile = headerFiles.get(baseName);
            if (headerFile != null) {
                headerFile.updateCode(code);
                updateImplFile(headerFile);
            }
        } else {
            CppFile implFile = implFiles.get(baseName);
            if (implFile != null) {
                implFile.updateCode(code);
            }
        }

        notifyModelChanged();
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

    private void updateImplFile(CppFile headerFile) {
        String baseName = getBaseName(headerFile.getFileName());
        CppFile implFile = implFiles.get(baseName);
        if (implFile != null) {
            String currentCode = implFile.getCode();
            String includeStatement = "#include \"" + headerFile.getFileName() + "\"";
            if (!currentCode.contains(includeStatement)) {
                String newCode = includeStatement + "\n\n" +
                        (currentCode.startsWith("#include") ? currentCode.substring(currentCode.indexOf("\n\n") + 2)
                                : currentCode);
                implFile.updateCode(newCode);
            }
        }
    }

    private void updateImplFileName(String oldBaseName, String newBaseName) {
        CppFile implFile = implFiles.remove(oldBaseName);
        if (implFile != null) {
            String newImplName = newBaseName + ".cpp";
            implFile.updateCode(implFile.getCode().replace(
                    oldBaseName + ".hpp",
                    newBaseName + ".hpp"));
            implFiles.put(newBaseName, implFile);
        }
    }

    public List<Class> getUmlClassList() {
        List<Class> allClasses = new ArrayList<>();
        for (CppFile headerFile : headerFiles.values()) {
            allClasses.addAll(headerFile.getUmlClassList());
        }
        System.out.println("CppModel classes: " + allClasses.size());
        for (Class cls : allClasses) {
            System.out.println("  Class: " + cls.getName());
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
                String includeStatement = "#include \"" + superClassName + ".hpp\"";
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
        if (!changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
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
        String typeName = type.toString();
        Map<String, String> typeMap = Map.of(
                "string", "std::string",
                "vector", "std::vector",
                "map", "std::map",
                "set", "std::set");
        return typeMap.getOrDefault(typeName, typeName);
    }

    public void updateCodeFile(ICodeFile changedCodeFile, String code) {
        try {
            if (changedCodeFile instanceof CppFile) {
                CppFile file = (CppFile) changedCodeFile;
                updateCode(file, code);

                if (file.isHeader()) {
                    System.err.println("Updating header file: " + file.getFileName());
                    if (umlController != null) {
                        umlController.updateDiagram(file);
                    }
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
        return fileName.replaceAll("\\.(hpp|cpp)$", "");
    }
}