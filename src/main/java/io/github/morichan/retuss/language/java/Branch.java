package io.github.morichan.retuss.language.java;

import java.util.ArrayList;
import java.util.List;

public class Branch implements BlockStatement {
    private Type type;
    private String condition;
    private List<BlockStatement> statements = new ArrayList<>();

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public List<BlockStatement> getStatements() {
        return statements;
    }

    public void addStatement(BlockStatement statement) {
        if (statement != null)
            statements.add(statement);
    }

    @Override
    public String getName() {
        return this.condition; // 仮
    }

    @Override
    public Type getType() {
        return type; // 仮
    }

    @Override
    public String getStatement() {
        return this.condition; // 仮
    }
}
