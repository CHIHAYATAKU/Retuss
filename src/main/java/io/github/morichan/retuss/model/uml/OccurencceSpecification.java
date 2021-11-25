package io.github.morichan.retuss.model.uml;

public class OccurencceSpecification extends InteractionFragment {
    private Message message;

    public OccurencceSpecification(Lifeline lifeline, Message message) {
        super.setLifeline(lifeline);
        this.message = message;
    }
}
