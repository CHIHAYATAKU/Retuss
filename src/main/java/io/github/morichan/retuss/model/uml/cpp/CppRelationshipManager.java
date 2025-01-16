package io.github.morichan.retuss.model.uml.cpp;

import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import java.util.*;
import java.util.stream.Collectors;

public class CppRelationshipManager {
    private final Map<String, Set<RelationshipInfo>> targetClassNameRelationshipInfoMap = new HashMap<>();
    private final String sourceClassName; // 関係元のクラス名

    public CppRelationshipManager(String sourceClassName) {
        this.sourceClassName = sourceClassName;
    }

    public String getSourceClassname() {
        return sourceClassName;
    }

    // 関係の追加
    public void addRelationship(RelationshipInfo relationship) {
        targetClassNameRelationshipInfoMap.computeIfAbsent(relationship.getTargetClass(), k -> new HashSet<>())
                .add(relationship);
    }

    public void addRealization(String interfaceName) {
        RelationshipInfo relation = new RelationshipInfo(interfaceName, RelationType.REALIZATION);
        addRelationship(relation);
    }

    // 継承関係の追加
    public void addGeneralization(String targetClass) {
        RelationshipInfo relation = new RelationshipInfo(targetClass, RelationType.INHERITANCE);
        addRelationship(relation);
    }

    // コンポジション関係の追加
    public void addComposition(String targetClass, String memberName, String multiplicity, Visibility visibility) {
        RelationshipInfo relation = new RelationshipInfo(targetClass, RelationType.COMPOSITION);
        relation.setElement(memberName, ElementType.ATTRIBUTE, multiplicity, visibility);
        addRelationship(relation);
    }

    // 集約関係の追加
    public void addAggregation(String targetClass, String memberName, String multiplicity, Visibility visibility) {
        RelationshipInfo relation = new RelationshipInfo(targetClass, RelationType.AGGREGATION);
        relation.setElement(memberName, ElementType.ATTRIBUTE, multiplicity, visibility);
        addRelationship(relation);
    }

    public void addAssociation(String targetClass, String memberName, String multiplicity, Visibility visibility) {
        RelationshipInfo relation = new RelationshipInfo(targetClass, RelationType.ASSOCIATION);
        relation.setElement(memberName, ElementType.ATTRIBUTE, multiplicity, visibility);
        addRelationship(relation);
    }

    // public void addReturnTypeDependency(String targetClass, String memberName,
    // Visibility visibility) {
    // RelationshipInfo relation = new RelationshipInfo(targetClass,
    // RelationType.DEPENDENCY);
    // relation.setElement(
    // memberName,
    // ElementType.OPERATION,
    // "1",
    // Visibility visibility,
    // String type,
    // String returnType,
    // String defaultValue,
    // boolean isPureVirtual,
    // Set<Modifier> modifiers);
    // addRelationship(relation);
    // }

    // 指定したターゲットクラスとの関係を取得
    public Set<RelationshipInfo> getRelationshipsWith(String targetClass) {
        return Collections.unmodifiableSet(
                targetClassNameRelationshipInfoMap.getOrDefault(targetClass, new HashSet<>()));
    }

    // 全ての関係を取得
    public Set<RelationshipInfo> getAllRelationships() {
        return targetClassNameRelationshipInfoMap.values().stream()
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
        Map<String, Set<RelationType>> dependencyMap = new HashMap<>();

        // 関係の分類と処理
        for (RelationshipInfo relation : getAllRelationships()) {
            if (relation.getType().isDependency()) {
                // 依存関係の収集
                dependencyMap.computeIfAbsent(relation.getTargetClass(), k -> new HashSet<>())
                        .add(relation.getType());
            } else if (relation.getType() == RelationType.INHERITANCE ||
                    relation.getType() == RelationType.REALIZATION) {
                // 継承/実現関係の処理
                String targetClass = relation.getTargetClass();
                if (relation.getType() == RelationType.REALIZATION ||
                        !inheritanceMap.containsKey(targetClass)) {
                    inheritanceMap.put(targetClass, relation);
                }
            } else {
                // その他の関係（コンポジション、集約、関連）の処理
                appendRegularRelationship(sb, relation);
            }
        }

        // 依存関係の出力
        appendDependencyRelationships(sb, dependencyMap);

        // 継承/実現関係の出力（最後に出力）
        appendInheritanceRelationships(sb, inheritanceMap);

        return sb.toString();
    }

    private void appendRegularRelationship(StringBuilder sb, RelationshipInfo relation) {
        sb.append(sourceClassName)
                .append(" ")
                .append(relation.getType().getPlantUmlText())
                .append(" \"");

        RelationshipElement elem = relation.getElement();
        if (elem != null) {
            sb.append(elem.getMultiplicity() != null ? elem.getMultiplicity() : "1");
        }

        sb.append("\" ")
                .append(relation.getTargetClass());

        if (elem != null) {
            sb.append(" : ").append(elem.getVisibility()).append(elem.getName());
        }

        sb.append("\n");
    }

    private void appendDependencyRelationships(StringBuilder sb,
            Map<String, Set<RelationType>> dependencyMap) {
        for (Map.Entry<String, Set<RelationType>> entry : dependencyMap.entrySet()) {
            sb.append(sourceClassName)
                    .append(" ..> ")
                    .append(entry.getKey())
                    .append(" : <<");

            // ステレオタイプをソートして結合
            String stereotypes = entry.getValue().stream()
                    .map(RelationType::getStereotype)
                    .sorted()
                    .collect(Collectors.joining(", "));

            sb.append(stereotypes)
                    .append(">>\n");
        }
    }

    private void appendInheritanceRelationships(StringBuilder sb,
            Map<String, RelationshipInfo> inheritanceMap) {
        for (RelationshipInfo relation : inheritanceMap.values()) {
            sb.append(sourceClassName)
                    .append(" ")
                    .append(relation.getType().getPlantUmlText())
                    .append(" ")
                    .append(relation.getTargetClass())
                    .append("\n");
        }
    }

    // 特定のターゲットクラスとの関係を削除
    public void removeRelationshipsWith(String targetClass) {
        targetClassNameRelationshipInfoMap.remove(targetClass);
    }

    // 特定の種類の関係を全て削除
    public void removeRelationshipsOfType(RelationType type) {
        targetClassNameRelationshipInfoMap.values()
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