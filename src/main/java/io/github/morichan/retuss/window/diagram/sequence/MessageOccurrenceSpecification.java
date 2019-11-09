package io.github.morichan.retuss.window.diagram.sequence;

import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageOccurrenceSpecification {

    private Point2D beginPoint = Point2D.ZERO;
    private Point2D endPoint = Point2D.ZERO;

    private MessageType type = MessageType.Undefined;
    private Class umlClass;
    private String name;
    private Map<Integer, String> instanceMap = new HashMap<>();
    private String value;
    private Lifeline lifeline;
    private List<InteractionFragment> interactionFragmentList = new ArrayList<>();

    public Point2D getBeginPoint() {
        return beginPoint;
    }

    public Point2D getEndPoint() {
        return endPoint;
    }

    public void setBeginPoint(Point2D beginPoint) {
        this.beginPoint = beginPoint;
    }

    public void setEndPoint(Point2D endPoint) {
        this.endPoint = endPoint;
    }

    public void setMessageType(MessageType type) {
        this.type = type;
    }

    public MessageType getMessageType() {
        return type;
    }

    public void setType(Class umlClass) {
        this.umlClass = umlClass;
    }

    public Class getType() {
        return umlClass;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void putInstance(int key, String instance) {
        instanceMap.put(key, instance);
    }

    public String getInstance(int key) {
        return instanceMap.get(key);
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setLifeline(Lifeline lifeline) {
        this.lifeline = lifeline;
    }

    public Lifeline getLifeline() {
        return lifeline;
    }

    public void addInteractionFragment(InteractionFragment interactionFragment) {
        this.interactionFragmentList.add(interactionFragment);
    }

    public void addInteractionFragment(int i, MessageOccurrenceSpecification message) {
        InteractionFragment interactionFragment = new InteractionFragment();
        interactionFragment.setMessage(message);
        if (i < interactionFragmentList.size()) {
            interactionFragmentList.set(i, interactionFragment);
        } else {
            interactionFragmentList.add(interactionFragment);
        }
    }

    public List<InteractionFragment> getInteractionFragmentList(){
        return this.interactionFragmentList;
    }

    public void addMessage(MessageOccurrenceSpecification message) {
        InteractionFragment interactionFragment = new InteractionFragment();
        interactionFragment.setMessage(message);
        interactionFragmentList.add(interactionFragment);
    }

    public List<MessageOccurrenceSpecification> getMessages() {
        List<MessageOccurrenceSpecification> messages = new ArrayList<MessageOccurrenceSpecification>();
        MessageOccurrenceSpecification childMessage;
        for (InteractionFragment interactionFragment : interactionFragmentList) {
            if (interactionFragment instanceof CombinedFragment) {
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                for (InteractionOperand io : cf.getInteractionOperandList()) {
                    for (InteractionFragment interactionFragment2 : io.getInteractionFragmentList()) {
                        childMessage = interactionFragment2.getMessage();
                        if (childMessage != null) {
                            messages.add(childMessage);
                        }
                    }
                }
            } else {
                childMessage = interactionFragment.getMessage();
                if (childMessage != null) {
                    messages.add(childMessage);
                }
            }
        }
        return messages;
    }

    public boolean hasSameLifeline(Lifeline lifeline) {
        return this.lifeline.getClassName().equals(lifeline.getClassName());
    }

    public void calculatePoint() {
        double height = 40;

        lifeline.calculatePoint();
        Point2D scheduledLifelineForLastDrawing = lifeline.getBottomRightCorner();
        beginPoint = new Point2D(lifeline.getHeadCenterPoint().getX(), lifeline.getHeadCenterPoint().getY() + 40);
        Point2D beforeGoalPoint = new Point2D(beginPoint.getX(), beginPoint.getY());

        int count = 0;
        List<MessageOccurrenceSpecification> messages = getMessages();
//        for (MessageOccurrenceSpecification message : messages) {
//            scheduledLifelineForLastDrawing = message.calculatePoint(beforeGoalPoint, lifeline,
//                    scheduledLifelineForLastDrawing, 0, instanceMap.get(count));
//            height += message.calculateHeight();
//            beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
//            count++;
//        }

        // interactionFragmentでループを回すように変更
        MessageOccurrenceSpecification message = new MessageOccurrenceSpecification();
        for(InteractionFragment interactionFragment: interactionFragmentList){
            if(interactionFragment instanceof CombinedFragment){
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                // 直前のメッセージのendPointのYを利用する
                cf.setBeginPoint(lifeline.getHeadCenterPoint().getX() - 35, message.getEndPoint().getY() + 5);
                for (InteractionOperand io : cf.getInteractionOperandList()) {
                    double maxEndPointX = 0.0;
                    for (InteractionFragment interactionFragment2 : io.getInteractionFragmentList()) {
                        message = interactionFragment2.getMessage();
                        scheduledLifelineForLastDrawing = message.calculatePoint(beforeGoalPoint, lifeline, scheduledLifelineForLastDrawing, 0, instanceMap.get(count));
                        height += message.calculateHeight();
                        beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
                        count++;
                        if(maxEndPointX < message.getEndPoint().getX()){
                            maxEndPointX = message.getEndPoint().getX();
                        }
                    }
                    // 複合フラグメント内の最後のメッセージのendPointを利用する
                    cf.setHeight(message.getEndPoint().getY() - cf.getBeginPoint().getY() + 10);
                    cf.setWidth(maxEndPointX + 150);

                }
            }else{
                message = interactionFragment.getMessage();
                scheduledLifelineForLastDrawing = message.calculatePoint(beforeGoalPoint, lifeline, scheduledLifelineForLastDrawing, 0, instanceMap.get(count));
                height += message.calculateHeight();
                beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
                count++;
            }
        }

        if (messages.size() >= 2)
            height += (messages.size() - 1) * 40;

        endPoint = new Point2D(lifeline.getHeadCenterPoint().getX(), beginPoint.getY() + height);

        /*
         * if (count == 0) { beginPoint = new
         * Point2D(lifeline.getHeadCenterPoint().getX(),
         * lifeline.getHeadCenterPoint().getY() + 30); endPoint = new
         * Point2D(lifeline.getHeadCenterPoint().getX(),
         * lifeline.getHeadCenterPoint().getY() + 60 + y); }
         */
    }

    private Point2D calculatePoint(Point2D beforeBeginPoint, Lifeline fromLifeline, Point2D lastLifeline,
            int sameLifelineCount, String instanceName) {

        double height = 40;
        if (hasSameLifeline(fromLifeline)) {
            sameLifelineCount++;
            lifeline = fromLifeline;
            beginPoint = new Point2D(lifeline.getHeadCenterPoint().getX() + sameLifelineCount * 5,
                    beforeBeginPoint.getY() + 50);
        } else {
            sameLifelineCount = 0;
            if (!lifeline.isCalculated()) {
                lifeline.setInstance(instanceName);
                lifeline.setLeftLifelineBottomRightCorner(lastLifeline);
                lifeline.calculatePoint();
                lifeline.setCalculated(true);
            }
            lastLifeline = lifeline.getBottomRightCorner();
            beginPoint = new Point2D(lifeline.getHeadCenterPoint().getX(), beforeBeginPoint.getY() + 20);
        }

        int count = 0;
        Point2D beforeGoalPoint = new Point2D(beginPoint.getX(), beginPoint.getY());
        List<MessageOccurrenceSpecification> messages = getMessages();

        for (MessageOccurrenceSpecification message : messages) {
            lastLifeline = message.calculatePoint(beforeGoalPoint, lifeline, lastLifeline, sameLifelineCount,
                    instanceMap.get(count));
            height += message.calculateHeight();
            beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
            count++;
        }

        if (messages.size() >= 2)
            height += (messages.size() - 1) * 20;

        if (sameLifelineCount != 0) {
            endPoint = new Point2D(lifeline.getHeadCenterPoint().getX() + sameLifelineCount * 5,
                    beginPoint.getY() + height - 15);
        } else {
            endPoint = new Point2D(lifeline.getHeadCenterPoint().getX(), beginPoint.getY() + height);
        }

        return lastLifeline;
    }

    private double calculateHeight() {
        double height = 40;
        List<MessageOccurrenceSpecification> messages = getMessages();

        for (MessageOccurrenceSpecification message : messages) {
            height += message.calculateHeight();
        }

        if (messages.size() >= 2)
            height += (messages.size() - 1) * 20;

        return height;
    }

    private String formExpression() {
        if (type == MessageType.Declaration) {
            if (value == null) {
                return name + " : " + umlClass.getName();
            } else {
                return name + " = " + value + " : " + umlClass.getName();
            }
        } else if (type == MessageType.Assignment) {
            return name + " = " + value + " : " + umlClass.getName();
        } else if (type == MessageType.Method) {
            return name;
        }

        return name;
    }

    public void draw(GraphicsContext gc) {
        draw(gc, 5.0, null);
    }

    private void draw(GraphicsContext gc, double arrowFirstX, Lifeline fromLifeline) {
        double width = 10.0;
        double height = endPoint.getY() - beginPoint.getY();
        String diagramFont = "Consolas";
        double textSize = 12.0;
        String operationName = formExpression();
        Point2D lastDrawPoint = Point2D.ZERO;

        if (!lifeline.isDrawn()) {
            // ライフラインを描画していない場合
            lifeline.draw(gc);
            lifeline.setDrawn(true);
            fromLifeline = lifeline;

            // updateMessagePoint();

            Point2D first = new Point2D(arrowFirstX, beginPoint.getY());
            Point2D last = new Point2D(arrowFirstX, endPoint.getY());

            gc.setFill(Color.WHITE);
            gc.fillRect(beginPoint.getX() - width / 2, beginPoint.getY(), width, height);

            gc.setStroke(Color.BLACK);
            gc.strokeRect(beginPoint.getX() - width / 2, beginPoint.getY(), width, height);

            Text operationText = new Text(operationName);
            operationText.setFont(Font.font(diagramFont, FontWeight.LIGHT, textSize));

            gc.setFill(Color.BLACK);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(operationText.getFont());
            gc.fillText(operationText.getText(), first.getX() + 5, first.getY() - 5);

            gc.strokeLine(first.getX(), first.getY(), beginPoint.getX() - width / 2, beginPoint.getY());
            double[] arrowTipX = { beginPoint.getX() - width / 2 - 5.0, beginPoint.getX() - width / 2 - 5.0,
                    beginPoint.getX() - width / 2 };
            double[] arrowTipY = { beginPoint.getY() - 3.0, beginPoint.getY() + 3.0, beginPoint.getY() };
            gc.fillPolygon(arrowTipX, arrowTipY, 3);
            gc.strokePolygon(arrowTipX, arrowTipY, 3);

            gc.setLineDashes(5.0, 5.0);
            gc.strokeLine(endPoint.getX() - width / 2, endPoint.getY(), last.getX(), last.getY());
            gc.setLineDashes(null);
            gc.strokeLine(last.getX() + 5.0, last.getY() - 3.0, last.getX(), last.getY());
            gc.strokeLine(last.getX() + 5.0, last.getY() + 3.0, last.getX(), last.getY());

            lastDrawPoint = new Point2D(last.getX(), last.getY());

        } else if (!hasSameLifeline(fromLifeline)) {
            // メッセージの開始と終わりのライフラインが同一の場合
            Point2D first = new Point2D(arrowFirstX, beginPoint.getY());
            Point2D last = new Point2D(arrowFirstX, endPoint.getY());

            gc.setFill(Color.WHITE);
            gc.fillRect(beginPoint.getX() - width / 2, beginPoint.getY(), width, height);

            gc.setStroke(Color.BLACK);
            gc.strokeRect(beginPoint.getX() - width / 2, beginPoint.getY(), width, height);

            Text operationText = new Text(operationName);
            operationText.setFont(Font.font(diagramFont, FontWeight.LIGHT, textSize));

            gc.setFill(Color.BLACK);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(operationText.getFont());
            gc.fillText(operationText.getText(), first.getX() + 5, first.getY() - 5);

            gc.strokeLine(first.getX(), first.getY(), beginPoint.getX() - width / 2, beginPoint.getY());
            double[] arrowTipX = { beginPoint.getX() - width / 2 - 5.0, beginPoint.getX() - width / 2 - 5.0,
                    beginPoint.getX() - width / 2 };
            double[] arrowTipY = { beginPoint.getY() - 3.0, beginPoint.getY() + 3.0, beginPoint.getY() };
            gc.fillPolygon(arrowTipX, arrowTipY, 3);
            gc.strokePolygon(arrowTipX, arrowTipY, 3);

            gc.setLineDashes(5.0, 5.0);
            gc.strokeLine(endPoint.getX() - width / 2, endPoint.getY(), last.getX(), last.getY());
            gc.setLineDashes(null);
            gc.strokeLine(last.getX() + 5.0, last.getY() - 3.0, last.getX(), last.getY());
            gc.strokeLine(last.getX() + 5.0, last.getY() + 3.0, last.getX(), last.getY());

            lastDrawPoint = new Point2D(last.getX(), last.getY());

        } else {
            // ライフラインが描画済みかつ同じライフラインへのメッセージの場合
            // updateMessagePoint();

            Point2D arrowFirst = new Point2D(arrowFirstX, beginPoint.getY() - 10);
            Point2D arrowLast = new Point2D(beginPoint.getX() + width / 2, arrowFirst.getY() + 10);

            gc.setFill(Color.WHITE);
            gc.fillRect(beginPoint.getX() - width / 2, beginPoint.getY(), width, height);

            gc.setStroke(Color.BLACK);
            gc.strokeRect(beginPoint.getX() - width / 2, beginPoint.getY(), width, height);

            Text operationText = new Text(operationName);
            operationText.setFont(Font.font(diagramFont, FontWeight.LIGHT, textSize));

            gc.setFill(Color.BLACK);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(operationText.getFont());
            gc.fillText(operationText.getText(), arrowFirst.getX() + 5, arrowFirst.getY() - 5);

            gc.strokeLine(arrowFirst.getX(), arrowFirst.getY(), arrowFirst.getX() + 20, arrowFirst.getY());
            gc.strokeLine(arrowFirst.getX() + 20, arrowFirst.getY(), arrowFirst.getX() + 20, arrowLast.getY());
            gc.strokeLine(arrowFirst.getX() + 20, arrowLast.getY(), arrowLast.getX(), arrowLast.getY());

            double[] arrowTipX = { arrowLast.getX(), arrowLast.getX() + 5.0, arrowLast.getX() + 5.0 };
            double[] arrowTipY = { arrowLast.getY(), arrowLast.getY() - 3.0, arrowLast.getY() + 3.0 };
            gc.fillPolygon(arrowTipX, arrowTipY, 3);
            gc.strokePolygon(arrowTipX, arrowTipY, 3);

            lastDrawPoint = new Point2D(arrowLast.getX(), arrowLast.getY());
        }

        arrowFirstX = beginPoint.getX() + width / 2;
        for (InteractionFragment interactionFragment : interactionFragmentList) {
            if (interactionFragment instanceof CombinedFragment) {
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                cf.draw(gc);
                for (InteractionOperand io : cf.getInteractionOperandList()) {
                    for (InteractionFragment interactionFragment2 : io.getInteractionFragmentList()) {
                        interactionFragment2.getMessage().draw(gc, arrowFirstX, fromLifeline);
                    }
                }
            } else {
                interactionFragment.getMessage().draw(gc, arrowFirstX, fromLifeline);
            }
        }
        // List<MessageOccurrenceSpecification> messages = getMessages();

        // for (MessageOccurrenceSpecification message : messages) {
        // message.draw(gc, arrowFirstX, fromLifeline);
        // }

    }
}
