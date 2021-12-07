package io.github.morichan.retuss.model.uml;

public class InteractionOperand extends InteractionFragment {
    private String guard = "";

    public InteractionOperand(Lifeline lifeline, String guard) {
        super.setLifeline(lifeline);
        this.guard = guard;
    }

    public String getGuard() {
        return guard;
    }
}
