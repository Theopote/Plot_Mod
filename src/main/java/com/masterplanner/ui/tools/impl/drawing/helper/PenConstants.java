package com.masterplanner.ui.tools.impl.drawing.helper;

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
    public static final String MSG_START_DRAWING = "点击开始绘制贝塞尔曲线";
    public static final String MSG_FIRST_POINT = "拖动创建曲线控制点，或直接点击添加直线段，右键或Enter键完成绘制，Esc键取消";
    public static final String MSG_ADDING_POINTS = "已添加 %d 个点，右键或Enter键完成绘制，Esc键取消";
    public static final String MSG_DRAG_FOR_CURVE = "拖动创建曲线控制点，或直接点击添加锚点，右键或Esc键结束绘制";
    
    // 错误消息常量
    public static final String ERROR_INSUFFICIENT_POINTS = "点数不足，无法完成绘制";
    public static final String ERROR_CANCELLED = "绘制已取消";
    
    private PenConstants() {
        // 防止实例化
    }
} 