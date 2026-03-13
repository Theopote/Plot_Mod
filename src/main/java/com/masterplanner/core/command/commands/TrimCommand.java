package com.masterplanner.core.command.commands;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;

import java.util.List;
import java.util.ArrayList;

/**
 * 修剪命令（遗留实现）。
 *
 * <p>该类已废弃，不再参与当前修剪主链路。当前修剪统一通过
 * {@link ModifyCommand} 由 {@code TrimHandler} 创建并执行。</p>
 */
@Deprecated(since = "2.0")
public class TrimCommand extends ModifyCommand {
    private final Vec2d point;
    private Shape originalShape;
    private List<Shape> resultShapes;

    @Deprecated(since = "2.0")
    public TrimCommand(Shape shape, Vec2d point, AppState appState) {
        super(List.of(shape), new ArrayList<>(), appState);
        this.point = point;
        this.resultShapes = new ArrayList<>();
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
