package io.github.morichan.retuss.model.uml.cpp;

import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import java.util.*;

public class CppImplClass {
    private final CppHeaderClass headerClass;
    private final Map<String, List<LocalVariable>> methodLocals;
    private final Set<String> includes;
    private final Set<String> usingDirectives;
    private final CppRelationshipManager relationshipManager;

    public CppImplClass(CppHeaderClass headerClass) {
        this.headerClass = headerClass;
        this.methodLocals = new HashMap<>();
        this.includes = new LinkedHashSet<>();
        this.usingDirectives = new LinkedHashSet<>();
        this.relationshipManager = new CppRelationshipManager(headerClass.getName());

        // ヘッダーファイルのインクルードを自動追加
        addInclude("\"" + headerClass.getName() + ".h\"");
    }

    // ローカル変数の定義
    public static class LocalVariable {
        private final String name;
        private final String type;
        private final String initialValue;
        private final Set<Modifier> modifiers;

        public LocalVariable(String name, String type, String initialValue, Set<Modifier> modifiers) {
            this.name = name;
            this.type = type;
            this.initialValue = initialValue;
            this.modifiers = modifiers != null ? EnumSet.copyOf(modifiers) : EnumSet.noneOf(Modifier.class);
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getInitialValue() {
            return initialValue;
        }

        public Set<Modifier> getModifiers() {
            return Collections.unmodifiableSet(modifiers);
        }
    }

    // ローカル変数の管理
    public void addLocalVariable(String methodName, LocalVariable variable) {
        methodLocals.computeIfAbsent(methodName, k -> new ArrayList<>())
                .add(variable);

        if (!isBuiltInType(variable.getType())) {
            RelationshipInfo relation = new RelationshipInfo(
                    extractClassName(variable.getType()),
                    RelationType.DEPENDENCY_USE);

            relation.setElement(
                    variable.getName(), // name
                    ElementType.LOCAL_VARIABLE, // elemType
                    "1", // multiplicity
                    Visibility.Private // visibility
            );

            relationshipManager.addRelationship(relation);
            addInclude("\"" + extractClassName(variable.getType()) + ".h\"");
        }
    }

    public List<LocalVariable> getLocalVariables(String methodName) {
        return Collections.unmodifiableList(
                methodLocals.getOrDefault(methodName, new ArrayList<>()));
    }

    public void clearLocalVariables() {
        methodLocals.clear();
    }

    // インクルードの管理
    public void addInclude(String include) {
        includes.add(include);
    }

    public Set<String> getIncludes() {
        return Collections.unmodifiableSet(includes);
    }

    // using ディレクティブの管理
    public void addUsingDirective(String using) {
        usingDirectives.add(using);
    }

    public Set<String> getUsingDirectives() {
        return Collections.unmodifiableSet(usingDirectives);
    }

    // 関係管理
    public void addMethodCall(String targetClass, String methodName,
            String callingMethod) {
        RelationshipInfo relation = new RelationshipInfo(
                targetClass,
                RelationType.DEPENDENCY_USE); // METHOD_CALL

        relation.setElement(
                methodName, // name
                ElementType.METHOD_CALL, // elemType
                "1", // multiplicity
                Visibility.Public // visibility
        );

        relationshipManager.addRelationship(relation);
        addInclude("\"" + targetClass + ".h\"");
    }

    public void addTemporaryObject(String targetClass, String context) {
        RelationshipInfo relation = new RelationshipInfo(
                targetClass,
                RelationType.DEPENDENCY_USE);

        relation.setElement(
                "temp_" + context, // name
                ElementType.TEMPORARY, // elemType
                "1", // multiplicity
                Visibility.Private // visibility
        );

        relationshipManager.addRelationship(relation);
    }

    public void addParameterType(String targetClass, String paramName, String methodName) {
        RelationshipInfo relation = new RelationshipInfo(
                targetClass,
                RelationType.DEPENDENCY_USE);

        relation.setElement(
                paramName, // name
                ElementType.PARAMETER, // elemType
                "1", // multiplicity
                Visibility.Private // visibility
        );

        relationshipManager.addRelationship(relation);

        // 必要なインクルードを自動追加
        addInclude("\"" + targetClass + ".h\"");
    }

    public CppRelationshipManager getRelationshipManager() {
        return relationshipManager;
    }

    public Set<RelationshipInfo> getRelationships() {
        return relationshipManager.getAllRelationships();
    }

    private boolean isBuiltInType(String type) {
        Set<String> builtInTypes = Set.of(
                "void", "bool", "char", "int", "long", "float", "double",
                "size_t", "string", "vector", "map", "set");
        return builtInTypes.contains(extractBaseType(type).toLowerCase());
    }

    private String extractClassName(String type) {
        // テンプレート型からクラス名を抽出
        int templateStart = type.indexOf('<');
        return templateStart == -1 ? type : type.substring(0, templateStart).trim();
    }

    private String extractBaseType(String type) {
        // ポインタ、参照、const修飾子を除去
        return type.replaceAll("\\s*[*&]\\s*$", "") // ポインタ/参照を除去
                .replaceAll("^\\s*const\\s+", "") // constを除去
                .replaceAll("<.*>", "") // テンプレートパラメータを除去
                .trim();
    }

    public CppHeaderClass getHeaderClass() {
        return headerClass;
    }
}