package io.github.morichan.retuss.window.diagram.sequence;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.geometry.Point2D;
import java.util.ArrayList;

public class CombinedFragment extends InteractionFragment {
    private InteractionOperandKind kind;
    private ArrayList<InteractionOperand> interactionOperandList = new ArrayList<InteractionOperand>();
    private Point2D beginPoint = Point2D.ZERO;
    private double height = 0;
    private double width = 0;

    public CombinedFragment(InteractionOperandKind kind) {
        this.kind = kind;
    }

    public void addInteractionOperand(InteractionOperand interactionOperand) {
        this.interactionOperandList.add(interactionOperand);
    }

    public ArrayList<InteractionOperand> getInteractionOperandList() {
        return this.interactionOperandList;
    }

    public void setBeginPoint(double x, double y){
        this.beginPoint = new Point2D(x, y);
    }

    public void setHeight(double height){
        this.height = height;
    }

    public void setWidth(double width){
        this.width = width;
    }

    public Point2D getBeginPoint(){
        return this.beginPoint;
    }

    public void draw(GraphicsContext gc) {
        gc.setStroke(Color.RED);
        // 全体の枠を描画
        gc.strokeRect(beginPoint.getX(), beginPoint.getY(), width, height);
        // 左上の枠を描画
        gc.strokeLine(beginPoint.getX(), beginPoint.getY() + 20, beginPoint.getX() + 20, beginPoint.getY() + 20);
        gc.strokeLine(beginPoint.getX() + 20, beginPoint.getY() + 20, beginPoint.getX() + 25, beginPoint.getY() + 15);
        gc.strokeLine(beginPoint.getX() + 25, beginPoint.getY() + 15, beginPoint.getX() + 25, beginPoint.getY());
        // 左上に複合フラグメントの種類を描画する
        gc.fillText(kind.name(), beginPoint.getX() + 5, beginPoint.getY() + 15);
        // ガード条件を表示する
        gc.fillText(String.format("[ %s ]", interactionOperandList.get(0).getGuard()), beginPoint.getX() + 50, beginPoint.getY() + 15);

    }
}