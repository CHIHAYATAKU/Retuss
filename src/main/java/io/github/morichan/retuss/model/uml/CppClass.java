package io.github.morichan.retuss.model.uml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.morichan.fescue.feature.Attribute;
import io.github.morichan.fescue.feature.Operation;

public class CppClass extends Class {
    // メンバー名と修飾子のマッピング
    private Map<String, List<String>> memberModifiers = new HashMap<>();

    public CppClass(String name) {
        super(name);
    }

    public CppClass(String name, Boolean isActive) {
        super(name, isActive);
    }

    // 修飾子の追加
    public void addMemberModifiers(String memberName, List<String> modifiers) {
        memberModifiers.put(memberName, modifiers);
    }

    // 修飾子の取得
    public List<String> getModifiers(String memberName) {
        return memberModifiers.getOrDefault(memberName, new ArrayList<>());
    }

    // 既存のメソッドをオーバーライド
    @Override
    public void addOperation(Operation operation) {
        super.addOperation(operation);
        // 必要に応じて修飾子の初期化
        if (!memberModifiers.containsKey(operation.getName().getNameText())) {
            memberModifiers.put(operation.getName().getNameText(), new ArrayList<>());
        }
    }

    @Override
    public void addAttribute(Attribute attribute) {
        super.addAttribute(attribute);
        // 必要に応じて修飾子の初期化
        if (!memberModifiers.containsKey(attribute.getName().getNameText())) {
            memberModifiers.put(attribute.getName().getNameText(), new ArrayList<>());
        }
    }
}