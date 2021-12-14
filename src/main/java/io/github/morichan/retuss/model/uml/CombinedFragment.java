package io.github.morichan.retuss.model.uml;

import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.Optional;

public class CombinedFragment extends InteractionFragment{
    private InteractionOperandKind kind;
    private ArrayList<InteractionOperand> interactionOperandList = new ArrayList<>();
    private Statement statement;

    public CombinedFragment(Lifeline lifeline, InteractionOperandKind kind) {
        super.setLifeline(lifeline);
        this.kind = kind;
    }

    public InteractionOperandKind getKind() {
        return kind;
    }

    public ArrayList<InteractionOperand> getInteractionOperandList() {
        return interactionOperandList;
    }

    public Optional<Statement> getStatement() {
        return Optional.ofNullable(statement);
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    @Override
    public String toString() {
        return "Combined Fragment : " + kind.toString();
    }
}
