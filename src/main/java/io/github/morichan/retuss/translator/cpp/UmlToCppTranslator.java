package io.github.morichan.retuss.translator.cpp;

import io.github.morichan.retuss.translator.common.UmlToCodeTranslator;
import io.github.morichan.retuss.translator.cpp.util.CppTypeMapper;
import io.github.morichan.retuss.translator.cpp.util.CppVisibilityMapper;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.Class;
import java.util.*;

public class UmlToCppTranslator implements UmlToCodeTranslator {
    private final CppTypeMapper typeMapper;
    private final CppVisibilityMapper visibilityMapper;

    public UmlToCppTranslator() {
        this.typeMapper = new CppTypeMapper();
        this.visibilityMapper = new CppVisibilityMapper();
    }

    @Override
    public String translate(List<Class> classes) {
        StringBuilder code = new StringBuilder();
        addHeaders(code, classes);
        addHeaderGuard(code, classes);
        addClassDefinitions(code, classes);
        closeHeaderGuard(code, classes);
        return code.toString();
    }

    private void addHeaders(StringBuilder code, List<Class> classes) {
        Set<String> includes = collectRequiredIncludes(classes);
        for (String include : includes) {
            code.append("#include ").append(include).append("\n");
        }
        code.append("\n");
    }

    private Set<String> collectRequiredIncludes(List<Class> classes) {
        Set<String> includes = new HashSet<>();
        includes.add("<string>");

        for (Class cls : classes) {
            for (Attribute attr : cls.getAttributeList()) {
                String type = attr.getType().toString();
                if (type.contains("vector"))
                    includes.add("<vector>");
                if (type.contains("map"))
                    includes.add("<map>");
            }
        }

        return includes;
    }

    private void addHeaderGuard(StringBuilder code, List<Class> classes) {
        if (!classes.isEmpty()) {
            String guardName = classes.get(0).getName().toUpperCase() + "_H";
            code.append("#ifndef ").append(guardName).append("\n");
            code.append("#define ").append(guardName).append("\n\n");
        }
    }

    private void closeHeaderGuard(StringBuilder code, List<Class> classes) {
        if (!classes.isEmpty()) {
            code.append("#endif // ")
                    .append(classes.get(0).getName().toUpperCase())
                    .append("_H\n");
        }
    }

    private void addClassDefinitions(StringBuilder code, List<Class> classes) {
        for (Class cls : classes) {
            code.append(translateClass(cls)).append("\n\n");
        }
    }

    private String translateClass(Class umlClass) {
        StringBuilder builder = new StringBuilder();

        // クラスコメント（アクティブクラスの場合）
        if (umlClass.getActive()) {
            builder.append("// Active class\n");
        }

        // クラス宣言
        builder.append("class ").append(umlClass.getName());

        // 継承
        if (umlClass.getSuperClass().isPresent()) {
            builder.append(" : public ")
                    .append(umlClass.getSuperClass().get().getName());
        }

        builder.append(" {\n");

        // メンバーを可視性でグループ化して出力
        Map<Visibility, List<Attribute>> attributesByVisibility = new HashMap<>();
        Map<Visibility, List<Operation>> operationsByVisibility = new HashMap<>();

        // 属性のグループ化
        for (Attribute attr : umlClass.getAttributeList()) {
            attributesByVisibility
                    .computeIfAbsent(attr.getVisibility(), k -> new ArrayList<>())
                    .add(attr);
        }

        // メソッドのグループ化
        for (Operation op : umlClass.getOperationList()) {
            operationsByVisibility
                    .computeIfAbsent(op.getVisibility(), k -> new ArrayList<>())
                    .add(op);
        }

        // 属性の出力
        for (Map.Entry<Visibility, List<Attribute>> entry : attributesByVisibility.entrySet()) {
            builder.append(visibilityMapper.toSourceCode(entry.getKey())).append(":\n");
            for (Attribute attr : entry.getValue()) {
                builder.append("    ")
                        .append(translateAttribute(attr))
                        .append(";\n");
            }
            builder.append("\n");
        }

        // メソッドの出力
        for (Map.Entry<Visibility, List<Operation>> entry : operationsByVisibility.entrySet()) {
            builder.append(visibilityMapper.toSourceCode(entry.getKey())).append(":\n");
            for (Operation op : entry.getValue()) {
                if (umlClass.getAbstruct() && op.toString().contains("abstract")) {
                    builder.append("    virtual ")
                            .append(translateOperation(op))
                            .append(" = 0;\n");
                } else {
                    builder.append("    ")
                            .append(translateOperation(op))
                            .append(";\n");
                }
            }
            builder.append("\n");
        }

        builder.append("};\n");

        return builder.toString();
    }

    private String translateAttribute(Attribute attribute) {
        StringBuilder builder = new StringBuilder();

        // constメンバの場合
        if (attribute.toString().contains("const")) {
            builder.append("const ");
        }

        // 型と名前
        builder.append(typeMapper.mapType(attribute.getType().toString()))
                .append(" ")
                .append(attribute.getName().getNameText());

        return builder.toString();
    }

    private String translateOperation(Operation operation) {
        StringBuilder builder = new StringBuilder();

        // 仮想関数の場合
        if (operation.toString().contains("virtual")) {
            builder.append("virtual ");
        }

        // 戻り値の型と関数名
        builder.append(typeMapper.mapType(operation.getReturnType().toString()))
                .append(" ")
                .append(operation.getName().getNameText())
                .append("(");

        // パラメータ
        List<String> params = new ArrayList<>();
        operation.getParameters().forEach(param -> params.add(String.format("%s %s",
                typeMapper.mapType(param.getType().toString()),
                param.getName().getNameText())));
        builder.append(String.join(", ", params));

        builder.append(")");

        return builder.toString();
    }
}
