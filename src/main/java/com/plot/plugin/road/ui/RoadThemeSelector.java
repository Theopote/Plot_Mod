package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;
import com.plot.plugin.road.style.RoadThemeCatalog;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 道路视觉主题下拉选择（认领默认与单条道路编辑共用）。
 */
public final class RoadThemeSelector {
    private RoadThemeSelector() {
    }

    public static void renderForConfig(RoadSystemConfig config, Runnable onChanged) {
        if (config == null) {
            return;
        }
        if (renderCombo("##road_theme_config", config.getRoadThemeId())) {
            String selected = consumeSelection(config.getRoadThemeId());
            if (selected != null && !selected.equals(config.getRoadThemeId())) {
                config.setRoadThemeId(selected);
                reapplyConfigTheme(config);
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.theme_hint"));
    }

    public static void renderForRoad(Road road, RoadSystemConfig config, Runnable onHistory) {
        if (road == null || config == null) {
            return;
        }
        String effectiveThemeId = road.getEffectiveThemeId(config);
        if (renderCombo("##road_theme_road_" + road.getId(), effectiveThemeId)) {
            String selected = consumeSelection(effectiveThemeId);
            if (selected != null && !selected.equals(effectiveThemeId)) {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setThemeId(selected);
                reapplyRoadTheme(road, config, selected);
            }
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.theme_road_hint"));
    }

    private static boolean renderCombo(String id, String selectedThemeId) {
        return ImGui.beginCombo(
            PlotI18n.tr("plugin.road.theme_label") + id,
            PlotI18n.tr("theme.road." + selectedThemeId));
    }

    private static String consumeSelection(String previousThemeId) {
        String selected = previousThemeId;
        for (var theme : RoadThemeCatalog.defaultThemes()) {
            boolean isSelected = theme.id.equals(previousThemeId);
            if (ImGui.selectable(PlotI18n.tr("theme.road." + theme.id) + "##theme_pick_" + theme.id, isSelected)) {
                selected = theme.id;
            }
            if (isSelected) {
                ImGui.setItemDefaultFocus();
            }
        }
        ImGui.endCombo();
        return selected;
    }

    private static void reapplyConfigTheme(RoadSystemConfig config) {
        String presetId = config.getSelectedPreset();
        if (presetId != null && !presetId.isBlank()) {
            RoadStyle style = config.findStyle(presetId);
            if (style != null) {
                config.applyStyle(style);
                return;
            }
        }
        RoadThemeCatalog.applyThemeToConfig(config.getRoadThemeId(), config);
    }

    private static void reapplyRoadTheme(Road road, RoadSystemConfig config, String themeId) {
        String styleId = road.getStyleId();
        if (styleId != null && !styleId.isBlank()) {
            RoadStyle style = RoadStyleCatalog.findById(config, styleId);
            if (style != null) {
                road.applyStyle(style, themeId);
                return;
            }
        }
        RoadThemeCatalog.overlayThemeOnRoad(themeId, road, config);
    }
}
