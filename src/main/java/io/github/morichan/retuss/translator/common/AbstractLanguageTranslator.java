package io.github.morichan.retuss.translator.common;

import io.github.morichan.retuss.model.uml.Class;
import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.fescue.feature.visibility.Visibility;
import io.github.morichan.fescue.feature.type.Type;
import java.util.List;
import java.util.Optional;

public abstract class AbstractLanguageTranslator {
    protected final CodeToUmlTranslator codeToUmlTranslator;
    protected final UmlToCodeTranslator umlToCodeTranslator;

    public AbstractLanguageTranslator() {
        this.codeToUmlTranslator = createCodeToUmlTranslator();
        this.umlToCodeTranslator = createUmlToCodeTranslator();
    }

    public List<Class> translateCodeToUml(String code) {
        return codeToUmlTranslator.translate(code);
    }

    public String translateUmlToCode(List<Class> classes) {
        return umlToCodeTranslator.translate(classes);
    }

    // 基本的な変換メソッド (publicに変更)
    public abstract String translateVisibility(Visibility visibility);

    public abstract String translateType(Type type);

    public abstract String translateAttribute(Attribute attribute);

    public abstract String translateOperation(Operation operation);

    // クラス名抽出メソッドを追加
    public abstract Optional<String> extractClassName(String code);

    // Factory メソッド
    protected abstract CodeToUmlTranslator createCodeToUmlTranslator();

    protected abstract UmlToCodeTranslator createUmlToCodeTranslator();

    // クラス図生成
    public abstract String generateClassDiagram(List<Class> classes);

    /**
     * ヘッダーとソースファイルの両方を解析してシーケンス図を生成
     */
    public abstract String generateSequenceDiagram(
            String headerCode,
            String implCode,
            String methodName);
}