package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.Set;

/** 批量桩号映射模式选择与超界预警。 */
final class BatchStationMappingUi {
    private BatchStationMappingUi() {
    }

    static BatchStationMappingMode renderModeSelector(BatchStationMappingMode current, String idSuffix) {
        BatchStationMappingMode mode = current != null ? current : BatchStationMappingMode.PROPORTIONAL;
        String[] labels = {
            PlotI18n.tr("plugin.road.floating_editor.batch_mapping.proportional"),
            PlotI18n.tr("plugin.road.floating_editor.batch_mapping.absolute")
        };
        ImInt index = new ImInt(mode == BatchStationMappingMode.ABSOLUTE ? 1 : 0);
        if (ImGui.combo(
                PlotI18n.tr("plugin.road.floating_editor.batch_mapping.mode") + "##" + idSuffix,
                index,
                labels)) {
            mode = index.get() == 1
                ? BatchStationMappingMode.ABSOLUTE
                : BatchStationMappingMode.PROPORTIONAL;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.batch_station_mapping"));
        }
        return mode;
    }

    static void renderVariablePreviewWarnings(
            RoadNetwork network,
            Road sourceRoad,
            RoadVariableCrossSections source,
            Set<String> targetRoadIds,
            BatchStationMappingMode mode) {
        if (network == null || sourceRoad == null || source == null || targetRoadIds == null || targetRoadIds.size() <= 1) {
            return;
        }
        double sourceLength = RoadStationing.canonicalLength(network, sourceRoad);
        int dropped = 0;
        for (String targetId : targetRoadIds) {
            if (targetId.equals(sourceRoad.getId())) {
                continue;
            }
            Road target = network.getRoad(targetId);
            if (target == null) {
                continue;
            }
            double targetLength = RoadStationing.canonicalLength(network, target);
            dropped += BatchStationDataMapper.mapVariableCrossSections(
                source, sourceLength, targetLength, mode).droppedCount();
        }
        if (dropped > 0) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.floating_editor.batch_mapping.oob_warning", dropped));
        }
    }

    static void renderFacilityPreviewWarnings(
            RoadNetwork network,
            Road sourceRoad,
            RoadStationFacilities source,
            Set<String> targetRoadIds,
            BatchStationMappingMode mode) {
        if (network == null || sourceRoad == null || source == null || targetRoadIds == null || targetRoadIds.size() <= 1) {
            return;
        }
        double sourceLength = RoadStationing.canonicalLength(network, sourceRoad);
        int dropped = 0;
        int clamped = 0;
        for (String targetId : targetRoadIds) {
            if (targetId.equals(sourceRoad.getId())) {
                continue;
            }
            Road target = network.getRoad(targetId);
            if (target == null) {
                continue;
            }
            double targetLength = RoadStationing.canonicalLength(network, target);
            var result = BatchStationDataMapper.mapStationFacilities(
                source, sourceLength, targetLength, mode);
            dropped += result.droppedCount();
            clamped += result.clampedCount();
        }
        if (dropped > 0 || clamped > 0) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr(
                    "plugin.road.floating_editor.batch_mapping.facility_oob_warning",
                    dropped,
                    clamped));
        }
    }

    static void reportApplyResult(com.plot.plugin.road.ui.RoadUiContext ctx, BatchApplyReport report) {
        if (ctx == null || report == null || !report.hasWarnings()) {
            return;
        }
        ctx.status().warning(PlotI18n.tr(
            "plugin.road.floating_editor.batch_mapping.apply_warning",
            report.droppedItems(),
            report.clampedItems()));
    }
}
