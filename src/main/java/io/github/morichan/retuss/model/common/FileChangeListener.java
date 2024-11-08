package io.github.morichan.retuss.model.common;

public interface FileChangeListener {
    void onFileChanged(ICodeFile file);

    void onFileNameChanged(String oldName, String newName);
}