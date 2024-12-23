package io.github.morichan.retuss.model;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.translator.cpp.header.CppTranslator;

import java.util.*;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class CppModel {
    private final UmlModel umlModel;
    private static final CppModel model = new CppModel();
    private final Map<String, CppFile> headerFiles = new HashMap<>();
    private final Map<String, CppFile> implFiles = new HashMap<>();
    private final CppTranslator translator;
    private final List<ModelChangeListener> listeners = new ArrayList<>();

    private CppModel() {
        this.umlModel = UmlModel.getInstance();
        this.translator = createTranslator();
    }

    private CppTranslator createTranslator() {
        return new CppTranslator();
    }

    public static CppModel getInstance() {
        return model;
    }

    public void addNewFile(String fileName) {
        String baseName = fileName.replaceAll("\\.(h|hpp|cpp)$", "");
        if (!headerFiles.containsKey(baseName)) {
            createCppFiles(baseName, Optional.empty());
        }
    }

    public void addNewFileFromUml(CppHeaderClass headerClass) {
        createCppFiles(headerClass.getName(), Optional.of(headerClass));
    }

    private void createCppFiles(String baseName, Optional<CppHeaderClass> headerClass) {
        if (headerFiles.containsKey(baseName)) {
            System.out.println("Files already exist for baseName: " + baseName);
            return;
        }
        // ヘッダーファイル作成
        CppFile headerFile = new CppFile(baseName + ".h", true);
        CppFile implFile = new CppFile(baseName + ".cpp", false);

        if (headerClass.isPresent()) {
            headerFile.updateCode(headerFile.getCode());
        }

        headerFiles.put(baseName, headerFile);
        implFiles.put(baseName, implFile);

        notifyFileAdded(headerFile);
        notifyFileAdded(implFile);
    }

    private void handleFileRename(CppFile headerFile, String oldName, String newName) {
        String oldBaseName = oldName.replace(".h", "");
        String newBaseName = newName.replace(".h", "");

        // ヘッダーファイル更新
        updateHeaderForRename(headerFile, oldBaseName, newBaseName);

        // 実装ファイル更新
        updateImplForRename(oldBaseName, newBaseName, oldName, newName);

        notifyFileRenamed(oldName, newName);
    }

    private void updateHeaderForRename(CppFile headerFile, String oldBaseName, String newBaseName) {
        String headerCode = headerFile.getCode();
        headerCode = headerCode
                .replace(oldBaseName.toUpperCase() + "_H", newBaseName.toUpperCase() + "_H")
                .replaceAll("(?<![a-zA-Z0-9_])" + oldBaseName + "(\\s*\\([^)]*\\)\\s*;)", newBaseName + "$1")
                .replaceAll("(?<![a-zA-Z0-9_])~" + oldBaseName + "(\\s*\\([^)]*\\)\\s*;)",
                        "~" + newBaseName + "$1");
        headerFile.updateCode(headerCode);
    }

    private void updateImplForRename(String oldBaseName, String newBaseName, String oldName, String newName) {
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
            notifyFileUpdated(implFile);
        }
    }

    public void updateCode(CppFile file, String code) {
        String baseName = file.getBaseName();

        if (file.isHeader()) {
            updateHeaderFile(file, code, baseName);
        } else {
            updateImplementationFile(file, code, baseName);
        }
    }

    private void updateHeaderFile(CppFile headerFile, String code, String baseName) {
        String oldClassName = headerFile.getFileName().replace(".h", "");
        headerFile.updateCode(code);
        // 新しいクラス名を取得
        String newClassName = headerFile.getBaseName();
        if (!newClassName.equals(oldClassName)) {
            String newName = newClassName;
            handleClassNameChange(oldClassName, newName, headerFile);
        }

        notifyFileUpdated(headerFile);
    }

    private void updateImplementationFile(CppFile implFile, String code, String baseName) {
        implFile.updateCode(code);
        notifyFileUpdated(implFile);
    }

    private void handleClassNameChange(String oldClassName, String newClassName, CppFile headerFile) {
        try {
            // 1. マップの更新前に両方のファイルの参照を保持
            CppFile implFile = implFiles.get(oldClassName);

            // 2. ヘッダーファイルの更新
            // インクルードガード、クラス名、コンストラクタ、デストラクタの更新
            String headerCode = headerFile.getCode();
            // headerCode = headerCode
            // .replace(oldClassName.toUpperCase() + "_H", newClassName.toUpperCase() +
            // "_H")
            // .replaceAll("class\\s+" + oldClassName + "\\s*", "class " + newClassName + "
            // ")
            // .replaceAll("(?<![a-zA-Z0-9_])" + oldClassName + "\\s*\\(", newClassName +
            // "(")
            // .replaceAll("(?<![a-zA-Z0-9_])~" + oldClassName + "\\s*\\(", "~" +
            // newClassName + "(");

            // 3. 実装ファイルの更新
            if (implFile != null) {
                // String implCode = implFile.getCode();
                // // インクルード文、スコープ解決演算子、コンストラクタ、デストラクタの更新
                // implCode = implCode
                // .replace("#include \"" + oldClassName + ".h\"", "#include \"" + newClassName
                // + ".h\"")
                // .replace(oldClassName + "::", newClassName + "::")
                // .replaceAll(oldClassName + "::" + oldClassName, newClassName + "::" +
                // newClassName)
                // .replaceAll(oldClassName + "::~" + oldClassName, newClassName + "::~" +
                // newClassName);

                // ファイル名の更新
                implFile.updateFileName(newClassName + ".cpp");
                // implFile.updateCode(implCode);
            }

            // 4. マップの更新
            headerFiles.remove(oldClassName);
            implFiles.remove(oldClassName);

            headerFile.updateFileName(newClassName + ".h");
            // headerFile.updateCode(headerCode);

            headerFiles.put(newClassName, headerFile);
            if (implFile != null) {
                implFiles.put(newClassName, implFile);
            }

            // 5. リスナーへの通知
            if (implFile != null) {
                notifyFileRenamed(oldClassName + ".cpp", newClassName + ".cpp");
            }
            notifyFileRenamed(oldClassName + ".h", newClassName + ".h");

            // // 6. ファイルの内容更新を通知
            // if (implFile != null) {
            // notifyFileUpdated(implFile);
            // }
            // notifyFileUpdated(headerFile);

        } catch (Exception e) {
            System.err.println("Error during class name change: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addAttribute(String className, Attribute attribute) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty())
            return;

        CppFile headerFile = headerFileOpt.get();
        try {
            if (!headerFile.getHeaderClasses().isEmpty()) {
                CppHeaderClass targetClass = headerFile.getHeaderClasses().get(0);

                String newCode = translator.addAttribute(headerFile.getCode(), targetClass, attribute);
                headerFile.updateCode(newCode);
                notifyFileUpdated(headerFile);
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

            String newCode = translator.addOperation(headerFile.getCode(), targetClass, operation);
            headerFile.updateCode(newCode);
            notifyFileUpdated(headerFile);
        } catch (Exception e) {
            System.err.println("Failed to add operation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void delete(String className) {
        System.out.println("DEBUG: Attempting to delete class: " + className);

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

            headerFiles.remove(className);
            implFiles.remove(className);

            notifyFileDeleted(className);
        } catch (Exception e) {
            System.err.println("Error during class deletion:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void delete(String className, Attribute attribute) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty()) {
            System.out.println("Header file not found for class: " + className);
            return;
        }

        try {
            CppFile headerFile = headerFileOpt.get();
            CppHeaderClass targetClass = headerFile.getHeaderClasses().get(0);

            System.out.println("Attempting to delete attribute: " + attribute.getName().getNameText() + " from class: "
                    + className);

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
            notifyFileUpdated(headerFile);
        } catch (Exception e) {
            System.err.println("Failed to delete attribute: " + e.getMessage());
        }
    }

    public void delete(String className, Operation operation) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty()) {
            System.out.println("Header file not found for class: " + className);
            return;
        }

        try {
            CppFile headerFile = headerFileOpt.get();
            CppHeaderClass targetClass = headerFile.getHeaderClasses().get(0);

            System.out.println("Attempting to delete operation: " + operation.getName().getNameText() + " from class: "
                    + className);

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
            notifyFileUpdated(headerFile);
        } catch (Exception e) {
            System.err.println("Failed to delete operation: " + e.getMessage());
        }
    }

    public void addInheritance(String derivedClassName, String baseClassName) {
        Optional<CppFile> derivedFileOpt = findHeaderFileByClassName(derivedClassName);

        if (derivedFileOpt.isEmpty())
            return;

        try {
            CppFile derivedFile = derivedFileOpt.get();
            String newCode = translator.addInheritance(derivedFile.getCode(), derivedClassName, baseClassName);
            derivedFile.updateCode(newCode);
            notifyFileUpdated(derivedFile);
        } catch (Exception e) {
            System.err.println("Failed to add inheritance: " + e.getMessage());
        }
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

    public void addRealization(String sourceClassName, String interfaceName) {
        Optional<CppFile> sourceFileOpt = findHeaderFileByClassName(sourceClassName);
        Optional<CppFile> interfaceFileOpt = findHeaderFileByClassName(interfaceName);

        if (sourceFileOpt.isEmpty() || interfaceFileOpt.isEmpty())
            return;

        try {
            CppFile sourceFile = sourceFileOpt.get();
            CppHeaderClass sourceClass = sourceFile.getHeaderClasses().get(0);
            CppHeaderClass interfaceClass = interfaceFileOpt.get().getHeaderClasses().get(0);

            String newCode = translator.addInheritance(
                    sourceFile.getCode(), sourceClassName,
                    interfaceName);

            List<String> lines = new ArrayList<>(Arrays.asList(newCode.split("\n")));
            int classStart = -1;
            int classEnd = findClassEndPosition(lines);

            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("class " + sourceClassName)) {
                    classStart = i;
                    break;
                }
            }

            for (Operation op : interfaceClass.getOperationList()) {
                // 重複チェック
                if (isDuplicateMethod(op, lines, classStart, classEnd)) {
                    System.out.println("Skipping duplicate method: " + op.getName().getNameText());
                    continue;
                }

                Operation implementedOp = new Operation(op.getName());
                implementedOp.setReturnType(op.getReturnType());
                implementedOp.setVisibility(Visibility.Public);
                implementedOp.setParameters(new ArrayList<>()); // 空のパラメータリストで初期化

                // 安全にパラメータを取得してコピー
                List<Parameter> params = safeGetParameters(op);
                for (Parameter param : params) {
                    implementedOp.addParameter(param);
                }

                System.out.println("Adding method: " + implementedOp.getName().getNameText());
                sourceClass.addMemberModifier(implementedOp.getName().getNameText(), Modifier.OVERRIDE);
                newCode = translator.addOperation(newCode, sourceClass, implementedOp);
            }

            // sourceClass.getRelationshipManager().addRealization(interfaceName);
            sourceFile.updateCode(newCode);
            notifyFileUpdated(sourceFile);
        } catch (Exception e) {
            System.err.println("Failed to add realization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Parameter> safeGetParameters(Operation op) {
        try {
            return op.getParameters() != null ? op.getParameters() : new ArrayList<>();
        } catch (IllegalStateException e) {
            System.out.println("No parameters initialized for method: " + op.getName().getNameText());
            return new ArrayList<>();
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
            notifyFileUpdated(ownerFile);
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
            notifyFileUpdated(ownerFile);
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
            notifyFileUpdated(ownerFile);
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
            notifyFileUpdated(ownerFile);
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
            notifyFileUpdated(sourceFile);
        } catch (Exception e) {
            System.err.println("Failed to add association: " + e.getMessage());
        }
    }

    private boolean isDuplicateMethod(Operation newOp, List<String> lines, int startLine, int endLine) {
        // メソッドのシグネチャを作成
        String signature = getMethodSignature(newOp);
        System.out.println("Checking for duplicate: " + signature);

        // 既存のコードで同じシグネチャを持つメソッドを探す
        for (int i = startLine; i < endLine; i++) {
            String line = lines.get(i).trim();
            if (line.endsWith(";") && line.contains("(")) {
                // 既存メソッドのシグネチャを抽出して比較
                String existingMethod = line.substring(0, line.indexOf(";"))
                        .replaceAll("\\s+", "")
                        .replaceAll("override", "")
                        .replaceAll("virtual", "");
                System.out.println("Comparing with: " + existingMethod);

                if (existingMethod.contains(signature)) {
                    System.out.println("Found duplicate: " + existingMethod);
                    return true;
                }
            }
        }
        return false;
    }

    private String getMethodSignature(Operation op) {
        StringBuilder signature = new StringBuilder();
        signature.append(op.getReturnType().toString())
                .append(op.getName().getNameText())
                .append("(");

        List<Parameter> params = safeGetParameters(op);
        if (!params.isEmpty()) {
            List<String> paramTypes = new ArrayList<>();
            for (Parameter param : params) {
                paramTypes.add(param.getType().toString());
            }
            signature.append(String.join(",", paramTypes));
        }
        signature.append(")");

        return signature.toString().replaceAll("\\s+", "");
    }

    public void removeInheritance(String className, String baseClassName) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty())
            return;

        try {
            CppFile headerFile = headerFileOpt.get();
            String newCode = translator.removeInheritance(headerFile.getCode(), baseClassName);
            headerFile.updateCode(newCode);
            notifyFileUpdated(headerFile);
        } catch (Exception e) {
            System.err.println("Failed to remove inheritance: " + e.getMessage());
        }
    }

    public void removeRealization(String className, String interfaceName) {
        Optional<CppFile> headerFileOpt = findHeaderFileByClassName(className);
        if (headerFileOpt.isEmpty())
            return;

        try {
            CppFile headerFile = headerFileOpt.get();
            translator.removeInheritance(headerFile.getCode(), interfaceName);
        } catch (Exception e) {
            System.err.println("Failed to remove realization: " + e.getMessage());
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

    public Map<String, CppFile> getHeaderFiles() {
        return Collections.unmodifiableMap(headerFiles);
    }

    public Map<String, CppFile> getImplFiles() {
        return Collections.unmodifiableMap(implFiles);
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

    public interface ModelChangeListener {
        void onModelChanged();

        void onFileAdded(CppFile file);

        void onFileUpdated(CppFile file);

        void onFileDeleted(String className);

        void onFileRenamed(String oldName, String newName);
    }

    public void addChangeListener(ModelChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(ModelChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyFileAdded(CppFile file) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onFileAdded(file);
            } catch (Exception e) {
                System.err.println("Error notifying file addition: " + e.getMessage());
            }
        }
    }

    private void notifyFileUpdated(CppFile file) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onFileUpdated(file);
            } catch (Exception e) {
                System.err.println("Error notifying file update: " + e.getMessage());
            }
        }
    }

    private void notifyFileDeleted(String className) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onFileDeleted(className);
            } catch (Exception e) {
                System.err.println("Error notifying file deletion: " + e.getMessage());
            }
        }
    }

    private void notifyFileRenamed(String oldName, String newName) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onFileRenamed(oldName, newName);
            } catch (Exception e) {
                System.err.println("Error notifying file rename: " + e.getMessage());
            }
        }
    }

    private void notifyModelChanged() {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onModelChanged();
            } catch (Exception e) {
                System.err.println("Error notifying model change: " + e.getMessage());
            }
        }
    }

    public void updateCodeFile(CppFile changedCodeFile, String code) {
        try {
            CppFile file = changedCodeFile;
            System.out.println("DEBUG: Updating code for file: " + file.getFileName());

            updateCode(file, code);
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