package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Batch vertical strategy and flat baseline controls for multi-road edit. */
final class RoadVerticalBatchEditor {

    private enum BatchStrategyState {
        MIXED,
        TERRAIN_ADAPTIVE,
        FLAT
    }

    private float unifiedElevationDraft = 64f;
    private String syncedSelectionKey = "";

    void render(
            RoadUiContext ctx,
            Collection<String> selectedRoadIds,
            RoadVerticalStrategySwitchDialog switchDialog) {
        if (selectedRoadIds == null || selectedRoadIds.isEmpty()) {
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        List<String> stationableRoadIds = stationableRoadIds(network, selectedRoadIds);
        if (stationableRoadIds.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_batch_requires_stationing"));
            return;
        }

        ImGui.separator();
        RoadUiSections.group("plugin.road.vertical_batch_section");
        syncDraftSelection(stationableRoadIds, network);

        BatchStrategyState state = resolveBatchStrategy(network, stationableRoadIds);
        String preview = switch (state) {
            case MIXED -> PlotI18n.tr("plugin.road.vertical_batch_strategy_mixed");
            case TERRAIN_ADAPTIVE -> RoadVerticalStrategy.TERRAIN_ADAPTIVE.label();
            case FLAT -> RoadVerticalStrategy.FLAT.label();
        };
        if (ImGui.beginCombo(PlotI18n.tr("plugin.road.vertical_strategy"), preview)) {
            if (ImGui.selectable(
                    RoadVerticalStrategy.TERRAIN_ADAPTIVE.label(),
                    state == BatchStrategyState.TERRAIN_ADAPTIVE)) {
                if (state != BatchStrategyState.TERRAIN_ADAPTIVE) {
                    switchDialog.requestToTerrainAdaptive(stationableRoadIds);
                }
            }
            if (ImGui.selectable(
                    PlotI18n.tr("plugin.road.vertical_batch_strategy_flat_recommended"),
                    state == BatchStrategyState.FLAT)) {
                switchDialog.requestBatchToFlatRecommended(stationableRoadIds);
            }
            ImGui.endCombo();
        }

        FlatVerticalIntentSupport.BatchElevationSummary summary =
            FlatVerticalIntentSupport.summarizeFlatBaseElevations(network, stationableRoadIds);
        String elevationLabel = summary.mixed()
            ? PlotI18n.tr("plugin.road.vertical_batch_elevation_mixed")
            : PlotI18n.tr("plugin.road.vertical_alignment_flat_elevation");
        if (!summary.mixed() && summary.flatRoadCount() > 0) {
            unifiedElevationDraft = (float) summary.commonElevation();
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
        float[] elevation = {unifiedElevationDraft};
        ImGui.dragFloat(
            elevationLabel,
            elevation,
            0.5f,
            -64f,
            320f,
            summary.mixed() ? PlotI18n.tr("plugin.road.vertical_batch_elevation_mixed") : "%.1f");
        unifiedElevationDraft = elevation[0];
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.vertical_batch_apply_uniform_y"))) {
            switchDialog.requestBatchToFlatUniform(stationableRoadIds, unifiedElevationDraft);
        }

        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_batch_hint", stationableRoadIds.size()));
        if (stationableRoadIds.size() < selectedRoadIds.size()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.vertical_batch_skipped_not_stationable",
                    selectedRoadIds.size() - stationableRoadIds.size()));
        }
    }

    private void syncDraftSelection(List<String> stationableRoadIds, RoadNetwork network) {
        String key = String.join(",", stationableRoadIds);
        if (key.equals(syncedSelectionKey)) {
            return;
        }
        syncedSelectionKey = key;
        FlatVerticalIntentSupport.BatchElevationSummary summary =
            FlatVerticalIntentSupport.summarizeFlatBaseElevations(network, stationableRoadIds);
        if (!summary.mixed() && summary.flatRoadCount() > 0) {
            unifiedElevationDraft = (float) summary.commonElevation();
        }
    }

    private static List<String> stationableRoadIds(
            RoadNetwork network,
            Collection<String> selectedRoadIds) {
        List<String> result = new ArrayList<>();
        for (String roadId : selectedRoadIds) {
            Road road = network.getRoad(roadId);
            if (road != null && RoadStationing.isStationable(network, road)) {
                result.add(roadId);
            }
        }
        return result;
    }

    private static BatchStrategyState resolveBatchStrategy(
            RoadNetwork network,
            Collection<String> roadIds) {
        Set<RoadVerticalStrategy> strategies = new LinkedHashSet<>();
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                strategies.add(RoadVerticalStrategy.fromRoad(road));
            }
        }
        if (strategies.size() != 1) {
            return BatchStrategyState.MIXED;
        }
        return strategies.contains(RoadVerticalStrategy.FLAT)
            ? BatchStrategyState.FLAT
            : BatchStrategyState.TERRAIN_ADAPTIVE;
    }
}
