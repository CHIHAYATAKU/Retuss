package io.github.morichan.retuss.model;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.controller.UmlController;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.Modifier;
import io.github.morichan.retuss.translator.cpp.CppTranslator;

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
    private final List<FileChangeListener> listeners = new ArrayList<>();

    private UmlController umlController;

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
            headerClasses.clear();
            headerClasses.addAll(translator.translateCodeToUml(sourceCode));
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

    public void updateCode(String code) {
        // 入力コードの即時反映
        synchronized (updateLock) {
            if (code.equals(this.sourceCode)) {
                return; // 変更がなければスキップ
            }
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
                    System.out.println("DEBUG: Updating header file code");
                    // クラス名の更新を先に行う
                    Optional<String> newClassName = translator.extractClassName(code);
                    if (newClassName.isPresent()) {
                        String className = newClassName.get();
                        String expectedFileName = className + ".h";
                        if (!expectedFileName.equals(this.fileName)) {
                            String oldFileName = this.fileName;
                            this.fileName = expectedFileName;
                            notifyFileNameChanged(oldFileName, expectedFileName);
                        }
                    }

                    // ヘッダーのUMLクラスリストの更新
                    List<CppHeaderClass> newUmlClassList = translator.translateCodeToUml(code);
                    System.out.println(
                            "DEBUG: Parsed classes: " + (newUmlClassList != null ? newUmlClassList.size() : "null"));
                    if (!newUmlClassList.isEmpty()) {
                        // リストの内容を更新
                        headerClasses.clear();
                        headerClasses.addAll(newUmlClassList);
                        for (CppHeaderClass cls : this.headerClasses) {
                            if (cls.getAbstruct() && !cls.getOperationList().isEmpty()) {
                                boolean allAbstract = true;
                                for (Operation op : cls.getOperationList()) {
                                    if (!cls.getModifiers(op.getName().getNameText()).contains(Modifier.ABSTRACT)) {
                                        allAbstract = false;
                                        cls.setInterface(false);
                                        break; // 一度でも修飾子に ABTRACT が含まれていない場合、ループを終了
                                    }
                                }
                                if (allAbstract && cls.getAttributeList().isEmpty()) {
                                    System.out.println("すべての操作が抽象かつ属性が空です。");
                                    cls.setInterface(true);
                                }
                            }

                        }

                        // 対応する実装ファイルからの関係も解析
                        String baseName = getBaseName();
                        CppFile implFile = CppModel.getInstance().findImplFile(baseName);
                        // if (implFile != null) {
                        // analyzeImplementationFile(implFile);
                        // }
                    }
                }

                // UMLコントローラーに通知
                if (umlController != null) {
                    umlController.updateDiagram(this);
                }
                notifyFileChanged();
            } catch (

            CancellationException e) {
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
        System.out.println("Removing class: " + cls.getName());
        // リストの内容を変更
        headerClasses.clear();

        // クラス定義全体を削除
        List<String> lines = new ArrayList<>(Arrays.asList(this.sourceCode.split("\n")));
        int classStart = -1;
        int classEnd = -1;

        // クラスの開始位置を探す
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("class " + cls.getName())) {
                classStart = i;
                break;
            }
        }

        // クラスの終了位置を探す
        if (classStart != -1) {
            for (int i = classStart; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("};")) {
                    classEnd = i;
                    break;
                }
            }
        }

        // クラス定義を削除
        if (classStart != -1 && classEnd != -1) {
            System.out.println("Removing class definition from lines " + classStart + " to " + classEnd);
            lines.subList(classStart, classEnd + 1).clear();
            this.sourceCode = String.join("\n", lines);

            // コードを更新
            updateCode(this.sourceCode);
        }

        System.out.println("Class removal completed");
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