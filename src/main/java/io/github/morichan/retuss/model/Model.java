package io.github.morichan.retuss.model;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import io.github.morichan.fescue.feature.value.expression.symbol.Mod;
import io.github.morichan.retuss.controller.CodeController;
import io.github.morichan.retuss.controller.UmlController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p> RETUSS内のデータ構造を保持するクラス </p>
 * <p> Singletonデザインパターンにより、インスタンスが1つであることを保証する。 </p>
 */
public class Model {
    private static Model model = new Model();
    private UmlController umlController;
    private CodeController codeController;
    private List<CodeFile> codeFileList = new ArrayList<>();


    private Model() { }

    public static Model getInstance() {
        return model;
    }

    public void setUmlController(UmlController umlController) {
        this.umlController = umlController;
    }

    public void setCodeController(CodeController codeController) {
        this.codeController = codeController;
    }

    public List<CodeFile> getCodeFileList() {
        return Collections.unmodifiableList(codeFileList);
    }

    public void addCodeFile() {
        CodeFile newCodeFile = new CodeFile();
        codeFileList.add(newCodeFile);
        codeController.updateCodeTab(newCodeFile);
    }

    /**
     * <p>ソースコードを構文解析し、構文木をCodeFileにセットする。</p>
     * @param changedCodeFile 変更対象ファイル
     * @param code 変更後のソースコード
     */
    public void updateCodeFile(CodeFile changedCodeFile, String code) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(code);
            changedCodeFile.setCompilationUnit(compilationUnit);
            System.out.println(changedCodeFile.getCode());
        } catch (Exception e) {
            // JavaParserは、Javaの構文に従っていないコードは構文解析できない
            System.out.println("Cannot parse.");
            return;
        }
    }
}
