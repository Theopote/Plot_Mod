package com.plot.plugin.powerline.preview.overlay;

import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.ui.PowerLineAutoPoleLabels;
import com.plot.plugin.powerline.ui.PowerLineUiFormat;
import com.plot.utils.PlotI18n;

/** 画布塔位标记 hover 文案。 */
public final class TowerPreviewMarkerTooltip {
    private TowerPreviewMarkerTooltip() {
    }

    public static String format(TowerPreviewMarker marker) {
        if (marker == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(roleLabel(marker.role()));
        builder.append('\n').append(sourceLabel(marker));
        builder.append('\n').append(PlotI18n.tr(
            "plugin.powerline.canvas_preview.station",
            PowerLineUiFormat.format(marker.stationing())));
        if (marker.designLabel() != null && !marker.designLabel().isBlank()) {
            builder.append('\n').append(PlotI18n.tr(
                "plugin.powerline.canvas_preview.design",
                marker.designLabel()));
        }
        if (marker.role() == TowerRole.ANGLE && marker.source() == MarkerSource.CORNER) {
            builder.append('\n').append(PlotI18n.tr("plugin.powerline.canvas_preview.corner_hint"));
        }
        return builder.toString();
    }

    private static String roleLabel(TowerRole role) {
        if (role == null) {
            return PlotI18n.tr("plugin.powerline.canvas_preview.role.suspension");
        }
        return switch (role) {
            case ANGLE -> PlotI18n.tr("plugin.powerline.canvas_preview.role.angle");
            case TERMINAL, DEAD_END -> PlotI18n.tr("plugin.powerline.canvas_preview.role.terminal");
            case SPECIAL -> PlotI18n.tr("plugin.powerline.canvas_preview.role.special");
            default -> PlotI18n.tr("plugin.powerline.canvas_preview.role.suspension");
        };
    }

    private static String sourceLabel(TowerPreviewMarker marker) {
        return switch (marker.source()) {
            case USER_OVERRIDE -> PlotI18n.tr("plugin.powerline.canvas_preview.source.user_override");
            case TERRAIN_AUTO_INSERT -> terrainInsertLabel(marker.sourceReasonKey());
            case ENDPOINT -> PlotI18n.tr("plugin.powerline.canvas_preview.source.endpoint");
            case CORNER -> PlotI18n.tr("plugin.powerline.canvas_preview.source.corner");
            case STANDALONE -> PlotI18n.tr("plugin.powerline.canvas_preview.source.standalone");
            default -> PlotI18n.tr("plugin.powerline.canvas_preview.source.auto_layout");
        };
    }

    private static String terrainInsertLabel(String reasonKey) {
        if (reasonKey == null || reasonKey.isBlank()) {
            return PlotI18n.tr("plugin.powerline.canvas_preview.source.terrain_insert");
        }
        return PlotI18n.tr(
            "plugin.powerline.canvas_preview.source.terrain_insert_reason",
            PowerLineAutoPoleLabels.friendlyReason(new com.plot.plugin.powerline.model.PoleLayoutConstraint(
                0.0,
                reasonKey)));
    }
}
