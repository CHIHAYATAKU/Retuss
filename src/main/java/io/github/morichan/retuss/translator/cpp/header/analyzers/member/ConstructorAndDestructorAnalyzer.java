package io.github.morichan.retuss.translator.cpp.header.analyzers.member;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.name.Name;
import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.retuss.model.uml.cpp.*;
import io.github.morichan.retuss.model.uml.cpp.utils.*;
import io.github.morichan.retuss.parser.cpp.CPP14Parser;
import io.github.morichan.retuss.translator.cpp.header.analyzers.base.AbstractAnalyzer;

import org.antlr.v4.runtime.ParserRuleContext;
import java.util.*;
import java.util.stream.Collectors;

public class ConstructorAndDestructorAnalyzer extends AbstractAnalyzer {
    @Override
    public boolean appliesTo(ParserRuleContext context) {
        if (!(context instanceof CPP14Parser.MemberdeclarationContext))
            return false;

        CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
        if (ctx.memberDeclaratorList() == null)
            return false;

        // declSpecifierSeqがない、またはvirtualのみの場合、コンストラクタ/デストラクタの可能性
        if (ctx.declSpecifierSeq() == null || ctx.declSpecifierSeq().getText().equals("virtual")) {
            for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                if (memberDec.declarator() != null) {
                    String text = memberDec.declarator().getText();
                    System.out.println("DEBUG: Checking constructor/destructor: " + text);
                    // 戻り値の型指定がないメソッド風の宣言ならコンストラクタ/デストラクタ
                    return text.contains("(");
                }
            }
        }
        return false;
    }

    @Override
    protected void analyzeInternal(ParserRuleContext context) {
        try {
            CPP14Parser.MemberdeclarationContext ctx = (CPP14Parser.MemberdeclarationContext) context;
            CppHeaderClass currentHeaderClass = this.context.getCurrentHeaderClass();
            if (currentHeaderClass == null)
                return;

            // 修飾子の抽出
            Set<Modifier> modifiers = new HashSet<>();
            if (ctx.declSpecifierSeq() != null) {
                String rawType = ctx.declSpecifierSeq().getText();
                modifiers = extractModifiers(rawType);
            }

            for (CPP14Parser.MemberDeclaratorContext memberDec : ctx.memberDeclaratorList().memberDeclarator()) {
                if (memberDec.declarator() == null)
                    continue;

                String methodName = extractMethodName(memberDec.declarator());
                Operation operation = new Operation(new Name(methodName));
                operation.setReturnType(new Type("void"));
                operation.setVisibility(convertVisibility(this.context.getCurrentVisibility()));
                operation.setParameters(new ArrayList<>());

                // パラメータ処理
                CPP14Parser.DeclaratorContext declarator = memberDec.declarator();
                if (declarator != null &&
                        declarator.pointerDeclarator() != null &&
                        declarator.pointerDeclarator().noPointerDeclarator() != null) {

                    CPP14Parser.NoPointerDeclaratorContext noPtrDec = declarator.pointerDeclarator()
                            .noPointerDeclarator();

                    for (int i = 0; i < noPtrDec.getChildCount(); i++) {
                        if (noPtrDec.getChild(i) instanceof CPP14Parser.ParametersAndQualifiersContext) {
                            processParameters(
                                    (CPP14Parser.ParametersAndQualifiersContext) noPtrDec.getChild(i),
                                    operation);
                            break;
                        }
                    }
                }

                currentHeaderClass.addOperation(operation);
                for (Modifier modifier : modifiers) {
                    currentHeaderClass.addMemberModifier(methodName, modifier);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing constructor/destructor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String processOperationType(String type) {
        Map<String, String> standardTypes = Map.of(
                "string", "String",
                "vector", "Vector",
                "list", "List",
                "map", "Map",
                "set", "Set",
                "array", "Array");

        // std:: の除去
        String processedType = type.replaceAll("std::", "");

        // 標準型の変換
        for (Map.Entry<String, String> entry : standardTypes.entrySet()) {
            processedType = processedType.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
        }

        return processedType.trim();
    }

    private void processParameters(CPP14Parser.ParametersAndQualifiersContext params, Operation operation) {
        System.out.println("DEBUG: Processing parameters");
        // まず空のパラメータリストで初期化
        operation.setParameters(new ArrayList<>());
        CppHeaderClass currentHeaderClass = this.context.getCurrentHeaderClass();

        // パラメータリストがある場合のみ処理
        if (params.parameterDeclarationClause() != null &&
                params.parameterDeclarationClause().parameterDeclarationList() != null) {
            CPP14Parser.ParameterDeclarationListContext paramList = params.parameterDeclarationClause()
                    .parameterDeclarationList();

            if (paramList != null) {
                for (CPP14Parser.ParameterDeclarationContext paramCtx : paramList.parameterDeclaration()) {
                    try {
                        String paramType = paramCtx.declSpecifierSeq().getText();
                        String paramName = "";
                        String paramModifier = "";

                        System.out.println("DEBUG: Parameter context: " + paramCtx.getText());
                        System.out.println("DEBUG: Parameter type raw: " + paramType);

                        paramType = processOperationType(paramType);
                        paramType = cleanType(paramType);

                        handleCollectionType(paramType, currentHeaderClass);

                        if (paramCtx.declarator() != null) {
                            String fullDeclarator = paramCtx.declarator().getText();
                            System.out.println("DEBUG: Parameter declarator: " + fullDeclarator);

                            // 名前の抽出（修飾子を除去）
                            paramName = cleanName(fullDeclarator);

                            String fullParamType = paramType; // 元の型名を保持
                            if (fullDeclarator.contains("*"))
                                fullParamType += "*";
                            if (fullDeclarator.contains("&"))
                                fullParamType += "&";

                            Parameter param = new Parameter(new Name(paramName));
                            param.setType(new Type(fullParamType));
                            operation.addParameter(param);
                        }

                        analyzeTypeForDependency(paramType, currentHeaderClass);

                        System.out.println("DEBUG: Added parameter - " + paramName + " : " + paramType + paramModifier);
                    } catch (Exception e) {
                        System.err.println("ERROR processing parameter: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void analyzeTypeForDependency(String type, CppHeaderClass currentClass) {
        // テンプレート型の要素を含めて依存関係を抽出
        List<String> allTypes = extractAllTypesFromTemplate(type);
        for (String t : allTypes) {
            if (isUserDefinedType(t)) {
                RelationshipInfo relation = new RelationshipInfo(
                        t,
                        RelationType.DEPENDENCY_PARAMETER);
                currentClass.addRelationship(relation);
            }
        }
    }

    private List<String> extractAllTypesFromTemplate(String type) {
        List<String> types = new ArrayList<>();
        if (!type.contains("<")) {
            types.add(type);
            return types;
        }

        String baseName = type.substring(0, type.indexOf("<"));
        types.add(baseName);

        String params = extractTemplateParameters(type);
        List<String> paramTypes = parseTemplateParameters(params);
        for (String paramType : paramTypes) {
            types.addAll(extractAllTypesFromTemplate(paramType));
        }

        return types;
    }

    private String extractTemplateParameters(String type) {
        int nestLevel = 0;
        int start = type.indexOf('<') + 1;
        int end = -1;

        for (int i = start; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<')
                nestLevel++;
            else if (c == '>') {
                if (nestLevel == 0) {
                    end = i;
                    break;
                }
                nestLevel--;
            }
        }
        return type.substring(start, end);
    }

    private List<String> parseTemplateParameters(String params) {
        List<String> result = new ArrayList<>();
        int nestLevel = 0;
        StringBuilder current = new StringBuilder();

        for (char c : params.toCharArray()) {
            if (c == '<')
                nestLevel++;
            else if (c == '>')
                nestLevel--;
            else if (c == ',' && nestLevel == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private boolean isUserDefinedType(String type) {
        // まず修飾子を除去
        String baseType = type.replaceAll("[*&]", "").trim();
        Set<String> basicTypes = Set.of(
                // 基本型
                "void", "bool", "char", "int", "float", "double", "long", "short",
                "unsigned", "signed",
                // STL型
                "string", "vector", "list", "map", "set", "array", "queue", "stack",
                // スマートポインタ
                "unique_ptr", "shared_ptr", "weak_ptr");

        if (baseType.startsWith("enum ")) {
            return true; // enumは常にユーザー定義型として扱う
        }

        // std::を除去
        baseType = baseType.replaceAll("std::", "");

        if (baseType.contains("<")) {
            baseType = baseType.substring(0, baseType.indexOf("<"));
        }

        return !basicTypes.contains(baseType.toLowerCase());
    }

    private Visibility convertVisibility(String visibility) {
        if (visibility == null)
            return Visibility.Private;

        switch (visibility.toLowerCase()) {
            case "public":
                return Visibility.Public;
            case "protected":
                return Visibility.Protected;
            case "private":
                return Visibility.Private;
            default:
                return Visibility.Private;
        }
    }

    // 追加すべきメソッド
    private void handleCollectionType(String paramType, CppHeaderClass currentClass) {
        if (isCollectionType(paramType)) {
            String elementType = extractElementType(paramType);
            if (isUserDefinedType(elementType)) {
                RelationshipInfo relation = new RelationshipInfo(
                        elementType,
                        RelationType.DEPENDENCY_PARAMETER);
                currentClass.addRelationship(relation);
            }
        }
    }

    private boolean isCollectionType(String type) {
        return type.matches(".*(?:vector|list|map|set|array)<.*>");
    }

    private String extractElementType(String type) {
        if (type.contains("<") && type.contains(">")) {
            String innerType = type.substring(
                    type.indexOf("<") + 1,
                    type.lastIndexOf(">"));
            return innerType.replaceAll("std::", "").trim();
        }
        return type;
    }

    private String cleanName(String name) {
        return name.replaceAll("std::", "").replaceAll("[*&]", "").trim();
    }

    private String extractMethodName(CPP14Parser.DeclaratorContext declarator) {
        if (declarator == null) {
            System.out.println("DEBUG: Declarator is null");
            return null;
        }
        String fullText = declarator.getText();
        System.out.println("DEBUG: Full declarator text: " + fullText);
        // パラメータリストの前で切り取り
        int parenIndex = fullText.indexOf('(');
        if (parenIndex > 0) {
            String nameWithScope = fullText.substring(0, parenIndex);
            // スコープ解決演算子があれば除去
            int scopeIndex = nameWithScope.lastIndexOf("::");
            if (scopeIndex >= 0) {
                return nameWithScope.substring(scopeIndex + 2);
            }
            return nameWithScope;
        }
        return fullText;
    }

    public void appendOperationModifiers(StringBuilder pumlBuilder, Operation op, Set<Modifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty())
            return;

        List<String> modifierStrings = new ArrayList<>();

        // PURE_VIRTUALの場合は{abstract}のみを表示
        if (modifiers.contains(Modifier.PURE_VIRTUAL)) {
            modifierStrings.add("{abstract}");
        } else {
            // それ以外の修飾子を処理
            for (Modifier modifier : modifiers) {
                if (modifier.isApplicableTo(ElementType.OPERATION) &&
                        modifier != Modifier.PURE_VIRTUAL) {
                    modifierStrings.add(modifier.getPlantUmlText(false));
                }
            }
        }

        if (!modifierStrings.isEmpty()) {
            pumlBuilder.append(String.join(" ", modifierStrings))
                    .append(" ");
        }
    }

    private Set<Modifier> extractModifiers(String type) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);

        // virtualをまず検出
        if (type.contains(Modifier.VIRTUAL.getCppText(false)))
            modifiers.add(Modifier.VIRTUAL);
        if (type.contains(Modifier.STATIC.getCppText(false)))
            modifiers.add(Modifier.STATIC);
        if (type.contains(Modifier.READONLY.getCppText(false)))
            modifiers.add(Modifier.READONLY);
        if (type.contains(Modifier.OVERRIDE.getCppText(false)))
            modifiers.add(Modifier.OVERRIDE);
        return modifiers;
    }

    private String cleanType(String type) {
        return type.replaceAll("(virtual|static|const|abstract|override)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}