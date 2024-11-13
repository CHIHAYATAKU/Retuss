package io.github.morichan.retuss.model.uml;

import java.util.*;

public class Relationship {
    public enum RelationType {
        INHERITANCE,
        AGGREGATION,
        COMPOSITION,
        DEPENDENCY,
        ASSOCIATION
    }

    private final String source;
    private final String target;
    private final RelationType type;
    private final String sourceRole;
    private final String targetRole;
    private final String sourceMultiplicity;
    private final String targetMultiplicity;
    private final boolean isNavigable;

    public Relationship(String source, String target, RelationType type,
            String sourceRole, String targetRole,
            String sourceMultiplicity, String targetMultiplicity,
            boolean isNavigable) {
        this.source = source;
        this.target = target;
        this.type = type;
        this.sourceRole = sourceRole;
        this.targetRole = targetRole;
        this.sourceMultiplicity = sourceMultiplicity;
        this.targetMultiplicity = targetMultiplicity;
        this.isNavigable = isNavigable;
    }

    // Getters
    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public RelationType getType() {
        return type;
    }

    public String getSourceRole() {
        return sourceRole;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getSourceMultiplicity() {
        return sourceMultiplicity;
    }

    public String getTargetMultiplicity() {
        return targetMultiplicity;
    }

    public boolean isNavigable() {
        return isNavigable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Relationship))
            return false;
        Relationship that = (Relationship) o;
        return isNavigable == that.isNavigable &&
                Objects.equals(source, that.source) &&
                Objects.equals(target, that.target) &&
                type == that.type &&
                Objects.equals(sourceRole, that.sourceRole) &&
                Objects.equals(targetRole, that.targetRole) &&
                Objects.equals(sourceMultiplicity, that.sourceMultiplicity) &&
                Objects.equals(targetMultiplicity, that.targetMultiplicity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, type, sourceRole, targetRole,
                sourceMultiplicity, targetMultiplicity, isNavigable);
    }
}