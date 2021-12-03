package io.github.morichan.retuss.model.uml;

public class OccurenceSpecification extends InteractionFragment {
    private Message message;

    public OccurenceSpecification(Lifeline lifeline) {
        super.setLifeline(lifeline);
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
}
