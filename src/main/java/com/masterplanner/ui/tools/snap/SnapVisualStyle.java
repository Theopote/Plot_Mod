package com.masterplanner.ui.tools.snap;

import com.masterplanner.core.snap.SnapPriorityEvaluator;
import imgui.ImColor;

import java.awt.Color;

/**
 * 捕捉可视样式统一配置
 * 提供各类捕捉点/约束的颜色、指示圈大小与标记尺寸，供所有工具与管理器统一使用。
 */
public final class SnapVisualStyle {
    private SnapVisualStyle() {}

    // 基础尺寸（像素）
    public static final float DEFAULT_RING_SIZE = 8.0f;
    public static final float ENHANCED_RING_SIZE = 10.0f;
    public static final float MARKER_SIZE = 4.0f;

    // 默认高亮颜色（例如通用预览环）
    public static final Color DEFAULT_HIGHLIGHT = new Color(255, 230, 50);

    /**
     * 获取捕捉类型对应的颜色
     */
    public static Color colorFor(SnapPriorityEvaluator.SnapType type) {
        if (type == null) return DEFAULT_HIGHLIGHT;
        return switch (type) {
            case END_POINT -> Color.RED;                 // 端点
            case MID_POINT -> Color.GREEN;               // 中点
            case CENTER_POINT -> Color.BLUE;             // 中心点
            case INTERSECTION -> Color.MAGENTA;          // 交点
            case PERPENDICULAR, HORIZONTAL, VERTICAL -> Color.CYAN; // 垂足/水平/竖直
            case TANGENT -> Color.ORANGE;                // 切点
            case QUADRANT -> Color.PINK;                 // 象限点
            case NEAREST_POINT -> new Color(128, 128, 128); // 最近点
            case GRID_POINT -> Color.LIGHT_GRAY;         // 网格点
            case VERTEX -> new Color(255, 0, 255);       // 顶点
            case CENTROID -> new Color(0, 100, 200);     // 质心
            case CONTROL_POINT -> new Color(255, 192, 203); // 控制点
            case EXTENSION, PARALLEL -> Color.GRAY;      // 延长线/平行
            case NONE -> DEFAULT_HIGHLIGHT;
        };
    }

    /**
     * 获取 ImGui 颜色（带 Alpha）
     */
    public static int imGuiColorFor(SnapPriorityEvaluator.SnapType type, int alpha) {
        Color c = colorFor(type);
        int a = Math.max(0, Math.min(255, alpha));
        return ImColor.rgba(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /**
     * 获取捕捉类型对应的指示圈尺寸
     */
    public static float ringSizeFor(SnapPriorityEvaluator.SnapType type) {
        if (type == null) return DEFAULT_RING_SIZE;
        return switch (type) {
            case END_POINT, CENTER_POINT, CENTROID -> ENHANCED_RING_SIZE;                 // 重要点更大
            case INTERSECTION, TANGENT, VERTEX -> DEFAULT_RING_SIZE + 1.0f;               // 特殊点稍大
            case MID_POINT, QUADRANT, CONTROL_POINT -> DEFAULT_RING_SIZE;                 // 标准
            case PERPENDICULAR, EXTENSION, HORIZONTAL, VERTICAL, PARALLEL -> DEFAULT_RING_SIZE - 1.0f; // 约束稍小
            case NEAREST_POINT, GRID_POINT -> DEFAULT_RING_SIZE - 2.0f;                   // 辅助更小
            case NONE -> 0.0f;
        };
    }
}


