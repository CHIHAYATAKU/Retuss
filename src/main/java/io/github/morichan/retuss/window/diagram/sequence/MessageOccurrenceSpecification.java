package io.github.morichan.retuss.window.diagram.sequence;

import io.github.morichan.fescue.feature.Operation;
import io.github.morichan.retuss.language.java.Argument;
import io.github.morichan.retuss.language.java.Type;
import io.github.morichan.retuss.language.uml.Class;
import io.github.morichan.retuss.window.diagram.OperationGraphic;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.abego.treelayout.internal.util.java.lang.string.StringUtil;

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
    private String instance;
    private String value;
    private Lifeline lifeline;
    private List<InteractionFragment> interactionFragmentList = new ArrayList<>();
    private String messageSignature;
    private ArrayList<Argument> arguments = new ArrayList<Argument>();

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

    public void setInteractionFragmentList(List<InteractionFragment> interactionFragmentList) {
        this.interactionFragmentList = interactionFragmentList;
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

    public void setMessageSignature(String messageSignature) {
        this.messageSignature = messageSignature;
    }

    public String getMessageSignature() {
        return this.messageSignature;
    }

    public ArrayList<Argument> getArguments() { return this.arguments; }

    public void addArgument(String type, String name) {
        this.arguments.add(new Argument(new Type(type), name));
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

        // interactionFragmentでループを回すように変更
        MessageOccurrenceSpecification message = new MessageOccurrenceSpecification();
        for(InteractionFragment interactionFragment: interactionFragmentList){
            if(interactionFragment instanceof CombinedFragment){
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                double maxEndPointX = 0.0;
                int maxLenGuardText = 0;
                // 直前のメッセージのendPointのYを利用する
                cf.setBeginPoint(lifeline.getHeadCenterPoint().getX() - 50, beforeGoalPoint.getY() + 20);
                beforeGoalPoint = new Point2D(beforeGoalPoint.getX(), beforeGoalPoint.getY() + 20);
                for (int i = 0; i < cf.getInteractionOperandList().size(); i++) {
                    InteractionOperand io = cf.getInteractionOperandList().get(i);
                    if (i == 0) {
                        io.setBeginPointY(cf.getBeginPoint().getY());
                    } else {
                        InteractionOperand beforeIo = cf.getInteractionOperandList().get(i - 1);
                        io.setBeginPointY(beforeIo.getBeginPointY() + beforeIo.getHeight() + 5);
                    }
                    beforeGoalPoint = new Point2D(beforeGoalPoint.getX(), io.getBeginPointY());
                    double endPointYIo = io.getBeginPointY();

                    for (InteractionFragment interactionFragment2 : io.getInteractionFragmentList()) {
                        message = interactionFragment2.getMessage();
                        scheduledLifelineForLastDrawing = message.calculatePoint(beforeGoalPoint, lifeline, scheduledLifelineForLastDrawing, 0, instanceMap.get(count));
                        endPointYIo = message.getEndPoint().getY() + 20;
                        beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
                        count++;
                        if(maxEndPointX < message.getEndPoint().getX()) {
                            maxEndPointX = message.getEndPoint().getX();
                        }
                    }
                    io.setHeight(endPointYIo - io.getBeginPointY() + 5);
                    beforeGoalPoint = new Point2D(beforeGoalPoint.getX(), io.getBeginPointY() + io.getHeight());

                    if (io.getGuard().length() > maxLenGuardText) {
                        maxLenGuardText = io.getGuard().length();
                    }
                }

                cf.setWidth(calcWidthCF(cf.getBeginPoint(), lifeline, maxEndPointX, maxLenGuardText));

            } else {
                message = interactionFragment.getMessage();
                scheduledLifelineForLastDrawing = message.calculatePoint(beforeGoalPoint, lifeline, scheduledLifelineForLastDrawing, 0, instanceMap.get(count));
                height += message.calculateHeight();
                beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
                count++;
            }
        }

        if (messages.size() >= 2)
            height += (messages.size() - 1) * 40;

//        endPoint = new Point2D(lifeline.getHeadCenterPoint().getX(), beginPoint.getY() + height);
        endPoint = new Point2D(lifeline.getHeadCenterPoint().getX(), beforeGoalPoint.getY() + 20);


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
                    beforeBeginPoint.getY() + 40);
        } else {
            sameLifelineCount = 0;
            if (!lifeline.isCalculated()) {
                lifeline.setInstance(instanceName);
                lifeline.setLeftLifelineBottomRightCorner(lastLifeline);
                lifeline.calculatePoint();
                lifeline.setCalculated(true);
            }
            lastLifeline = lifeline.getBottomRightCorner();
            beginPoint = new Point2D(lifeline.getHeadCenterPoint().getX(), beforeBeginPoint.getY() + 40);
        }

        int count = 0;
        Point2D beforeGoalPoint = new Point2D(beginPoint.getX(), beginPoint.getY());

        for (InteractionFragment interactionFragment : interactionFragmentList) {
            if (interactionFragment instanceof CombinedFragment) {
                CombinedFragment cf = (CombinedFragment) interactionFragment;
                double maxEndPointX = 0.0;
                int maxLenGuardText = 0;
                // 直前のメッセージのendPointのYを利用する
                cf.setBeginPoint(lifeline.getHeadCenterPoint().getX() - 50, beforeGoalPoint.getY() + 20);
                beforeGoalPoint = new Point2D(beforeGoalPoint.getX(), beforeGoalPoint.getY() + 20);
                for (int i = 0; i < cf.getInteractionOperandList().size(); i++) {
                    InteractionOperand io = cf.getInteractionOperandList().get(i);
                    if (i == 0) {
                        io.setBeginPointY(cf.getBeginPoint().getY());
                    } else {
                        InteractionOperand beforeIo = cf.getInteractionOperandList().get(i - 1);
                        io.setBeginPointY(beforeIo.getBeginPointY() + beforeIo.getHeight() + 5);
                    }
                    beforeGoalPoint = new Point2D(beforeGoalPoint.getX(), io.getBeginPointY());
                    double endPointYIo = io.getBeginPointY();

                    for (InteractionFragment interactionFragment2 : io.getInteractionFragmentList()) {
                        MessageOccurrenceSpecification message = interactionFragment2.getMessage();
                        lastLifeline = message.calculatePoint(beforeGoalPoint, lifeline, lastLifeline, sameLifelineCount, instanceMap.get(count));
//                        height += message.calculateHeight();
                        endPointYIo = message.getEndPoint().getY() + 20;
                        beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
                        count++;
                        if(maxEndPointX < message.getEndPoint().getX()) {
                            maxEndPointX = message.getEndPoint().getX();
                        }
                    }
                    io.setHeight(endPointYIo - io.getBeginPointY() + 5);
                    beforeGoalPoint = new Point2D(beforeGoalPoint.getX(), io.getBeginPointY() + io.getHeight());

                    if (io.getGuard().length() > maxLenGuardText) {
                        maxLenGuardText = io.getGuard().length();
                    }
                }

                cf.setWidth(calcWidthCF(cf.getBeginPoint(), lifeline, maxEndPointX, maxLenGuardText));

            } else {
                MessageOccurrenceSpecification message = interactionFragment.getMessage();
                lastLifeline = message.calculatePoint(beforeGoalPoint, lifeline, lastLifeline, sameLifelineCount, instanceMap.get(count));
                height += message.calculateHeight();
                beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
                count++;
            }
        }
//        List<MessageOccurrenceSpecification> messages = getMessages();
//
//        for (MessageOccurrenceSpecification message : messages) {
//            lastLifeline = message.calculatePoint(beforeGoalPoint, lifeline, lastLifeline, sameLifelineCount,
//                    instanceMap.get(count));
//            height += message.calculateHeight();
//            beforeGoalPoint = new Point2D(message.getEndPoint().getX(), message.getEndPoint().getY());
//            count++;
//        }

        if (interactionFragmentList.size() >= 2)
            height += (interactionFragmentList.size() - 1) * 20;

        if (sameLifelineCount != 0) {
            endPoint = new Point2D(lifeline.getHeadCenterPoint().getX() + sameLifelineCount * 5,
                    beginPoint.getY() + height - 15);
        } else {
            endPoint = new Point2D(lifeline.getHeadCenterPoint().getX(), beforeGoalPoint.getY() + 20);
        }

        return lastLifeline;
    }

    private double calculateHeight() {
        double height = 40;

        for (InteractionFragment interactionFragment : interactionFragmentList) {
            if (interactionFragment instanceof CombinedFragment) {
                height += ((CombinedFragment) interactionFragment).getHeight();
            } else {
                height += interactionFragment.getMessage().calculateHeight();
            }
        }

//        List<MessageOccurrenceSpecification> messages = getMessages();
//
//        for (MessageOccurrenceSpecification message : messages) {
//            height += message.calculateHeight();
//        }
//
//        if (messages.size() >= 2)
//            height += (messages.size() - 1) * 20;

        return height;
    }

    private double calcWidthCF(Point2D beginPoinCF, Lifeline lifeline, double maxEndPointX, int maxLenGuardText) {
        double widthCF = 0.0;
        if (maxEndPointX == 0.0) {
            if (maxLenGuardText == 0) {
                widthCF = (lifeline.getBottomRightCorner().getX() - lifeline.getTopLeftCorner().getX()) * 2;
            } else {
                // CFの左端〜ライフラインの中点の幅 + 余白 + ガード条件分の幅
                widthCF = lifeline.getHeadCenterPoint().getX() - beginPoinCF.getX() + 10 + (10 * (maxLenGuardText + 2));
            }
        } else {
            widthCF = maxEndPointX - beginPoinCF.getX() + 120;
        }

        return widthCF;
    }

    private void formExpression() {
        if (type == MessageType.Declaration) {
            if (value == null) {
                setMessageSignature(name + " : " + umlClass.getName());
            } else {
                setMessageSignature(name + " = " + value + " : " + umlClass.getName());
            }
        } else if (type == MessageType.Assignment) {
            setMessageSignature(name + " = " + value + " : " + umlClass.getName());
        } else if (type == MessageType.Method) {
            // 呼び出すメソッドを探索し、引数の型と戻り値の型を求める
            String returnType = "";
            for (OperationGraphic og : lifeline.getUmlClass().getOperationGraphics()) {
                Operation operation = og.getOperation();
                if (operation.getName().getNameText().equals(this.name)) {
                    Operation calledOperation = operation;
                    try {
                        if (operation.getParameters().size() == this.arguments.size()) {
                            for (int i = 0; i < arguments.size(); i++) {
                                if (arguments.get(i).getType().toString().equals("TmpType")) {
                                    arguments.get(i).setType(new Type(calledOperation.getParameters().get(i).getType().toString()));
                                }
                            }
                            returnType = calledOperation.getReturnType().toString();
                        }
                        break;
                    } catch (IllegalStateException e) {
                        // Operation.getParameters()が0の場合例外が返ってくる
                        if (this.arguments.size() == 0) {
                            returnType = calledOperation.getReturnType().toString();
                        }
                    }
                }
            }

            // メッセージシグニチャを生成
            StringBuilder sb = new StringBuilder();
            sb.append(name + "(");
            for (Argument argument : arguments) {
                sb.append(argument.getName());
                if (!argument.getType().toString().equals("TmpType")) {
                    sb.append(" : " + argument.getType().toString());
                }
                sb.append(", ");
            }
            // 最後の", "を削除
            if (!(sb.charAt(sb.length() - 1) == '(')) {
                sb.delete(sb.length() - 2, sb.length());
            }
            sb.append(")");

            if (!returnType.isEmpty()) {
                sb.append(" : " + returnType);
            }

            setMessageSignature(sb.toString());

        } else {
            setMessageSignature(name);
        }
    }

    public void draw(GraphicsContext gc) {
        draw(gc, 5.0, null);
    }

    private void draw(GraphicsContext gc, double arrowFirstX, Lifeline fromLifeline) {
        double width = 10.0;
        double height = endPoint.getY() - beginPoint.getY();
        String diagramFont = "Consolas";
        double textSize = 12.0;
        formExpression();
        String operationName = getMessageSignature();
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
            // メッセージの開始と終わりのライフラインが異なる場合
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
