package com.masterplanner.ui.tools.snap;

import com.masterplanner.core.snap.SnapPriorityEvaluator;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImColor;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * 捕捉可视样式统一配置
 * 提供各类捕捉点/约束的颜色、指示圈大小与标记尺寸，供所有工具与管理器统一使用。
 */
public final class SnapVisualStyle {
    private SnapVisualStyle() {}
    private static final Map<SnapPriorityEvaluator.SnapType, Integer> CUSTOM_COLORS =
            new EnumMap<>(SnapPriorityEvaluator.SnapType.class);

    // 基础尺寸（像素）
    public static final float DEFAULT_RING_SIZE = 6.0f;
    public static final float ENHANCED_RING_SIZE = 7.5f;
    public static final float MARKER_SIZE = 3.0f;

    // 兼容旧引用：默认高亮色（避免静态初始化期访问 ThemeManager）
    public static final Color DEFAULT_HIGHLIGHT = new Color(255, 200, 0, 255);

    /**
     * 获取捕捉类型对应的颜色
     */
    public static Color colorFor(SnapPriorityEvaluator.SnapType type) {
        SnapPriorityEvaluator.SnapType snapType = type == null ? SnapPriorityEvaluator.SnapType.NONE : type;
        Integer customArgb = CUSTOM_COLORS.get(snapType);
        if (customArgb != null) {
            return toColor(customArgb, 255);
        }
        return toColor(defaultColorArgbFor(snapType), 255);
    }

    public static int defaultColorArgbFor(SnapPriorityEvaluator.SnapType type) {
        var theme = getThemeColorsSafely();
        if (type == null) return theme.warningText;
        return switch (type) {
            case END_POINT -> theme.errorText;
            case MID_POINT -> theme.successText;
            case CENTER_POINT, CENTROID -> ImColor.rgba(0, 220, 255, 255);
            case INTERSECTION -> ImColor.rgba(255, 120, 0, 255);
            case VERTEX -> ImColor.rgba(255, 0, 220, 255);
            case PERPENDICULAR -> ImColor.rgba(170, 120, 255, 255);
            case HORIZONTAL, VERTICAL -> theme.infoText;
            case TANGENT -> ImColor.rgba(255, 220, 0, 255);
            case QUADRANT -> ImColor.rgba(255, 160, 80, 255);
            case CONTROL_POINT -> theme.accent;
            case NEAREST_POINT -> ImColor.rgba(255, 140, 60, 255);
            case GRID_POINT -> theme.mutedText;
            case EXTENSION, PARALLEL -> ImColor.rgba(130, 170, 255, 255);
            case NONE -> theme.warningText;
        };
    }

    public static int getEffectiveColorArgb(SnapPriorityEvaluator.SnapType type) {
        SnapPriorityEvaluator.SnapType snapType = type == null ? SnapPriorityEvaluator.SnapType.NONE : type;
        Integer customArgb = CUSTOM_COLORS.get(snapType);
        return customArgb != null ? customArgb : defaultColorArgbFor(snapType);
    }

    public static void setCustomColor(SnapPriorityEvaluator.SnapType type, int argb) {
        if (type == null) {
            return;
        }
        CUSTOM_COLORS.put(type, (argb & 0x00FFFFFF) | 0xFF000000);
    }

    public static void clearCustomColor(SnapPriorityEvaluator.SnapType type) {
        if (type == null) {
            return;
        }
        CUSTOM_COLORS.remove(type);
    }

    public static void resetCustomColors() {
        CUSTOM_COLORS.clear();
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
            case END_POINT, CENTER_POINT, CENTROID -> ENHANCED_RING_SIZE + 1.0f;          // 重点更大
            case INTERSECTION, TANGENT, VERTEX -> ENHANCED_RING_SIZE;                      // 特殊点
            case MID_POINT, QUADRANT, CONTROL_POINT -> DEFAULT_RING_SIZE + 0.5f;          // 常规点
            case PERPENDICULAR, EXTENSION, HORIZONTAL, VERTICAL, PARALLEL -> DEFAULT_RING_SIZE; // 约束点
            case NEAREST_POINT, GRID_POINT -> DEFAULT_RING_SIZE - 0.5f;                    // 辅助点
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


