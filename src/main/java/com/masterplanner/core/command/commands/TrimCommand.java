package com.masterplanner.core.command.commands;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;

import java.util.List;
import java.util.ArrayList;

/**
 * 修剪命令
 */
public class TrimCommand extends ModifyCommand {
    private final Shape shape;
    private final Vec2d point;
    private final List<Shape> boundaries;
    private Shape originalShape;
    private List<Shape> resultShapes;

    public TrimCommand(Shape shape, Vec2d point, List<Shape> boundaries, AppState appState) {
        super(List.of(shape), new ArrayList<>(), appState);
        this.shape = shape;
        this.point = point;
        this.boundaries = new ArrayList<>(boundaries);
        this.resultShapes = new ArrayList<>();
    }
    
    //@Override
    protected void doExecute() {
        // 保存原始形状的副本
        originalShape = shape.clone();

        // 计算与边界的交点
        List<Vec2d> intersections = new ArrayList<>();
        for (Shape boundary : boundaries) {
            if (boundary == shape) continue;
            intersections.addAll(shape.getIntersectionsWith(boundary));
        }

        if (intersections.isEmpty()) {
            // 如果没有交点，不执行修剪
            setTargetShapes(List.of(shape));
            return;
        }

        // 根据点击位置分割形状
        resultShapes = shape.split(intersections, point);

        // 删除原始形状
        shape.delete();

        // 设置目标形状
        setTargetShapes(new ArrayList<>(resultShapes));
    }

    @Override
    public void undo() {
        // 删除分割后的形状
        for (Shape resultShape : resultShapes) {
            resultShape.delete();
        }
        resultShapes.clear();
        
        // 恢复原始形状
        originalShape.restore();
    }
    
    @Override
    public void redo() {
        execute();
    }
    
    @Override
    public String getDescription() {
        return String.format(
            "修剪对象 (%.2f, %.2f)",
            point.x,
            point.y
        );
    }
}
