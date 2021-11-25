package io.github.morichan.retuss.model.uml;

public class InteractionOperad extends InteractionFragment {
    private String guard = "";

    public InteractionOperad(Lifeline lifeline, String guard) {
        super.setLifeline(lifeline);
        this.guard = guard;
    }
}
