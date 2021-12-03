package io.github.morichan.retuss.model.uml;

public class Lifeline {
    private String name = "";
    private String type = "";

    public Lifeline(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Lifeline(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getSignature() {
        StringBuilder sb = new StringBuilder();
        if(!name.isEmpty()) {
            sb.append(name);
        }
        if(!type.isEmpty()) {
            sb.append(":" + type);
        }
        return sb.toString();
    }
}
