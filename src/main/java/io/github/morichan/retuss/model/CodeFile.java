package io.github.morichan.retuss.model;

import java.util.UUID;

public class CodeFile {
    final private UUID ID = UUID.randomUUID();
    private String fileName = "";

    public UUID getID() {
        return ID;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
