package io.github.morichan.retuss.translator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.VoidType;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.value.DefaultValue;
import io.github.morichan.fescue.feature.value.expression.OneIdentifier;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.Model;
import io.github.morichan.retuss.model.uml.Class;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Translator {

    /**
     * <p>ソースコードのASTをUMLデータに変換する。</p>
     */
    public List<Class> translateCodeToUml(CompilationUnit compilationUnit) {

        List<Class> umlClassList = new ArrayList<>();

        List<ClassOrInterfaceDeclaration> classList = compilationUnit.findAll(ClassOrInterfaceDeclaration.class);
        for(ClassOrInterfaceDeclaration codeClass : classList) {
            // クラスの生成
            Class umlClass = new Class(codeClass.getNameAsString());
            umlClass.setAbstruct(codeClass.isAbstract());

            // 属性の生成 (汎化関係は属性を元に判断するが、特別な処理はしない)
            List<FieldDeclaration> fieldDeclarationListList = codeClass.findAll(FieldDeclaration.class);
            for(FieldDeclaration fieldDeclaration : fieldDeclarationListList) {
                for(VariableDeclarator variableDeclarator : fieldDeclaration.getVariables()) {
                    Attribute attribute = new Attribute(new Name(variableDeclarator.getNameAsString()));
                    attribute.setVisibility(toVisibility(fieldDeclaration.getModifiers()));
                    attribute.setType(new Type(variableDeclarator.getTypeAsString()));
                    if(variableDeclarator.getInitializer().isPresent()){
                        attribute.setDefaultValue(new DefaultValue(new OneIdentifier(variableDeclarator.getInitializer().get().toString())));
                    }
                    umlClass.addAttribute(attribute);
                }
            }

            // 操作の生成
            List<MethodDeclaration> methodDeclarationList = codeClass.findAll(MethodDeclaration.class);
            for(MethodDeclaration methodDeclaration : methodDeclarationList) {
                Operation operation = new Operation(new Name(methodDeclaration.getNameAsString()));
                operation.setVisibility(toVisibility(methodDeclaration.getModifiers()));
                operation.setReturnType(new Type(methodDeclaration.getType().asString()));
                for(Parameter codeParameter : methodDeclaration.getParameters()) {
                    io.github.morichan.fescue.feature.parameter.Parameter umlParameter = new io.github.morichan.fescue.feature.parameter.Parameter(new Name(codeParameter.getNameAsString()));
                    umlParameter.setType(new Type(codeParameter.getTypeAsString()));
                    operation.addParameter(umlParameter);
                }
                umlClass.addOperation(operation);
            }

            // 汎化関係
            if(codeClass.getExtendedTypes().getFirst().isPresent()) {
                String superClassName = codeClass.getExtendedTypes().getFirst().get().getNameAsString();
                // model上で親クラスが見つからない場合は、仮のクラスを作成する
                // 同じファイルに親クラスが宣言されている場合は、考慮しない
                umlClass.setSuperClass(Model.getInstance().findClass(superClassName).orElse(new Class(superClassName, false)));
            }

            umlClassList.add(umlClass);
        }

        return umlClassList;
    }

    /**
     * <p>UMLデータをソースコードのASTに変換する。</p>
     */
    public CompilationUnit translateUmlToCode(List<Class> classList) {
        CompilationUnit newCU = new CompilationUnit();

        newCU.addClass(classList.get(0).getName());

        return newCU;
    }

    public FieldDeclaration translateAttribute(Attribute attribute) {
        NodeList<Modifier> modifiers = new NodeList<>();
        toModifierKeyword(attribute.getVisibility()).ifPresent(modifierKeyword -> modifiers.add(new Modifier(modifierKeyword)));

        com.github.javaparser.ast.type.Type type = toJavaType(attribute.getType());
        String name = attribute.getName().getNameText();
        // 初期値を持つFieldDeclarationの作り方がわからず、現状は初期値も変数名が持っている
        try {
            name += " = " + attribute.getDefaultValue();
        } catch (IllegalStateException e){

        }
        return new FieldDeclaration(modifiers, type, name);
    }

    private Visibility toVisibility(List<Modifier> modifierList) {
        for(Modifier modifier : modifierList) {
            Modifier.Keyword keyword = modifier.getKeyword();
            if(keyword == Modifier.Keyword.PUBLIC) {
                return Visibility.Public;
            } else if(keyword == Modifier.Keyword.PROTECTED) {
                return Visibility.Protected;
            } else if(keyword == Modifier.Keyword.PRIVATE) {
                return Visibility.Private;
            }
        }
        // (独自仕様) Javaで上記のいずれも記述されていない場合は、UMLでPackageとして扱う
        return Visibility.Package;
    }

    private Optional<Modifier.Keyword> toModifierKeyword(Visibility visibility) {
        if(visibility == Visibility.Public) {
            return Optional.of(Modifier.Keyword.PUBLIC);
        } else if(visibility == Visibility.Protected) {
            return Optional.of(Modifier.Keyword.PROTECTED);
        } else if(visibility == Visibility.Private) {
            return Optional.of(Modifier.Keyword.PRIVATE);
        } else {
            // (独自仕様) UMLのPackageは、Javaでアクセス修飾子なしとして扱う
            return Optional.empty();
        }
    }

    private com.github.javaparser.ast.type.Type toJavaType(Type umlType) {
        String typeName = umlType.getName().getNameText();
        if(typeName.equals("void")) {
            return new VoidType();
        }

        try {
            return new PrimitiveType(PrimitiveType.Primitive.valueOf(typeName));
        } catch (IllegalArgumentException e) {
            ClassOrInterfaceType classOrInterfaceType = new ClassOrInterfaceType();
            classOrInterfaceType.setName(new SimpleName(typeName));
            return classOrInterfaceType;
        }
    }
}
