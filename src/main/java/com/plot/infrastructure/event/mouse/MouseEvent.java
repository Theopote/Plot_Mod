package com.plot.infrastructure.event.mouse;

import com.plot.api.geometry.Vec2d;

/**
 * 鼠标事件类
 */
public class MouseEvent {
    /**
     * 鼠标事件类型
     */
    public enum Type {
        CLICKED,    // 点击
        PRESSED,    // 按下
        RELEASED,   // 释放
        DRAGGED,    // 拖动
        MOVED,      // 移动
        ENTERED,    // 进入
        EXITED      // 离开
    }

    private final Type type;
    private final Vec2d position;
    private final Vec2d delta;      // 移动增量
    private final int button;
    private final int clickCount;
    private final boolean isShiftDown;
    private final boolean isControlDown;
    private final boolean isAltDown;
    private final double x;
    private final double y;
    private final int action;

    public MouseEvent(Type type, Vec2d position, Vec2d delta) {
        this(type, position, delta, 0, 1, false, false, false, 0, 0, 0);
    }

    public MouseEvent(Type type, Vec2d position, Vec2d delta, int button, int clickCount,
                     boolean isShiftDown, boolean isControlDown, boolean isAltDown, double x, double y, int action) {
        this.type = type;
        this.position = position;
        this.delta = delta;
        this.button = button;
        this.clickCount = clickCount;
        this.isShiftDown = isShiftDown;
        this.isControlDown = isControlDown;
        this.isAltDown = isAltDown;
        this.x = x;
        this.y = y;
        this.action = action;
    }

    public Type getType() {
        return type;
    }

    public Vec2d getPosition() {
        return position;
    }

    public Vec2d getDelta() {
        return delta != null ? delta : new Vec2d(0, 0);  // 确保不返回null
    }

    public int getButton() {
        return button;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public String toString() {
        return String.format("MouseEvent{type=%s, position=%s, delta=%s, button=%d, clickCount=%d, " +
                        "shift=%b, control=%b, alt=%b, x=%f, y=%f, action=%d}",
                type, position, getDelta(), button, clickCount, isShiftDown, isControlDown, isAltDown, x, y, action);
    }
} 