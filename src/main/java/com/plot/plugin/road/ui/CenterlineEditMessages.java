package com.plot.plugin.road.ui;

import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.validation.RoadValidationMessage;
import com.plot.plugin.road.validation.RoadValidationMessageCatalog;
import com.plot.utils.PlotI18n;

/** 中心线编辑结果文案（从 {@link RoadEditPanel} 机械提取）。 */
final class CenterlineEditMessages {

    private CenterlineEditMessages() {
    }

    static String format(CenterlineEditResult result) {
        if (result == null) {
            return PlotI18n.tr("plugin.road.centerline_edit_failed");
        }
        if (result.isSuccess()) {
            if (result.detailMessageKey() != null && !result.detailMessageKey().isBlank()) {
                return PlotI18n.tr(result.detailMessageKey());
            }
            if (result.mergedEdgeId() != null) {
                return PlotI18n.tr("plugin.road.centerline_edit_merged");
            }
            if (result.secondEdgeId() != null) {
                return PlotI18n.tr("plugin.road.centerline_edit_split");
            }
            return PlotI18n.tr("plugin.road.centerline_edit_success");
        }
        String key = switch (result.status()) {
            case EDGE_NOT_FOUND -> "plugin.road.centerline_edit_edge_not_found";
            case INVALID_DISTANCE -> "plugin.road.centerline_edit_invalid_distance";
            case INVALID_VERTEX -> "plugin.road.centerline_edit_invalid_vertex";
            case INVALID_RADIUS -> "plugin.road.centerline_edit_invalid_radius";
            case SPLIT_FAILED -> "plugin.road.centerline_edit_split_failed";
            case MERGE_FAILED -> "plugin.road.centerline_edit_merge_failed";
            case ALIGNMENT_STATIONS_INVALID -> "plugin.road.centerline_edit_alignment_invalid";
            case HORIZONTAL_ALIGNMENT_NOT_DEFINED -> "plugin.road.horizontal_alignment_materialize_no_alignment";
            case ROAD_NOT_STATIONABLE -> "plugin.road.horizontal_alignment_materialize_not_stationable";
            case JUNCTION_ENDPOINT_CONFLICT -> null;
            default -> "plugin.road.centerline_edit_failed";
        };
        if (key == null) {
            RoadValidationMessage message = RoadValidationMessageCatalog.fromCenterlineStatus(result.status());
            if (message != null) {
                return PlotI18n.tr(message.titleKey(), message.args())
                    + "\n" + PlotI18n.tr(message.detailKey(), message.args());
            }
            return PlotI18n.tr("plugin.road.centerline_edit_failed");
        }
        return PlotI18n.tr(key);
    }
}
