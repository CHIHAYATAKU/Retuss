package io.github.morichan.retuss.model;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.Modifier;
import io.github.morichan.retuss.translator.cpp.header.CppTranslator;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

public class CppFile {
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor(); // 解析用の専用スレッド
    private final ScheduledExecutorService updateExecutor = Executors.newSingleThreadScheduledExecutor(); // 更新用のスレッド
    private Future<?> lastTask; // 最後の非同期タスクを追跡
    private final Object updateLock = new Object(); // 同期処理用ロック
    private final UUID ID = UUID.randomUUID();
    private String fileName = "";
    private String sourceCode;
    private final List<CppHeaderClass> headerClasses = new ArrayList<>();
    private CppImplClass implClass;
    private CppTranslator translator;
    private final boolean isHeader;

    public CppFile(String fileName, boolean isHeader) {
        this.fileName = fileName;
        this.isHeader = isHeader;
        this.translator = new CppTranslator();
        if (isHeader) {
            // 初期のヘッダークラスを追加
            CppHeaderClass headerClass = new CppHeaderClass(getBaseName());
            headerClasses.add(headerClass);
        }
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
            headerClasses.clear();
            headerClasses.addAll(translator.translateHeaderCodeToUml(sourceCode));
        }
    }

    // ファイルの種類に応じた初期化の改善
    private void initializeHeaderFile() {
        String className = fileName.replace(".h", "");

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

    public UUID getID() {
        return ID;
    }

    public String getFileName() {
        return fileName;
    }

    public String getCode() {
        return sourceCode != null ? sourceCode : "";
    }

    public List<CppHeaderClass> getHeaderClasses() {
        return isHeader ? Collections.unmodifiableList(headerClasses) : Collections.emptyList();
    }

    public void updateFileName(String newName) {
        this.fileName = newName;
    }

    public void updateCode(String code) {
        // 入力コードの即時反映
        synchronized (updateLock) {
            this.sourceCode = code;
        }

        // 古いタスクをキャンセル
        if (lastTask != null && !lastTask.isDone()) {
            lastTask.cancel(true);
        }
        // 新しい非同期タスクを登録
        lastTask = analysisExecutor.submit(() -> {
            try {
                if (isHeader) {
                    analyzeHeaderFile(code);
                }
            } catch (CancellationException e) {
                System.out.println("DEBUG: Task was canceled");
            } catch (Exception e) {
                System.err.println("Error during async code update: " + e.getMessage());
                e.printStackTrace();
            }
        });
        try {
            // 非同期タスクが完了するまで待つ
            lastTask.get(); // タスクの完了を待機
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void analyzeHeaderFile(String code) {
        List<CppHeaderClass> newUmlClassList = translator.translateHeaderCodeToUml(code);
        if (!newUmlClassList.isEmpty()) {
            headerClasses.clear();
            headerClasses.addAll(newUmlClassList);
            this.fileName = headerClasses.get(headerClasses.size() - 1).getName() + ".h";
            updateClassProperties();
        }
    }

    private void updateClassProperties() {
        for (CppHeaderClass cls : headerClasses) {
            if (cls.getAbstruct() && !cls.getOperationList().isEmpty()) {
                boolean allAbstract = true;
                for (Operation op : cls.getOperationList()) {
                    if (!cls.getModifiers(op.getName().getNameText()).contains(Modifier.PURE_VIRTUAL)
                            && !cls.getName().equals(op.getName().getNameText())
                            && !("~" + cls.getName()).equals(op.getName().getNameText())) {
                        allAbstract = false;
                        cls.setInterface(false);
                        break;
                    }
                }
                if (allAbstract && cls.getAttributeList().isEmpty()) {
                    cls.setInterface(true);
                }
            }
        }
    }

    public String getBaseName() {
        return fileName.replaceAll("\\.(h|hpp|cpp)$", "");
    }

    // リソースの解放
    public void shutdown() {
        analysisExecutor.shutdown();
        updateExecutor.shutdown();
    }

    public boolean isHeader() {
        return isHeader;
    }

    public void removeClass(CppHeaderClass cls) {
        headerClasses.clear();
    }
}