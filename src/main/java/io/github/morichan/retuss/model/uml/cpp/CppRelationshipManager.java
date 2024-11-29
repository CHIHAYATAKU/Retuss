package io.github.morichan.retuss.model.uml.cpp;

import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import java.util.*;
import java.util.stream.Collectors;

public class CppRelationshipManager {
    private final Map<String, Set<RelationshipInfo>> relationshipsByTarget = new HashMap<>();
    private final String sourceClassName; // 関係元のクラス名

    public CppRelationshipManager(String sourceClassName) {
        this.sourceClassName = sourceClassName;
    }

    // 関係の追加
    public void addRelationship(RelationshipInfo relationship) {
        relationshipsByTarget.computeIfAbsent(relationship.getTargetClass(), k -> new HashSet<>())
                .add(relationship);
    }

    public void addRealization(String interfaceName) {
        RelationshipInfo relation = new RelationshipInfo(interfaceName, RelationType.REALIZATION);
        addRelationship(relation);
    }

    // 継承関係の追加
    public void addInheritance(String targetClass) {
        RelationshipInfo relation = new RelationshipInfo(targetClass, RelationType.INHERITANCE);
        addRelationship(relation);
    }

    // コンポジション関係の追加
    public void addComposition(String targetClass, String memberName, String multiplicity, Visibility visibility) {
        RelationshipInfo relation = new RelationshipInfo(targetClass, RelationType.COMPOSITION);
        relation.addElement(memberName, ElementType.ATTRIBUTE, multiplicity, visibility);
        addRelationship(relation);
    }

    // 集約関係の追加
    public void addAggregation(String targetClass, String memberName, String multiplicity, Visibility visibility) {
        RelationshipInfo relation = new RelationshipInfo(targetClass, RelationType.AGGREGATION);
        relation.addElement(memberName, ElementType.ATTRIBUTE, multiplicity, visibility);
        addRelationship(relation);
    }

    public void addAssociation(String targetClass, String memberName, String multiplicity, Visibility visibility) {
        RelationshipInfo relation = new RelationshipInfo(targetClass, RelationType.ASSOCIATION);
        relation.addElement(memberName, ElementType.ATTRIBUTE, multiplicity, visibility);
        addRelationship(relation);
    }

    // 指定したターゲットクラスとの関係を取得
    public Set<RelationshipInfo> getRelationshipsWith(String targetClass) {
        return Collections.unmodifiableSet(
                relationshipsByTarget.getOrDefault(targetClass, new HashSet<>()));
    }

    // 全ての関係を取得
    public Set<RelationshipInfo> getAllRelationships() {
        return relationshipsByTarget.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    // 特定の種類の関係のみを取得
    public Set<RelationshipInfo> getRelationshipsOfType(RelationType type) {
        return getAllRelationships().stream()
                .filter(r -> r.getType() == type)
                .collect(Collectors.toSet());
    }

    // PlantUML形式の関係文字列を生成
    public String generatePlantUmlRelationships() {
        StringBuilder sb = new StringBuilder();
        Map<String, RelationshipInfo> inheritanceMap = new HashMap<>();

        for (RelationshipInfo relation : getAllRelationships()) {
            switch (relation.getType()) {
                case INHERITANCE:
                case REALIZATION:
                    // 実現を優先、または初めての継承関係を保存
                    String targetClass = relation.getTargetClass();
                    if (relation.getType() == RelationType.REALIZATION || !inheritanceMap.containsKey(targetClass)) {
                        inheritanceMap.put(targetClass, relation);
                    }
                    break;

                case COMPOSITION:
                case AGGREGATION:
                case ASSOCIATION:
                    sb.append(sourceClassName)
                            .append(" ")
                            .append(relation.getType().getPlantUmlText())
                            .append(" \"");

                    if (!relation.getElements().isEmpty()) {
                        RelationshipElement elem = relation.getElements().iterator().next();
                        sb.append(elem.getMultiplicity() != null ? elem.getMultiplicity() : "1");
                    }
                    sb.append("\" ")
                            .append(relation.getTargetClass());

                    if (!relation.getElements().isEmpty()) {
                        RelationshipElement elem = relation.getElements().iterator().next();
                        sb.append(" : ").append(elem.getVisibility()).append(elem.getName());
                    }
                    sb.append("\n");
                    break;

                case DEPENDENCY:
                    sb.append(sourceClassName)
                            .append(" ")
                            .append(relation.getType().getPlantUmlText())
                            .append(" ")
                            .append(relation.getTargetClass());

                    if (!relation.getElements().isEmpty()) {
                        RelationshipElement elem = relation.getElements().iterator().next();
                        sb.append(" : ").append(elem.getVisibility()).append(elem.getName());
                    }
                    sb.append("\n");
                    break;
            }
        }

        // 継承/実現関係の出力
        for (RelationshipInfo relation : inheritanceMap.values()) {
            sb.append(relation.getTargetClass())
                    .append(" ")
                    .append(relation.getType().getPlantUmlText())
                    .append(" ")
                    .append(sourceClassName)
                    .append("\n");
        }

        return sb.toString();
    }

    // 特定のターゲットクラスとの関係を削除
    public void removeRelationshipsWith(String targetClass) {
        relationshipsByTarget.remove(targetClass);
    }

    // 特定の種類の関係を全て削除
    public void removeRelationshipsOfType(RelationType type) {
        relationshipsByTarget.values()
                .removeIf(relations -> relations.removeIf(relation -> relation.getType() == type));
    }

    // 関係の検証（例：循環参照のチェックなど）
    public List<String> validateRelationships() {
        List<String> issues = new ArrayList<>();

        // 継承の重複チェック
        long inheritanceCount = getRelationshipsOfType(RelationType.INHERITANCE).size();
        if (inheritanceCount > 1) {
            issues.add("Multiple inheritance detected: " + inheritanceCount + " parent classes");
        }

        // その他の検証ルール
        return issues;
    }
}