package com.plot.plugin.ui;

import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImColor;

/**
 * 参数分区卡片色板：由当前主题派生，避免硬编码 PANEL_BG_DARK 破坏浅色主题。
 */
public final class PluginSectionColors {
    public final int surface;
    public final int border;
    public final int accentNeutral;
    public final int accentLevel0;
    public final int accentLevel1;
    public final int accentLevel2;

    private PluginSectionColors(
            int surface,
            int border,
            int accentNeutral,
            int accentLevel0,
            int accentLevel1,
            int accentLevel2) {
        this.surface = surface;
        this.border = border;
        this.accentNeutral = accentNeutral;
        this.accentLevel0 = accentLevel0;
        this.accentLevel1 = accentLevel1;
        this.accentLevel2 = accentLevel2;
    }

    public static PluginSectionColors current() {
        try {
            ThemeManager.Theme type = ThemeManager.getInstance().getCurrentThemeType();
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
            return forTheme(type != ThemeManager.Theme.LIGHT, theme);
        } catch (Throwable ignored) {
            return forTheme(true, null);
        }
    }

    /** 纯函数色板，便于单测与主题切换预览。 */
    public static PluginSectionColors forTheme(boolean dark, UITheme.ThemeColors theme) {
        int surface = dark
            ? ImColor.rgba(0.20f, 0.20f, 0.22f, 1.0f)
            : ImColor.rgba(0.88f, 0.88f, 0.90f, 1.0f);
        int border = theme != null ? theme.border : (dark
            ? ImColor.rgba(0.40f, 0.40f, 0.42f, 1.0f)
            : ImColor.rgba(0.68f, 0.68f, 0.70f, 1.0f));
        int accentNeutral = theme != null ? theme.accent : ImColor.rgba(0.30f, 0.55f, 0.90f, 1.0f);
        // 低饱和蓝灰 / 紫灰 / 暖灰，仅作色条与定位。
        int level0 = dark
            ? ImColor.rgba(0.35f, 0.52f, 0.72f, 1.0f)
            : ImColor.rgba(0.28f, 0.48f, 0.72f, 1.0f);
        int level1 = dark
            ? ImColor.rgba(0.55f, 0.42f, 0.68f, 1.0f)
            : ImColor.rgba(0.52f, 0.36f, 0.62f, 1.0f);
        int level2 = dark
            ? ImColor.rgba(0.72f, 0.58f, 0.32f, 1.0f)
            : ImColor.rgba(0.70f, 0.52f, 0.22f, 1.0f);
        return new PluginSectionColors(surface, border, accentNeutral, level0, level1, level2);
    }

    public int accentFor(PluginSectionCard.SectionTone tone) {
        if (tone == null) {
            return accentNeutral;
        }
        return switch (tone) {
            case NEUTRAL -> accentNeutral;
            case LEVEL_0 -> accentLevel0;
            case LEVEL_1 -> accentLevel1;
            case LEVEL_2 -> accentLevel2;
        };
    }
}
