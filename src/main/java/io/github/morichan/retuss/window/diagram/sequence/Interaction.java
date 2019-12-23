package io.github.morichan.retuss.window.diagram.sequence;

import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;

import java.util.ArrayList;

public class Interaction {

    MessageOccurrenceSpecification message;
    Class umlClass;
    OperationGraphic operationGraphic;

    public Interaction(Class umlClass, OperationGraphic og) {
        setUmlClass(umlClass);
        setOperationGraphic(og);
    }

    public Interaction(MessageOccurrenceSpecification message) {
        this.message = message;
    }

    public void setUmlClass(Class umlClass) {
        this.umlClass = umlClass;
    }

    public void setOperationGraphic(OperationGraphic operationGraphic) {
        this.operationGraphic = operationGraphic;
    }

    public MessageOccurrenceSpecification getMessage() {
        return message;
    }

    public void resetLifelineFlag() {
        resetLifelineFlag(message);
    }

    public void resetLifelineFlag(MessageOccurrenceSpecification message) {
        for (MessageOccurrenceSpecification childMessage : message.getMessages()) {
            // TODO: 再帰呼び出しの場合に、ここで無限ループになる
            resetLifelineFlag(childMessage);
        }
        message.getLifeline().setCalculated(false);
        message.getLifeline().setDrawn(false);
    }

    public void draw(GraphicsContext gc) {
        // Lifeline lifeline = new Lifeline();
        // lifeline.setClassName(umlClass.getName());

        // message = new MessageOccurrenceSpecification();
        // message.setName(operationGraphic.getOperation().toString());
        // message.setLifeline(lifeline);
        message.calculatePoint();
        message.draw(gc);
    }

    public InteractionFragment searchNearbyInteractionFragment(double mouseX, double mouseY) {
        double limitDistance = 100.0;
        double minDistance = 9999.9;
        double manhattanDistance = 0.0;
        InteractionFragment nearbyInteractionFragment = new InteractionFragment();

        // 複合フラグメントの入れ子には対応していない
        for (InteractionFragment interactionFragment : message.getInteractionFragmentList()) {
            if (interactionFragment instanceof CombinedFragment) {
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                Point2D beginPoint = cf.getBeginPoint();
                manhattanDistance = calcManhattanDistance(mouseX, mouseY, beginPoint.getX(), beginPoint.getY());
                if (manhattanDistance < minDistance) {
                    minDistance = manhattanDistance;
                    nearbyInteractionFragment = cf;
                }

                for (InteractionOperand io: cf.getInteractionOperandList()) {
                    for (InteractionFragment interactionFragmentInCf : io.getInteractionFragmentList()) {
                        Point2D endPoint = interactionFragmentInCf.getMessage().getEndPoint();
                        manhattanDistance = calcManhattanDistance(mouseX, mouseY, endPoint.getX(), endPoint.getY());
                        if (manhattanDistance < minDistance) {
                            minDistance = manhattanDistance;
                            nearbyInteractionFragment = interactionFragmentInCf;
                        }
                    }
                }
            } else {
                Point2D endPoint = interactionFragment.getMessage().getEndPoint();
                manhattanDistance = calcManhattanDistance(mouseX, mouseY, endPoint.getX(), endPoint.getY());
                if (manhattanDistance < minDistance) {
                    minDistance = manhattanDistance;
                    nearbyInteractionFragment = interactionFragment;
                }
            }
        }

        if (minDistance < limitDistance) {
            return nearbyInteractionFragment;
        } else {
            return null;
        }
    }

    public void addCombinedFragment(InteractionOperandKind interactionOperandKind, ArrayList<InteractionOperand> interactionOperandList) {
        CombinedFragment newCF = new CombinedFragment(interactionOperandKind);
        newCF.setInteractionOperandList(interactionOperandList);
        message.getInteractionFragmentList().add(newCF);
    }

    public void addCombinedFragment(InteractionOperandKind interactionOperandKind, ArrayList<InteractionOperand> interactionOperandList, String textNextToKind) {
        CombinedFragment newCF = new CombinedFragment(interactionOperandKind);
        newCF.setInteractionOperandList(interactionOperandList);
        newCF.setTextNextToKind(textNextToKind);
        message.getInteractionFragmentList().add(newCF);
    }

    public void deleteInteractionFragment(InteractionFragment target) {
        if (message.getInteractionFragmentList().remove(target)) {
            return;
        }

        // 複合フラグメント内のメッセージを削除する場合
        for (InteractionFragment interactionFragment : message.getInteractionFragmentList()) {
            if (interactionFragment instanceof CombinedFragment) {
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                for (InteractionOperand io : cf.getInteractionOperandList()) {
                    if (io.getInteractionFragmentList().remove(target)) {
                        return;
                    }
                }
            }
        }
    }

    private double calcManhattanDistance(double x1, double y1, double x2, double y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    @Deprecated
    private void drawOriginal(GraphicsContext gc) {
        Lifeline lifeline = new Lifeline();
        lifeline.setClassName(umlClass.getName());

        Lifeline another = new Lifeline();
        another.setClassName("ClassName");
        Lifeline another2 = new Lifeline();
        another2.setClassName("ClassName2");
        // Lifeline another3 = new Lifeline();
        // another3.setType(umlClass);

        MessageOccurrenceSpecification otherMessage = new MessageOccurrenceSpecification();
        otherMessage.setName("reprint");
        otherMessage.setLifeline(another);

        MessageOccurrenceSpecification anotherMessage = new MessageOccurrenceSpecification();
        anotherMessage.setName("reprint2");
        anotherMessage.setLifeline(another2);
        // otherMessage.addMessage(anotherMessage);

        MessageOccurrenceSpecification another3Message = new MessageOccurrenceSpecification();
        another3Message.setName("reprint3");
        another3Message.setLifeline(another);

        message = new MessageOccurrenceSpecification();
        message.setName(operationGraphic.getOperation().toString());
        message.setLifeline(lifeline);
        message.addMessage(otherMessage);
        message.addMessage(anotherMessage);
        message.addMessage(another3Message);
        message.calculatePoint();
        message.draw(gc);
    }
}
