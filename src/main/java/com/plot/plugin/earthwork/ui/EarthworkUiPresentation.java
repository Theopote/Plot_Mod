package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.manager.EarthworkPreviewState;
import com.plot.plugin.earthwork.model.EarthworkStatusLevel;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.ui.PluginUiColors;

/** 可独立验证的展示规则。 */
public final class EarthworkUiPresentation {
    private EarthworkUiPresentation() { }

    /** 挖填摘要：Y · 挖 · 填 · 差（不含 i18n 格式化以外的展示逻辑）。 */
    public record CutFillSummary(int platformY, long cut, long fill, long diff) {
        public static CutFillSummary of(EarthworkVolumeReport volumes, int platformY) {
            EarthworkVolumeReport safe = volumes != null ? volumes : EarthworkVolumeReport.empty();
            long cut = safe.geometricCutVolume();
            long fill = safe.geometricFillVolume();
            return new CutFillSummary(platformY, cut, fill, Math.abs(cut - fill));
        }
    }

    /** 预览是否由该分区触发（用于种子高度等「分区私有」语义）。 */
    public static boolean belongsTo(EarthworkGenerationResult preview, GradingRegion region) {
        return preview != null && region != null && region.getId().equals(preview.sourceRegionId);
    }

    /**
     * 整场合成预览是否可用于该分区的分析/剖面展示（同站点任意启用分区）。
     */
    public static boolean sharesSitePreview(
            EarthworkGenerationResult preview,
            com.plot.plugin.earthwork.model.EarthworkProject project,
            GradingRegion region) {
        if (preview == null || region == null || project == null) {
            return false;
        }
        if (belongsTo(preview, region)) {
            return true;
        }
        String siteId = preview.sourceSiteId;
        if (siteId == null || siteId.isBlank()) {
            return false;
        }
        var site = project.getActiveSite();
        if (site == null || !siteId.equals(site.getId())) {
            return false;
        }
        return site.getZone(region.getId()) != null;
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
        if (text == null) {
            return null;
        }
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

    public static boolean missingManualHeight(GradingRegion region) {
        return region != null && !region.isAutoBalance()
            && region.getSurfaceMode() == com.plot.plugin.earthwork.model.GradingSurfaceMode.LEVEL_PAD
            && region.getManualTargetElevation() == null;
    }
}
