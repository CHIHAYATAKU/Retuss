package io.github.morichan.retuss.translator.cpp.header.analyzers.member;

import java.util.ArrayList;
import java.util.List;

public class TemplateTypeHelper {
    // テンプレート型の完全な解析（入れ子対応）
    public static List<String> extractAllTypes(String type) {
        List<String> types = new ArrayList<>();
        if (!type.contains("<")) {
            types.add(cleanType(type));
            return types;
        }

        // まず基本の型を追加
        String baseName = type.substring(0, type.indexOf("<"));
        types.add(cleanType(baseName));

        // パラメータの解析
        String params = extractTemplateParameters(type);
        for (String param : splitTemplateParameters(params)) {
            types.addAll(extractAllTypes(param.trim()));
        }

        return types;
    }

    // テンプレートのパラメータ部分を抽出
    private static String extractTemplateParameters(String type) {
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

    // パラメータをカンマで分割（ネストレベル考慮）
    private static List<String> splitTemplateParameters(String params) {
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

    // 型名のクリーンアップ
    private static String cleanType(String type) {
        return type.replaceAll("std::", "")
                .replaceAll("(static|const|mutable|final)", "")
                .replaceAll("[*&]", "")
                .trim();
    }
}