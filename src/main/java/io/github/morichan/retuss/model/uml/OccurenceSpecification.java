package io.github.morichan.retuss.model.uml;

import com.github.javaparser.ast.stmt.Statement;

import java.util.Objects;
import java.util.Optional;

public class OccurenceSpecification extends InteractionFragment {
    private Message message;
    private Statement statement;

    public OccurenceSpecification(Lifeline lifeline) {
        super.setLifeline(lifeline);
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Optional<Statement> getStatement() {
        return Optional.ofNullable(statement);
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    @Override
    public String toString() {
        if(Objects.nonNull(this.message)) {
            return message.toString();
        }
        return super.toString();
    }
}
