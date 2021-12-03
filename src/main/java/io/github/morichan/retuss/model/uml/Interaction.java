package io.github.morichan.retuss.model.uml;

import io.github.morichan.fescue.feature.Operation;

import java.util.ArrayList;

public class Interaction {
    private Operation operation;
    private String name = "";
    private ArrayList<InteractionFragment> interactionFragmentList = new ArrayList<>();

    public Interaction(Operation operation, String name) {
        this.operation = operation;
        this.name = name;
    }

    public Operation getOperation() {
        return operation;
    }

    public String getName() {
        return name;
    }

    public ArrayList<InteractionFragment> getInteractionFragmentList() {
        return interactionFragmentList;
    }
}
