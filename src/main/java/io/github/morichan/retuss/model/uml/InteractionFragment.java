package io.github.morichan.retuss.model.uml;

import java.util.ArrayList;

public abstract class InteractionFragment {
    private Lifeline lifeline;
    private ArrayList<InteractionFragment> interactionFragmentList = new ArrayList<>();

    public Lifeline getLifeline() {
        return lifeline;
    }

    public void setLifeline(Lifeline lifeline) {
        this.lifeline = lifeline;
    }

    public ArrayList<InteractionFragment> getInteractionFragmentList() {
        return interactionFragmentList;
    }

    public void setInteractionFragmentList(ArrayList<InteractionFragment> interactionFragmentList) {
        this.interactionFragmentList = interactionFragmentList;
    }
}
