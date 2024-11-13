package io.github.morichan.retuss.translator.cpp.listeners;

import io.github.morichan.retuss.model.uml.CppClass;
import io.github.morichan.retuss.model.uml.Relationship;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.parser.cpp.CPP14ParserBaseListener;
import java.util.*;

public class CppDependencyAnalyzer extends CPP14ParserBaseListener {
    private final String currentClassName;
    private final Set<String> instantiatedClasses = new HashSet<>();
    private final Set<String> usedClasses = new HashSet<>();
    private final Set<Relationship> relationships = new HashSet<>();

    public CppDependencyAnalyzer(String currentClassName) {
        this.currentClassName = currentClassName;
    }

    @Override
    public void enterNewExpression_(CPP14Parser.NewExpression_Context ctx) {
        if (ctx.newTypeId() != null) {
            String typeName = ctx.newTypeId().getText();
            if (isUserDefinedType(typeName)) {
                instantiatedClasses.add(cleanTypeName(typeName));
                addDependencyRelationship(cleanTypeName(typeName));
            }
        }
    }

    @Override
    public void enterDeclaration(CPP14Parser.DeclarationContext ctx) {
        try {
            if (ctx.getChild(0) instanceof CPP14Parser.DeclSpecifierSeqContext) {
                CPP14Parser.DeclSpecifierSeqContext declSpec = (CPP14Parser.DeclSpecifierSeqContext) ctx.getChild(0);
                String typeName = declSpec.getText();
                if (isUserDefinedType(typeName)) {
                    usedClasses.add(cleanTypeName(typeName));
                    addDependencyRelationship(cleanTypeName(typeName));
                }
            }
        } catch (Exception e) {
            System.err.println("Error analyzing declaration: " + e.getMessage());
        }
    }

    private void addDependencyRelationship(String targetClass) {
        if (!targetClass.equals(currentClassName)) {
            relationships.add(new Relationship(
                    currentClassName,
                    targetClass,
                    Relationship.RelationType.DEPENDENCY,
                    "",
                    "",
                    "",
                    "",
                    true));
        }
    }

    private boolean isUserDefinedType(String typeName) {
        Set<String> basicTypes = Set.of("void", "bool", "char", "int", "float", "double",
                "long", "short", "unsigned", "signed");
        String cleanType = cleanTypeName(typeName);
        return !basicTypes.contains(cleanType) && !cleanType.startsWith("std::");
    }

    private String cleanTypeName(String typeName) {
        return typeName.replaceAll("[*&<>]", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    public Set<Relationship> getRelationships() {
        return Collections.unmodifiableSet(relationships);
    }

    public Set<String> getInstantiatedClasses() {
        return Collections.unmodifiableSet(instantiatedClasses);
    }

    public Set<String> getUsedClasses() {
        return Collections.unmodifiableSet(usedClasses);
    }
}