package io.github.morichan.retuss.language.java;

import java.util.ArrayList;
import java.util.List;

public class For implements BlockStatement {

    private String name = "for";
    private Type type;
    private String forInit;
    private String expression;
    private String forUpdate;
    private String numLoop = "";
    private List<BlockStatement> statements = new ArrayList<>();

    public For(String forInit, String expression, String forUpdate) {
        this.forInit = forInit;
        this.expression = expression;
        this.forUpdate = forUpdate;
    }

    public String getForInit() {
        return this.forInit;
    }

    public String getExpression() {
        return this.expression;
    }

    public String getForUpdate() {
        return this.forUpdate;
    }

    public String getNumLoop() { return this.numLoop; }

    public void setNumLoop(String numLoop) {
        if (numLoop == null || numLoop.isEmpty()) throw new IllegalArgumentException();
        this.numLoop = numLoop;
    }

    public List<BlockStatement> getStatements() {
        return this.statements;
    }

    public void addStatement(BlockStatement statement) {
        if (statement == null) throw new IllegalArgumentException();
        this.statements.add(statement);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    @Override
    public String getStatement() {
        StringBuilder sb = new StringBuilder();
        sb.append("for (" + this.forInit + "; " + this.expression+ "; " + this.forUpdate + ") {\n");
        for (BlockStatement blockStatement : this.statements) {
            sb.append("            ");
            sb.append(blockStatement.getStatement() + "\n");
        }
        sb.append("        }\n");
        return sb.toString();
    }
}
