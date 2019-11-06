package io.github.morichan.retuss.window.diagram.sequence;

import java.util.ArrayList;

public class InteractionOperand {
    private String guard;
    private ArrayList<InteractionFragment> interactionFragmentList = new ArrayList<InteractionFragment>();

    public InteractionOperand(String guard) {
        this.guard = guard;
    }

    public void addInteractionFragment(InteractionFragment interactionFragment) {
        this.interactionFragmentList.add(interactionFragment);
    }

    public ArrayList<InteractionFragment> getInteractionFragmentList() {
        return this.interactionFragmentList;
    }

    public void draw() {

    }
}