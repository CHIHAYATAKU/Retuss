package io.github.morichan.retuss.model;

import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.translator.CppTranslator;
import java.util.*;

public class CppFile implements ICodeFile {
    private final UUID ID = UUID.randomUUID();
    private String fileName = "";
    private String sourceCode;
    private List<Class> umlClassList = new ArrayList<>();
    private CppTranslator translator = new CppTranslator();
    private final boolean isHeader;
    private List<FileChangeListener> listeners = new ArrayList<>();

    public CppFile(String fileName, boolean isHeader) {
        this.fileName = fileName;
        this.isHeader = isHeader;
        initializeFile();
    }

    private void initializeFile() {
        if (isHeader) {
            initializeHeaderFile();
        } else {
            initializeImplementationFile();
        }

        // 初期化後にUMLクラスリストを更新（ヘッダーファイルのみ）
        if (isHeader && sourceCode != null) {
            this.umlClassList = translator.translateCodeToUml(sourceCode);
        }
    }

    // ファイルの種類に応じた初期化の改善
    private void initializeHeaderFile() {
        String className = fileName.replace(".hpp", "");
        String guardName = className.toUpperCase() + "_HPP";

        StringBuilder sb = new StringBuilder();
        sb.append("#ifndef ").append(guardName).append("\n");
        sb.append("#define ").append(guardName).append("\n\n");
        sb.append("class ").append(className).append(" {\n");
        sb.append("public:\n");
        sb.append("protected:\n");
        sb.append("private:\n");
        sb.append("};\n\n");
        sb.append("#endif // ").append(guardName).append("\n");

        this.sourceCode = sb.toString();
    }

    private void initializeImplementationFile() {
        String className = fileName.replace(".cpp", "");
        StringBuilder sb = new StringBuilder();
        sb.append("#include \"").append(className).append(".hpp\"\n\n");
        sb.append(className).append("::").append(className).append("() {\n}\n\n");
        sb.append(className).append("::~").append(className).append("() {\n}\n");

        this.sourceCode = sb.toString();
    }

    @Override
    public UUID getID() {
        return ID;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public String getCode() {
        return sourceCode != null ? sourceCode : "";
    }

    @Override
    public List<Class> getUmlClassList() {
        return isHeader ? Collections.unmodifiableList(umlClassList) : Collections.emptyList();
    }

    @Override
    public void updateCode(String code) {
        if (code.equals(this.sourceCode))
            return;

        try {
            if (isHeader) {
                // ヘッダーファイルの場合のみUMLクラスリストを更新
                List<Class> newUmlClassList = translator.translateCodeToUml(code);
                if (!newUmlClassList.isEmpty()) {
                    this.umlClassList = newUmlClassList;

                    // クラス名の変更を検出
                    Optional<String> newClassName = translator.extractClassName(code);
                    newClassName.ifPresent(className -> {
                        String expectedFileName = className + ".hpp";
                        if (!expectedFileName.equals(this.fileName)) {
                            String oldFileName = this.fileName;
                            this.fileName = expectedFileName;
                            notifyFileNameChanged(oldFileName, expectedFileName);
                        }
                    });
                }
            }

            this.sourceCode = code;
            notifyFileChanged();
        } catch (Exception e) {
            System.err.println("Failed to update code: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void addUmlClass(Class umlClass) {
        if (!isHeader)
            return;

        umlClassList.add(umlClass);
        String newCode = translator.translateUmlToCode(Collections.singletonList(umlClass));
        updateCode(newCode);
    }

    @Override
    public void removeClass(Class umlClass) {
        if (!isHeader)
            return;

        umlClassList.remove(umlClass);
        if (!umlClassList.isEmpty()) {
            String newCode = translator.translateUmlToCode(umlClassList);
            updateCode(newCode);
        }
    }

    public boolean isHeader() {
        return isHeader;
    }

    // 拡張したファイル変更リスナー
    public interface FileChangeListener {
        void onFileChanged(CppFile file);

        void onFileNameChanged(String oldName, String newName);
    }

    public void addChangeListener(FileChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    private void notifyFileChanged() {
        for (FileChangeListener listener : listeners) {
            try {
                listener.onFileChanged(this);
            } catch (Exception e) {
                System.err.println("Error notifying file change: " + e.getMessage());
            }
        }
    }

    private void notifyFileNameChanged(String oldName, String newName) {
        for (FileChangeListener listener : listeners) {
            try {
                listener.onFileNameChanged(oldName, newName);
            } catch (Exception e) {
                System.err.println("Error notifying filename change: " + e.getMessage());
            }
        }
    }

}