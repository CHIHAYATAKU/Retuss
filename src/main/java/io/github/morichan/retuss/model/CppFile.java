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

        updateCode(sb.toString()); // updateCodeを使用して初期化
    }

    private void initializeImplementationFile() {
        this.sourceCode = "";
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
        return sourceCode;
    }

    @Override
    public List<Class> getUmlClassList() {
        return Collections.unmodifiableList(umlClassList);
    }

    @Override
    public void updateCode(String code) {
        try {
            // コードが実質的に変更されている場合のみ処理
            if (!code.equals(this.sourceCode)) {
                // C++のコードをUMLに変換
                if (isHeader) {
                    List<Class> newUmlClassList = translator.translateCodeToUml(code);
                    // クラスリストが有効な場合のみ更新
                    if (!newUmlClassList.isEmpty()) {
                        this.umlClassList = newUmlClassList;
                    }
                }

                // ファイル名の同期処理
                Optional<String> newClass = translator.extractClassName(code);
                newClass.ifPresent(className -> {
                    // mainClassが存在すればファイル名を更新
                    String expectedFileName = className + (isHeader ? ".hpp" : ".cpp");
                    if (!expectedFileName.equals(this.fileName)) {
                        String oldFileName = this.fileName; // 古いファイル名を保存
                        this.fileName = expectedFileName;
                        notifyFileNameChanged(oldFileName, this.fileName); // ファイル名変更を通知
                    }

                });

                // コードが更新されたことを通知
                this.sourceCode = code;
                notifyFileChanged();
            }
        } catch (Exception e) {
            System.err.println("Failed to update C++ code in " + fileName + ": " + e.getMessage());
        }
    }

    @Override
    public void addUmlClass(Class umlClass) {
        this.umlClassList.add(umlClass);
        this.sourceCode = translator.translateUmlToCode(umlClassList).toString();
    }

    @Override
    public void removeClass(Class umlClass) {
        this.umlClassList.remove(umlClass);
        this.sourceCode = translator.translateUmlToCode(umlClassList).toString();
    }

    public boolean isHeader() {
        return isHeader;
    }

    // 拡張したファイル変更リスナー
    public interface FileChangeListener {
        void onFileChanged(CppFile file);

        default void onFileNameChanged(String oldName, String newName) {
        }
    }

    public void addChangeListener(FileChangeListener listener) {
        listeners.add(listener);
    }

    private void notifyFileChanged() {
        for (FileChangeListener listener : listeners) {
            listener.onFileChanged(this);
        }
    }

    private void notifyFileNameChanged(String oldName, String newName) {
        for (FileChangeListener listener : listeners) {
            listener.onFileNameChanged(oldName, newName);
        }
    }

}