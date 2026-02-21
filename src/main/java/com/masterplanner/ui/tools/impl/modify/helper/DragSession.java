package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.ui.tools.impl.modify.helper.BoundingBoxControlManager.ControlPointType;

/**
 * 拖拽会话管理类
 * 
 * <p>封装拖拽状态和逻辑，简化主策略类的复杂度</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 拖拽会话管理
 */
public class DragSession {
    private final Vec2d startPoint;
    private final ControlPointType controlPoint;
    private final double dragThreshold;
    private Vec2d currentPoint;
    private boolean isActive;

    public DragSession(Vec2d startPoint, ControlPointType controlPoint, double dragThreshold) {
        this.startPoint = startPoint;
        this.controlPoint = controlPoint;
        this.dragThreshold = dragThreshold;
        this.currentPoint = startPoint;
        this.isActive = false;
    }

    /**
     * 激活拖拽会话
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * 更新当前拖拽点
     */
    public void updateCurrentPoint(Vec2d newPoint) {
        this.currentPoint = newPoint;
    }

    /**
     * 检查是否应该开始拖拽
     */
    public boolean shouldStartDrag() {
        if (startPoint == null || currentPoint == null) {
            return false;
        }
        return currentPoint.distance(startPoint) > dragThreshold;
    }

    /**
     * 获取拖拽向量
     */
    public Vec2d getDragVector() {
        if (startPoint == null || currentPoint == null) {
            return new Vec2d(0, 0);
        }
        return currentPoint.subtract(startPoint);
    }

    // Getters
    public boolean isActive() {
        return isActive;
    }

    public Vec2d getCurrentPoint() {
        return currentPoint;
    }

    public Vec2d getStartPoint() {
        return startPoint;
    }

}
