package io.github.morichan.retuss.model.uml;

import java.util.ArrayList;

public class CombinedFragment extends InteractionFragment{
    private InteractionOperandKind kind;
    private ArrayList<InteractionOperad> interactionOperandList = new ArrayList<>();

    public CombinedFragment(Lifeline lifeline, InteractionOperandKind kind) {
        super.setLifeline(lifeline);
        this.kind = kind;
    }
}
