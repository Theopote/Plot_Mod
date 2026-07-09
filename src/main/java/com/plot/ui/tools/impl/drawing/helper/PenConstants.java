package com.plot.ui.tools.impl.drawing.helper;

/**
 * 钢笔工具常量定义
 * <p>
 * 集中管理钢笔工具中使用的所有常量，提高代码可读性和维护性。
 */
public final class PenConstants {
    
    // 鼠标按钮常量
    public static final int MOUSE_BUTTON_LEFT = 0;
    public static final int MOUSE_BUTTON_RIGHT = 1;

    // 键盘按键常量
    public static final int KEY_ESC = 27;
    public static final int KEY_ENTER = 13;

    // 绘制相关常量
    public static final int MIN_POINTS_FOR_COMPLETION = 2;

    // 状态消息常量
    public static final String MSG_START_DRAWING = "status.plot.pen.start";
    public static final String MSG_FIRST_POINT = "status.plot.pen.first_point";
    public static final String MSG_ADDING_POINTS = "status.plot.pen.adding_points";
    public static final String MSG_DRAG_FOR_CURVE = "status.plot.pen.drag_curve";

    // 错误消息常量
    public static final String ERROR_INSUFFICIENT_POINTS = "status.plot.pen.insufficient_points";
    public static final String ERROR_CANCELLED = "status.plot.common.draw_cancelled";
    
    private PenConstants() {
        // 防止实例化
    }
} 