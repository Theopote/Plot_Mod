package com.plot.core.command.commands;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.Command;
import com.plot.core.model.Shape;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 控制点编辑命令。
 * 拖拽过程中图形已被实时修改，因此命令在首次执行时为 no-op，
 * 撤销/重做通过恢复保存的几何快照完成。
 */
public class ControlPointEditCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlPointEditCommand.class);

    private final Shape target;
    private final Shape beforeSnapshot;
    private final Shape afterSnapshot;

    public ControlPointEditCommand(Shape target, Shape beforeSnapshot, Shape afterSnapshot) {
        this.target = target;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
    }

    @Override
    public void execute() {
        // 拖拽结束时几何已应用到 target，无需再次修改。
    }

    @Override
    public void undo() {
        applySnapshot(beforeSnapshot);
    }

    @Override
    public void redo() {
        applySnapshot(afterSnapshot);
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.control_point_edit");
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("history.plot.control_point_edit.detail",
                PlotI18n.shapeTypeLabel(target.getClass().getSimpleName()),
                countChangedPoints(beforeSnapshot, afterSnapshot));
    }

    private void applySnapshot(Shape snapshot) {
        if (target == null || snapshot == null) {
            return;
        }

        List<Vec2d> controlPoints = snapshot.getControlPoints();
        if (controlPoints == null || controlPoints.isEmpty()) {
            LOGGER.warn("控制点编辑命令无法恢复几何：快照缺少控制点");
            return;
        }

        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d worldPoint = controlPoints.get(i);
            Vec2d localPoint = worldPoint;
            if (target.getTransform() != null) {
                try {
                    localPoint = target.getTransform().inverseTransform(worldPoint);
                } catch (Exception ex) {
                    LOGGER.warn("逆变换失败，使用世界坐标恢复控制点", ex);
                }
            }
            target.setControlPoint(i, localPoint);
        }
    }

    private static int countChangedPoints(Shape before, Shape after) {
        List<Vec2d> beforePoints = before != null ? before.getControlPoints() : List.of();
        List<Vec2d> afterPoints = after != null ? after.getControlPoints() : List.of();
        int count = Math.min(beforePoints.size(), afterPoints.size());
        int changed = 0;
        for (int i = 0; i < count; i++) {
            Vec2d a = beforePoints.get(i);
            Vec2d b = afterPoints.get(i);
            if (a == null || b == null) {
                if (a != b) {
                    changed++;
                }
                continue;
            }
            if (a.distance(b) > 1e-6) {
                changed++;
            }
        }
        changed += Math.abs(beforePoints.size() - afterPoints.size());
        return changed;
    }

    public Shape getTarget() {
        return target;
    }

    public List<Vec2d> getBeforeControlPoints() {
        return copyControlPoints(beforeSnapshot);
    }

    public List<Vec2d> getAfterControlPoints() {
        return copyControlPoints(afterSnapshot);
    }

    private static List<Vec2d> copyControlPoints(Shape shape) {
        if (shape == null) {
            return List.of();
        }
        List<Vec2d> points = shape.getControlPoints();
        if (points == null) {
            return List.of();
        }
        List<Vec2d> copies = new ArrayList<>(points.size());
        for (Vec2d point : points) {
            copies.add(point == null ? null : new Vec2d(point.x, point.y));
        }
        return copies;
    }
}
