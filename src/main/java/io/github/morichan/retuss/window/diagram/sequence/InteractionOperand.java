package io.github.morichan.retuss.window.diagram.sequence;

import java.util.ArrayList;

public class InteractionOperand {
    private String guard;
    private ArrayList<InteractionFragment> interactionFragmentList = new ArrayList<InteractionFragment>();
    private double beginPointY;
    private static final double  MIN_HEIGHT = 30.0;
    private double height = MIN_HEIGHT;

    public InteractionOperand() {
        this.guard = "";
    }

    public InteractionOperand(String guard) {
        this.guard = guard;
    }

    public String getGuard(){
        return this.guard;
    }

    public void addInteractionFragment(InteractionFragment interactionFragment) {
        this.interactionFragmentList.add(interactionFragment);
    }

    public ArrayList<InteractionFragment> getInteractionFragmentList() {
        return this.interactionFragmentList;
    }

    public void setBeginPointY(double beginPointY) {
        this.beginPointY = beginPointY;
    }

    public double getBeginPointY() {
        return beginPointY;
    }

    public void setHeight(double height) {
        if (height > MIN_HEIGHT) {
            this.height = height;
        }
    }

    public double getHeight() {
        return height;
    }
}