package io.github.morichan.retuss.model;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.translator.CppTranslator;
import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;

import java.util.*;

public class CppModel {
    private static CppModel model = new CppModel();
    private List<CppPairFile> sourceFileList = new ArrayList<>();
    private CppTranslator translator = new CppTranslator();
    private CodeController codeController;
    private UmlController umlController;

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
        // 拡張子を除去してベース名を取得
        String baseName = fileName.replaceAll("\\.(hpp|cpp)$", "");
        CppPairFile pairFile = new CppPairFile(baseName);
        sourceFileList.add(pairFile);

        // ファイル名の変更を監視
        pairFile.getHeaderFile().addChangeListener(file -> {
            notifyModelChanged();
            umlController.updateDiagram(file);
        });

        // JavaModelと同様、両方のコントローラに通知
        codeController.updateCodeTab(pairFile.getHeaderFile());
        codeController.updateCodeTab(pairFile.getImplFile());
        umlController.updateDiagram(pairFile.getHeaderFile());
    }

    public void addNewUmlClass(Class umlClass) {
        CppPairFile pairFile = new CppPairFile(umlClass.getName());
        String headerCode = (String) translator.translateUmlToCode(Collections.singletonList(umlClass));
        pairFile.updateHeaderCode(headerCode);

        sourceFileList.add(pairFile);
        notifyModelChanged(); // モデル変更を通知

        umlController.updateDiagram(pairFile.getHeaderFile());
        codeController.updateCodeTab(pairFile.getHeaderFile());
        codeController.updateCodeTab(pairFile.getImplFile());
    }

    public void addAttribute(String className, Attribute attribute) {
        Optional<CppPairFile> pairFileOpt = findPairFile(className);
        if (pairFileOpt.isEmpty())
            return;

        CppPairFile pairFile = pairFileOpt.get();
        List<Class> umlClasses = pairFile.getUmlClassList();
        if (umlClasses.isEmpty())
            return;

        try {
            // 型に基づいて必要なヘッダーを追加
            addRequiredIncludes(pairFile, attribute.getType().toString());

            // クラスに属性を追加
            Class targetClass = umlClasses.get(0);
            targetClass.addAttribute(attribute);

            // ヘッダーファイルを更新
            String headerCode = (String) translator.translateUmlToCode(Collections.singletonList(targetClass));
            pairFile.updateHeaderCode(headerCode);

            umlController.updateDiagram(pairFile.getHeaderFile());
            codeController.updateCodeTab(pairFile.getHeaderFile());
        } catch (Exception e) {
            System.err.println("Failed to add attribute: " + e.getMessage());
        }
    }

    public void addOperation(String className, Operation operation) {
        Optional<CppPairFile> pairFileOpt = findPairFile(className);
        if (pairFileOpt.isEmpty())
            return;

        CppPairFile pairFile = pairFileOpt.get();
        List<Class> umlClasses = pairFile.getUmlClassList();
        if (umlClasses.isEmpty())
            return;

        try {
            // 戻り値と引数の型に基づいて必要なヘッダーを追加
            addRequiredIncludes(pairFile, operation.getReturnType().toString());
            for (var param : operation.getParameters()) {
                addRequiredIncludes(pairFile, param.getType().toString());
            }

            // クラスに操作を追加
            Class targetClass = umlClasses.get(0);
            targetClass.addOperation(operation);

            // ヘッダーファイルを更新
            String headerCode = (String) translator.translateUmlToCode(Collections.singletonList(targetClass));
            pairFile.updateHeaderCode(headerCode);

            // 実装ファイルにメソッドの骨組みを追加
            StringBuilder implCode = new StringBuilder();
            implCode.append(toSourceCodeType(operation.getReturnType())).append(" ")
                    .append(targetClass.getName()).append("::")
                    .append(operation.getName()).append("(");

            // パラメータリストの構築
            List<String> params = new ArrayList<>();
            operation.getParameters().forEach(param -> params.add(String.format("%s %s",
                    toSourceCodeType(param.getType()),
                    param.getName())));
            implCode.append(String.join(", ", params)).append(") {\n");
            implCode.append("    // TODO: Implement this method\n");
            implCode.append("}\n\n");

            String currentImplCode = pairFile.getImplFile().getCode();
            pairFile.updateImplementationCode(currentImplCode + implCode.toString());

            umlController.updateDiagram(pairFile.getHeaderFile());
            codeController.updateCodeTab(pairFile.getHeaderFile());
            codeController.updateCodeTab(pairFile.getImplFile());
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
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

    public void addGeneralization(String className, String superClassName) {
        Optional<CppPairFile> childFileOpt = findPairFile(className);
        Optional<CppPairFile> parentFileOpt = findPairFile(superClassName);
        if (childFileOpt.isEmpty() || parentFileOpt.isEmpty())
            return;

        try {
            CppPairFile childFile = childFileOpt.get();
            List<Class> childClasses = childFile.getUmlClassList();
            List<Class> parentClasses = parentFileOpt.get().getUmlClassList();

            if (!childClasses.isEmpty() && !parentClasses.isEmpty()) {
                // 継承関係を設定
                childClasses.get(0).setSuperClass(parentClasses.get(0));

                // 親クラスのヘッダーをインクルード
                childFile.addInclude("\"" + superClassName + ".hpp\"");

                // translateClassToHeaderの代わりにtranslateUmlToCodeを使用
                String headerCode = (String) translator.translateUmlToCode(
                        Collections.singletonList(childClasses.get(0)));
                childFile.updateHeaderCode(headerCode);

                umlController.updateDiagram(childFile.getHeaderFile());
                codeController.updateCodeTab(childFile.getHeaderFile());
            }
        } catch (Exception e) {
            System.err.println("Failed to add generalization: " + e.getMessage());
        }
    }

    private void addRequiredIncludes(CppPairFile file, String type) {
        Map<String, String> standardIncludes = Map.of(
                "string", "<string>",
                "vector", "<vector>",
                "map", "<map>",
                "set", "<set>");

        // テンプレート型の解析（例：vector<string>）
        if (type.contains("<")) {
            String baseType = type.substring(0, type.indexOf("<"));
            if (standardIncludes.containsKey(baseType.toLowerCase())) {
                file.addInclude(standardIncludes.get(baseType.toLowerCase()));
            }
            // 内部の型も再帰的にチェック
            String innerType = type.substring(type.indexOf("<") + 1, type.lastIndexOf(">"));
            addRequiredIncludes(file, innerType);
        } else {
            if (standardIncludes.containsKey(type.toLowerCase())) {
                file.addInclude(standardIncludes.get(type.toLowerCase()));
            }
        }
    }

    private Optional<CppPairFile> findPairFile(String className) {
        // クラス名またはファイル名での検索に対応
        String searchName = className.replaceAll("\\.(hpp|cpp)$", "");
        return sourceFileList.stream()
                .filter(pair -> {
                    String baseName = pair.getHeaderFile().getFileName().replaceAll("\\.(hpp|cpp)$", "");
                    return baseName.equals(searchName);
                })
                .findFirst();
    }

    private Optional<CppPairFile> findPairFile(CppFile file) {
        return sourceFileList.stream()
                .filter(pair -> pair.getHeaderFile().equals(file) || pair.getImplFile().equals(file))
                .findFirst();
    }

    public Optional<Class> findClass(String className) {
        return sourceFileList.stream()
                .map(pair -> pair.getUmlClassList())
                .filter(classes -> !classes.isEmpty())
                .map(classes -> classes.get(0))
                .filter(clazz -> clazz.getName().equals(className))
                .findFirst();
    }

    public List<Class> getUmlClassList() {
        List<Class> allClasses = new ArrayList<>();
        for (CppPairFile pairFile : sourceFileList) {
            allClasses.addAll(pairFile.getHeaderFile().getUmlClassList());
        }
        return Collections.unmodifiableList(allClasses);
    }

    private List<ModelChangeListener> changeListeners = new ArrayList<>();

    public interface ModelChangeListener {
        void onModelChanged();
    }

    public void addChangeListener(ModelChangeListener listener) {
        changeListeners.add(listener);
    }

    private void notifyModelChanged() {
        for (ModelChangeListener listener : changeListeners) {
            listener.onModelChanged();
        }
    }

    public void updateCode(CppFile file, String code) {
        try {
            Optional<CppPairFile> pairFile = findPairFile(file);
            if (pairFile.isPresent()) {
                if (file.isHeader()) {
                    pairFile.get().updateHeaderCode(code);
                    System.err.println("ヘッダ変更！" + pairFile);
                } else {
                    pairFile.get().updateImplementationCode(code);
                }
                // ヘッダーファイルが変更された場合のみUMLを更新
                if (file.isHeader()) {
                    notifyModelChanged();
                    umlController.updateDiagram(file);
                }
                // 両方のファイルのタブを更新
                codeController.updateCodeTab(pairFile.get().getHeaderFile());
                codeController.updateCodeTab(pairFile.get().getImplFile());
            }
        } catch (Exception e) {
            System.err.println("Failed to update code: " + e.getMessage());
        }
    }
}