package io.github.morichan.retuss.window.diagram.sequence;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.geometry.Point2D;
import java.util.ArrayList;

public class CombinedFragment extends InteractionFragment {
    private InteractionOperandKind kind;
    private ArrayList<InteractionOperand> interactionOperandList = new ArrayList<InteractionOperand>();

    public CombinedFragment(InteractionOperandKind kind) {
        this.kind = kind;
    }

    public void addInteractionOperand(InteractionOperand interactionOperand) {
        this.interactionOperandList.add(interactionOperand);
    }

    public ArrayList<InteractionOperand> getInteractionOperandList() {
        return this.interactionOperandList;
    }

    public void draw(GraphicsContext gc, Point2D lastDrawPoint, Lifeline fromLifeline) {
        double width = 10.0;
        double height = 100;

        gc.setStroke(Color.RED);
        gc.strokeRect(lastDrawPoint.getX(), lastDrawPoint.getY(), width, height);

        // for (InteractionOperand io : interactionOperandList) {
        // ArrayList<InteractionFragment> interactionFragmentList =
        // io.getInteractionFragmentList();
        // if (interactionFragmentList.size() > 0) {
        // for (InteractionFragment interactionFragment : interactionFragmentList) {
        // interactionFragment.getMessage().draw(gc);
        // }
        // }
        // }
    }
}