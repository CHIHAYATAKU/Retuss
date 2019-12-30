package io.github.morichan.retuss.language.java;

import java.util.ArrayList;
import java.util.List;

public class If implements BlockStatement {
    private String name = "if";
    private Type type;
    private String condition;
    private List<BlockStatement> statements = new ArrayList<>();
    private List<BlockStatement> elseStatements = new ArrayList<>();

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

    public List<BlockStatement> getElseStatements() {
        return elseStatements;
    }

    public void setElseStatement(BlockStatement elseStatement) {
        this.elseStatements.add(elseStatement);
    }

    public void addElseStatement(BlockStatement elseStatement) {
        if (elseStatement != null)
            this.elseStatements.add(elseStatement);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getStatement() {
        StringBuilder sb = new StringBuilder();
        sb.append("if (" + this.condition + ") {\n");
        for (BlockStatement blockStatement : this.statements) {
            sb.append("            ");
            sb.append(blockStatement.getStatement() + "\n");
        }
        sb.append("        } ");

        if (this.elseStatements.size() > 0) {
            sb.append("else ");
            if (this.elseStatements.get(0) instanceof If) {
                sb.append(this.elseStatements.get(0).getStatement());
            } else {
                sb.append("{\n");
                for (BlockStatement blockStatement : this.elseStatements) {
                    sb.append("            ");
                    sb.append(blockStatement.getStatement() + "\n");
                }
                sb.append("        }\n");
            }
        }
        return sb.toString();
    }
}
