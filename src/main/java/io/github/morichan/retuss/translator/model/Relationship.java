package io.github.morichan.retuss.translator.model;

public class Relationship {
    public enum RelationType {
        INHERITANCE,
        AGGREGATION,
        COMPOSITION,
        DEPENDENCY
    }

    private final String source;
    private final String target;
    private final RelationType type;
    private final String multiplicity;

    public Relationship(String source, String target, RelationType type, String multiplicity) {
        this.source = source;
        this.target = target;
        this.type = type;
        this.multiplicity = multiplicity;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public RelationType getType() {
        return type;
    }

    public String getMultiplicity() {
        return multiplicity;
    }
}
