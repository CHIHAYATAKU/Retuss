package io.github.morichan.retuss.language.java;

import java.util.ArrayList;
import java.util.List;

public class While implements BlockStatement{
    private String name = "while";
    private Type type;
    private String condition;
    private List<BlockStatement> statements = new ArrayList<>();

    public String getCondition() {
        return this.condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
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
        return this.name + this.condition;
    }
}
