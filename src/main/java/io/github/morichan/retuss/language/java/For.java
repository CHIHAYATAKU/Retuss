package io.github.morichan.retuss.language.java;

import java.util.ArrayList;
import java.util.List;

public class For implements BlockStatement {

    private String name = "for";
    private Type type;
    private String forInit;
    private String expression;
    private String forUpdate;

    private List<BlockStatement> statements = new ArrayList<>();

    public String getForInit() {
        return this.forInit;
    }

    public void setForInit(String forInit) {
        this.forInit = forInit;
    }

    public String getExpression() {
        return this.expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getForUpdate() {
        return this.forUpdate;
    }

    public void setForUpdate(String forUpdate) {
        this.forUpdate = forUpdate;
    }

    public List<BlockStatement> getStatements() {
        return this.statements;
    }

    public void addStatement(BlockStatement statement) {
        if (statement != null)
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
        sb.append("for (" + this.forInit + "; " + this.expression+ "; " + this.forUpdate + " ) {\n");
        for (BlockStatement blockStatement : this.statements) {
            sb.append("            ");
            sb.append(blockStatement.getStatement() + "\n");
        }
        sb.append("        }\n");
        return sb.toString();
    }
}
