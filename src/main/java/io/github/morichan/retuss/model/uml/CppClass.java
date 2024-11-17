package io.github.morichan.retuss.model.uml;

import java.util.*;
import io.github.morichan.fescue.feature.*;
import io.github.morichan.fescue.feature.visibility.Visibility;

public class CppClass extends Class {
    // 修飾子の定義
    public enum Modifier {
        STATIC,
        CONST,
        VOLATILE,
        MUTABLE,
        VIRTUAL,
        PURE_VIRTUAL,
        ABSTRACT,
        OVERRIDE,
        FINAL
    }

    // 関係情報の定義
    public static class RelationshipInfo {
        // 関係の種類
        public enum RelationType {
            INHERITANCE, // 継承
            COMPOSITION, // コンポジション（値型メンバ）
            AGGREGATION, // 集約（ポインタ型メンバ）
            ASSOCIATION, // 関連（参照型メンバ）
            DEPENDENCY, // 依存（一時的な使用）
            REALIZATION // 実現（インターフェース実装）
        }

        // 要素の種類
        public enum ElementType {
            ATTRIBUTE, // 属性
            OPERATION, // 操作
            PARAMETER // パラメータ
        }

        // 関係の要素
        public static class RelationshipElement {
            private final String name;
            private final ElementType elemType;
            private final String multiplicity;
            private final Visibility visibility;
            private final String type;
            private final String returnType;
            private final String defaultValue;
            private final boolean isPureVirtual;
            private final Set<Modifier> modifiers;

            public RelationshipElement(
                    String name,
                    ElementType elemType,
                    String multiplicity,
                    Visibility visibility,
                    String type,
                    String returnType,
                    String defaultValue,
                    boolean isPureVirtual,
                    Set<Modifier> modifiers) {
                this.name = name;
                this.elemType = elemType;
                this.multiplicity = multiplicity;
                this.visibility = visibility;
                this.type = type;
                this.returnType = returnType;
                this.defaultValue = defaultValue;
                this.isPureVirtual = isPureVirtual;
                // nullや空のSetが渡された場合の処理を追加
                this.modifiers = (modifiers != null && !modifiers.isEmpty()) ? EnumSet.copyOf(modifiers)
                        : EnumSet.noneOf(Modifier.class);
            }

            // ゲッターメソッド
            public String getName() {
                return name;
            }

            public ElementType getElemType() {
                return elemType;
            }

            public String getMultiplicity() {
                return multiplicity;
            }

            public Visibility getVisibility() {
                return visibility;
            }

            public String getType() {
                return type;
            }

            public String getReturnType() {
                return returnType;
            }

            public String getDefaultValue() {
                return defaultValue;
            }

            public boolean isPureVirtual() {
                return isPureVirtual;
            }

            public Set<Modifier> getModifiers() {
                return Collections.unmodifiableSet(modifiers);
            }
        }

        private final String targetClass;
        private final RelationType type;
        private final Set<RelationshipElement> elements = new HashSet<>();

        public RelationshipInfo(String targetClass, RelationType type) {
            this.targetClass = targetClass;
            this.type = type;
        }

        public void addElement(String name, ElementType elemType, String multiplicity, Visibility visibility) {
            addElement(name, elemType, multiplicity, visibility, null, null, null, false,
                    EnumSet.noneOf(Modifier.class));
        }

        // 完全版
        public void addElement(
                String name,
                ElementType elemType,
                String multiplicity,
                Visibility visibility,
                String type,
                String returnType,
                String defaultValue,
                boolean isPureVirtual,
                Set<Modifier> modifiers) {
            elements.add(new RelationshipElement(
                    name, elemType, multiplicity, visibility,
                    type, returnType, defaultValue, isPureVirtual, modifiers));
        }

        // ゲッターメソッド
        public String getTargetClass() {
            return targetClass;
        }

        public RelationType getType() {
            return type;
        }

        public Set<RelationshipElement> getElements() {
            return Collections.unmodifiableSet(elements);
        }
    }

    // メンバー変数
    private final Map<String, Set<Modifier>> memberModifiers = new HashMap<>();
    private final Map<String, Set<RelationshipInfo>> relationships = new HashMap<>();

    // コンストラクタ
    public CppClass(String name) {
        super(name);
    }

    public CppClass(String name, Boolean isActive) {
        super(name, isActive);
    }

    // メンバー修飾子の管理
    public void addMemberModifier(String memberName, Modifier modifier) {
        memberModifiers.computeIfAbsent(memberName, k -> EnumSet.noneOf(Modifier.class))
                .add(modifier);
    }

    public Set<Modifier> getModifiers(String memberName) {
        return Collections.unmodifiableSet(
                memberModifiers.getOrDefault(memberName, EnumSet.noneOf(Modifier.class)));
    }

    // 関係の管理
    public void addRelationship(RelationshipInfo relationship) {
        relationships.computeIfAbsent(relationship.getTargetClass(), k -> new HashSet<>())
                .add(relationship);
    }

    public Set<RelationshipInfo> getRelationships() {
        Set<RelationshipInfo> allRelationships = new HashSet<>();
        for (Set<RelationshipInfo> relationSet : relationships.values()) {
            allRelationships.addAll(relationSet);
        }
        return Collections.unmodifiableSet(allRelationships);
    }

    // 基底クラスのメソッドのオーバーライド
    @Override
    public void addOperation(Operation operation) {
        super.addOperation(operation);
    }

    @Override
    public void addAttribute(Attribute attribute) {
        super.addAttribute(attribute);
    }
}