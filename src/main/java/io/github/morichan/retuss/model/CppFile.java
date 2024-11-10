package io.github.morichan.retuss.model;

import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.common.FileChangeListener;
import io.github.morichan.retuss.model.common.ICodeFile;
import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.retuss.parser.cpp.CPP14Lexer;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.CppTranslator;
import io.github.morichan.retuss.translator.cpp.listeners.CppMethodAnalyzer;

import java.util.*;

import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.*;

public class CppFile implements ICodeFile {
    private final UUID ID = UUID.randomUUID();
    private String fileName = "";
    private String sourceCode;
    private List<Class> umlClassList = new ArrayList<>();
    private CppTranslator translator;
    private final boolean isHeader;
    private final List<FileChangeListener> listeners = new ArrayList<>();

    private UmlController umlController;

    public CppFile(String fileName, boolean isHeader) {
        this.fileName = fileName;
        this.isHeader = isHeader;
        this.translator = new CppTranslator();
        initializeFile();
    }

    public void setUmlController(UmlController controller) {
        this.umlController = controller;
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
        String className = fileName.replace(".h", "");
        String guardName = className.toUpperCase() + "_H";

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
        sb.append("#include \"").append(className).append(".h\"\n\n");
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

    public void updateFileName(String newName) {
        if (!this.fileName.equals(newName)) {
            String oldName = this.fileName;
            this.fileName = newName;
            System.out.println("DEBUG: CppFile updating filename from " + oldName + " to " + newName);

            // UmlControllerに通知（null チェック付き）
            if (umlController != null) {
                umlController.updateFileName(oldName, newName);
            } else {
                System.out.println("DEBUG: UmlController is not set");
            }

            notifyFileNameChanged(oldName, newName);
        }
    }

    @Override
    public void updateCode(String code) {
        try {
            if (!code.equals(this.sourceCode)) {
                this.sourceCode = code;

                if (isHeader) {
                    List<Class> newUmlClassList = translator.translateCodeToUml(code);
                    if (!newUmlClassList.isEmpty()) {
                        System.out.println("DEBUG: New UML classes found: " + newUmlClassList.size());
                        this.umlClassList = newUmlClassList;
                    }

                    // クラス名の変更を検出
                    Optional<String> newClassName = translator.extractClassName(code);
                    if (newClassName.isPresent()) {
                        String className = newClassName.get();
                        String expectedFileName = className + ".h";
                        System.out.println("DEBUG: Detected class name: " + className +
                                ", current file: " + this.fileName);

                        if (!expectedFileName.equals(this.fileName)) {
                            String oldFileName = this.fileName;
                            this.fileName = expectedFileName;
                            System.out.println("DEBUG: File name changing from " +
                                    oldFileName + " to " + expectedFileName);
                            notifyFileNameChanged(oldFileName, expectedFileName);
                        }
                    }
                } else {
                    updateMethodImplementations(code);
                }

                notifyFileChanged();
            }
        } catch (Exception e) {
            System.err.println("Failed to update code: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateMethodImplementations(String code) {
        try {
            // 対応するヘッダーファイルのクラスを取得
            String baseName = getFileName().replace(".cpp", "");
            CppFile headerFile = CppModel.getInstance().findHeaderFile(baseName);

            if (headerFile != null && !headerFile.getUmlClassList().isEmpty()) {
                Class umlClass = headerFile.getUmlClassList().get(0);

                // メソッドの実装を解析
                CppMethodAnalyzer analyzer = new CppMethodAnalyzer(umlClass);
                CharStream input = CharStreams.fromString(code);
                CPP14Lexer lexer = new CPP14Lexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                CPP14Parser parser = new CPP14Parser(tokens);

                ParseTreeWalker walker = new ParseTreeWalker();
                walker.walk(analyzer, parser.translationUnit());

                // シーケンス図の更新をトリガー
                if (umlController != null) {
                    umlController.updateDiagram(this);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to update method implementations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void addUmlClass(Class umlClass) {
        if (!isHeader)
            return;

        umlClassList.add(umlClass);
        // 修正前: String newCode =
        // translator.translateUmlToCode(Collections.singletonList(umlClass));
        // 修正後:
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

    public void addChangeListener(FileChangeListener listener) {
        listeners.add(listener);
    }

    // 拡張したファイル変更リスナー
    public interface FileChangeListener {
        void onFileChanged(CppFile file);

        void onFileNameChanged(String oldName, String newName);
    }

    private void notifyFileChanged() {
        System.out.println("DEBUG: Notifying file change for " + fileName);
        for (FileChangeListener listener : listeners) {
            try {
                listener.onFileChanged(this);
            } catch (Exception e) {
                System.err.println("Error in file change notification: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void notifyFileNameChanged(String oldName, String newName) {
        System.out.println("DEBUG: Notifying file name change: " + oldName + " -> " + newName);
        for (FileChangeListener listener : listeners) {
            try {
                listener.onFileNameChanged(oldName, newName);
            } catch (Exception e) {
                System.err.println("Error in file name change notification: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

}