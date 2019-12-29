package io.github.morichan.retuss.window.diagram.sequence;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.geometry.Point2D;
import java.util.ArrayList;

public class CombinedFragment extends InteractionFragment {
    private InteractionOperandKind kind;
    private String textNextToKind = "";
    private ArrayList<InteractionOperand> interactionOperandList = new ArrayList<InteractionOperand>();
    private int minInt = 0;
    private int maxInt = 0;
    private Point2D beginPoint = Point2D.ZERO;
    private double height = 40;
    private double width = 0;
    private String codeText = "";

    public CombinedFragment(InteractionOperandKind kind) {
        this.kind = kind;
    }

    public InteractionOperandKind getInteractionOperandKind() {
        return this.kind;
    }

    public void setTextNextToKind(String textNextToKind) {
        this.textNextToKind = textNextToKind;
    }

    public String getTextNextToKind() {
        return this.textNextToKind;
    }

    public ArrayList<InteractionOperand> getInteractionOperandList() {
        return this.interactionOperandList;
    }

    public void setInteractionOperandList(ArrayList<InteractionOperand> interactionOperandList) {
        this.interactionOperandList = interactionOperandList;
    }

    public void addInteractionOperand(InteractionOperand interactionOperand) {
        this.interactionOperandList.add(interactionOperand);
    }

    public void setMinInt(int minInt){
        this.minInt = minInt;
    }

    public void setMaxInt(int maxInt){
        this.maxInt = maxInt;
    }

    public Point2D getBeginPoint(){
        return this.beginPoint;
    }

    public void setBeginPoint(double x, double y){
        this.beginPoint = new Point2D(x, y);
    }

    public void setHeight(double height){
        // 複合フラグメント内にメッセージがなくてもheightの初期値分の高さは確保する
        if(this.height < height)
            this.height = height;
    }

    public void setWidth(double width){
        this.width = width;
    }

    public String getCodeText() { return this.codeText; }

    public void setCodeText(String codeText) { this.codeText = codeText; }

    public void draw(GraphicsContext gc) {
        calcHeight();
        int kindNameAreaSize = (kind.name().length() + textNextToKind.length()) * 7;
        gc.setStroke(Color.BLACK);
        // 全体の枠を描画
        gc.strokeRect(beginPoint.getX(), beginPoint.getY(), width, height);
        // 左上の枠を描画
        gc.strokeLine(beginPoint.getX(), beginPoint.getY() + 20, beginPoint.getX() + kindNameAreaSize, beginPoint.getY() + 20);
        gc.strokeLine(beginPoint.getX() + kindNameAreaSize, beginPoint.getY() + 20, beginPoint.getX() + kindNameAreaSize + 5, beginPoint.getY() + 15);
        gc.strokeLine(beginPoint.getX() + kindNameAreaSize + 5, beginPoint.getY() + 15, beginPoint.getX() + kindNameAreaSize + 5, beginPoint.getY());
        // 左上に複合フラグメントの種類を描画する
        if (textNextToKind.isEmpty()) {
            gc.fillText(kind.name(), beginPoint.getX() + 2, beginPoint.getY() + 15);
        } else {
            gc.fillText(kind.name() + "(" + textNextToKind + ")", beginPoint.getX() + 2, beginPoint.getY() + 15);
        }

        // 区切りのダッシュ線とガード条件を描画する
        InteractionOperand io = interactionOperandList.get(0);
        if (!io.getGuard().isBlank()) {
            gc.fillText(String.format("[ %s ]", io.getGuard()), beginPoint.getX() + 50, io.getBeginPointY() + 10);
        }
        for (int i=1; i<interactionOperandList.size(); i++) {
            io = interactionOperandList.get(i);
            gc.setLineDashes(10.0, 10.0);
            gc.strokeLine(beginPoint.getX(), io.getBeginPointY(), beginPoint.getX() + width, io.getBeginPointY());
            gc.setLineDashes(null);
            if (!io.getGuard().isBlank()) {
                gc.fillText(String.format("[ %s ]", io.getGuard()), beginPoint.getX() + 50, io.getBeginPointY() + 15);
            }
        }
    }

    private void calcHeight() {
        double height = 0.0;
        for (InteractionOperand io : interactionOperandList) {
            height += io.getHeight();
        }
        this.height = height;
    }
}