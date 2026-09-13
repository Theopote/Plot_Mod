package com.plot.plugin.powerline.model;

import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.utils.PlotI18n;

/** 已放置单塔样式显示名解析（不依赖当前 mutable plugin style state）。 */
public final class PlacedSingleTowerStyleResolver {

    private PlacedSingleTowerStyleResolver() {
    }

    public static String resolveStyleName(PlacedSingleTower tower, PowerLineProject project) {
        if (tower == null) {
            return "";
        }
        String styleLineId = tower.getStyleLineId();
        if (SingleTowerStyleFootprint.isStandaloneStyle(styleLineId)) {
            return resolveStandaloneStyleName(tower);
        }
        if (project == null) {
            return "";
        }
        if (styleLineId == null || styleLineId.isBlank()) {
            return PlotI18n.tr("plugin.powerline.single_tower.style_unknown");
        }
        PowerLineFootprint line = project.getLine(styleLineId);
        if (line == null) {
            return PlotI18n.tr("plugin.powerline.single_tower.style_missing");
        }
        return line.getName();
    }

    private static String resolveStandaloneStyleName(PlacedSingleTower tower) {
        String presetId = tower.getStylePresetId();
        if (presetId != null && !presetId.isBlank()) {
            PowerLineStylePreset preset = PowerLineStylePresetCatalog.find(presetId);
            if (preset != null) {
                return PlotI18n.tr(preset.getLabelKey());
            }
        }
        String designLabel = tower.getDesignLabel();
        if (designLabel != null && !designLabel.isBlank()) {
            return designLabel;
        }
        return PlotI18n.tr("plugin.powerline.single_tower.style_preset");
    }
}
