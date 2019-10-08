package io.github.morichan.retuss.window.diagram.sequence;

// import java.util.ArrayList;

public class InteractionOperand {
    private String guard;
    private Interaction interaction;

    public InteractionOperand(String guard, Interaction interaction) {
        this.guard = guard;
        this.interaction = interaction;
    }

    public InteractionOperand(Interaction interaction) {
        this.interaction = interaction;
    }

    public void draw() {

    }
}