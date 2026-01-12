package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.geometry.Vec2d;

/**
 * 钢笔路径节点
 * 
 * 表示钢笔工具路径中的一个节点，包含锚点和相关的控制点。
 * 支持不同类型的节点：角点、平滑点、不对称平滑点等。
 */
public class PathNode {
    
    /**
     * 节点类型枚举
     */
    public enum NodeType {
        /** 角点：两个控制点可独立移动 */
        CORNER,
        /** 平滑点：两个控制点共线且等距 */
        SMOOTH,
        /** 不对称平滑点：共线但不同距 */
        ASYMMETRIC
    }
    
    private Vec2d anchor;           // 锚点
    private Vec2d controlPrev;      // 指向前一个锚点的控制点 (in-handle)
    private Vec2d controlNext;      // 指向后一个锚点的控制点 (out-handle)
    private NodeType type;          // 节点类型
    
    /**
     * 创建新的路径节点
     * 
     * @param anchor 锚点位置
     */
    public PathNode(Vec2d anchor) {
        this.anchor = anchor;
        // 默认情况下，控制点和锚点重合，表示一个没有曲线的角点
        this.controlPrev = anchor;
        this.controlNext = anchor;
        this.type = NodeType.CORNER;
    }
    
    /**
     * 创建带有控制点的路径节点
     * 
     * @param anchor 锚点位置
     * @param controlPrev 前控制点
     * @param controlNext 后控制点
     * @param type 节点类型
     */
    public PathNode(Vec2d anchor, Vec2d controlPrev, Vec2d controlNext, NodeType type) {
        this.anchor = anchor;
        this.controlPrev = controlPrev;
        this.controlNext = controlNext;
        this.type = type;
    }
    
    // Getters
    public Vec2d getAnchor() { return anchor; }
    public Vec2d getControlPrev() { return controlPrev; }
    public Vec2d getControlNext() { return controlNext; }
    public NodeType getType() { return type; }
    
    // Setters
    public void setAnchor(Vec2d anchor) { this.anchor = anchor; }
    public void setControlPrev(Vec2d controlPrev) { this.controlPrev = controlPrev; }
    public void setControlNext(Vec2d controlNext) { this.controlNext = controlNext; }
    public void setType(NodeType type) { this.type = type; }
    
    /**
     * 移动锚点
     * 
     * @param newPosition 新位置
     */
    public void moveAnchor(Vec2d newPosition) {
        Vec2d offset = newPosition.subtract(anchor);
        anchor = newPosition;
        controlPrev = controlPrev.add(offset);
        controlNext = controlNext.add(offset);
    }
    
    /**
     * 设置平滑控制点
     * 根据拖拽位置计算对称的控制点
     * 
     * @param dragPoint 拖拽点
     */
    public void setSmoothControlPoints(Vec2d dragPoint) {
        Vec2d direction = dragPoint.subtract(anchor);
        double distance = direction.length();
        
        if (distance > 1e-6) {
            direction = direction.normalize();
            controlNext = anchor.add(direction.multiply(distance));
            controlPrev = anchor.add(direction.multiply(-distance));
            type = NodeType.SMOOTH;
        }
    }
    
    /**
     * 设置不对称控制点
     * 
     * @param prevPoint 前控制点
     * @param nextPoint 后控制点
     */
    public void setAsymmetricControlPoints(Vec2d prevPoint, Vec2d nextPoint) {
        controlPrev = prevPoint;
        controlNext = nextPoint;
        type = NodeType.ASYMMETRIC;
    }
    
    /**
     * 转换为角点（控制点与锚点重合）
     */
    public void convertToCorner() {
        controlPrev = anchor;
        controlNext = anchor;
        type = NodeType.CORNER;
    }
    
    /**
     * 检查是否为角点（控制点与锚点重合）
     */
    public boolean isCorner() {
        return controlPrev.equals(anchor) && controlNext.equals(anchor);
    }
    
    /**
     * 检查是否为平滑点
     */
    public boolean isSmooth() {
        return type == NodeType.SMOOTH;
    }
    
    @Override
    public String toString() {
        return String.format("PathNode{anchor=%s, controlPrev=%s, controlNext=%s, type=%s}", 
                           anchor, controlPrev, controlNext, type);
    }
} 