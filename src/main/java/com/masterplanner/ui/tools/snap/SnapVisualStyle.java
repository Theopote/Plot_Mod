package com.masterplanner.ui.tools.snap;

import com.masterplanner.core.snap.SnapPriorityEvaluator;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImColor;

import java.awt.Color;

/**
 * 捕捉可视样式统一配置
 * 提供各类捕捉点/约束的颜色、指示圈大小与标记尺寸，供所有工具与管理器统一使用。
 */
public final class SnapVisualStyle {
    private SnapVisualStyle() {}

    // 基础尺寸（像素）
    public static final float DEFAULT_RING_SIZE = 4.0f;
    public static final float ENHANCED_RING_SIZE = 5.0f;
    public static final float MARKER_SIZE = 2.0f;

    // 兼容旧引用：默认高亮色（避免静态初始化期访问 ThemeManager）
    public static final Color DEFAULT_HIGHLIGHT = new Color(255, 200, 0, 255);

    /**
     * 获取捕捉类型对应的颜色
     */
    public static Color colorFor(SnapPriorityEvaluator.SnapType type) {
        var theme = getThemeColorsSafely();
        if (type == null) return toColor(theme.warningText, 255);
        return switch (type) {
            case END_POINT -> toColor(theme.errorText, 255);
            case MID_POINT -> toColor(theme.successText, 255);
            case CENTER_POINT, CENTROID -> toColor(theme.infoText, 255);
            case INTERSECTION, VERTEX -> toColor(theme.accent, 255);
            case PERPENDICULAR, HORIZONTAL, VERTICAL -> toColor(theme.infoText, 255);
            case TANGENT -> toColor(theme.warningText, 255);
            case QUADRANT, CONTROL_POINT -> toColor(theme.accent, 255);
            case NEAREST_POINT, GRID_POINT -> toColor(theme.mutedText, 255);
            case EXTENSION, PARALLEL -> toColor(theme.mutedText, 255);
            case NONE -> toColor(theme.warningText, 255);
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

    private static Color toColor(int color, int alpha) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }

    private static UITheme.ThemeColors getThemeColorsSafely() {
        try {
            var manager = ThemeManager.getInstance();
            var theme = manager.getCurrentTheme();
            return theme != null ? theme : UITheme.DARK_THEME;
        } catch (Throwable ignored) {
            return UITheme.DARK_THEME;
        }
    }
}


