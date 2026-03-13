package com.masterplanner.core.graphics;

import java.awt.Color;

/**
 * 绘图常量类
 * 集中管理所有绘图相关的常量
 */
public final class DrawingConstants {
    // 禁止实例化
    private DrawingConstants() {
        throw new AssertionError("常量类不应被实例化");
    }
    
    // 默认值常量
    public static final float DEFAULT_OPACITY = 1.0f;
    public static final float DEFAULT_LINE_WIDTH = 1.0f;
    public static final Color DEFAULT_COLOR = Color.BLACK;
    public static final int DEFAULT_CIRCLE_SEGMENTS = 32;
    
    // 线型参数常量
    public static final double DASH_LENGTH = 10.0;
    public static final double GAP_LENGTH = 4.0;
    
    // 选择相关常量
    public static final double SELECTION_TOLERANCE = 5.0;
} 