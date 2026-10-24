package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.manager.EarthworkPreviewState;
import com.plot.plugin.earthwork.model.EarthworkStatusLevel;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.ui.PluginUiColors;

/** 可独立验证的展示规则。 */
public final class EarthworkUiPresentation {
    private EarthworkUiPresentation() { }

    public static boolean belongsTo(EarthworkGenerationResult preview, GradingRegion region) {
        return preview != null && region != null && region.getId().equals(preview.sourceRegionId);
    }

    public static Integer initialManualHeight(GradingRegion region, EarthworkGenerationResult preview,
                                             boolean validPreview, TerrainSnapshot terrain) {
        if (validPreview && belongsTo(preview, region)) {
            return preview.resolvedElevation;
        }
        if (region.getManualTargetElevation() != null) {
            return region.getManualTargetElevation();
        }
        var heights = terrain.groundHeights().stream().sorted().toList();
        return heights.isEmpty() ? null : heights.get(heights.size() / 2);
    }

    public static Integer parseHeight(String text, int min, int max) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= min && value <= max ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String elevationLabel(boolean auto, boolean valid) {
        return !valid ? "plugin.earthwork.ui.last_height"
            : auto ? "plugin.earthwork.quick.recommended_y" : "plugin.earthwork.platform_height";
    }

    public static String previewStateKey(EarthworkPreviewState state) {
        return "plugin.earthwork.ui.preview_state." + state.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static int statusColor(EarthworkStatusLevel level) {
        return switch (level) {
            case INFO -> PluginUiColors.STATUS_INFO;
            case SUCCESS -> PluginUiColors.STATUS_OK;
            case WARNING -> PluginUiColors.WARNING;
            case ERROR -> PluginUiColors.ERROR;
        };
    }

    public static int plannedChanges(EarthworkGenerationResult preview) {
        return preview != null ? preview.placementRecords.size() : 0;
    }
}
