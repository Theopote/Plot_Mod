package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.RoadUniformElevationUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatElevationRecommendation;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.Collection;
import java.util.List;

/** Confirms adaptive ↔ flat strategy switches with recommended elevations. */
final class RoadVerticalStrategySwitchDialog {

    private enum Mode {
        SINGLE_TO_FLAT,
        BATCH_TO_FLAT_RECOMMENDED,
        BATCH_TO_FLAT_UNIFORM,
        TO_TERRAIN_ADAPTIVE
    }

    private boolean popupPending;
    private Mode mode;
    private List<String> roadIds = List.of();
    private RoadVerticalStrategy targetStrategy = RoadVerticalStrategy.TERRAIN_ADAPTIVE;
    private float elevationDraft = 64f;
    private float terrainRecommendedElevation = Float.NaN;
    private String terrainSummary = "";

    void requestSingleToFlat(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            TerrainSampler terrain) {
        if (road == null) {
            return;
        }
        mode = Mode.SINGLE_TO_FLAT;
        roadIds = List.of(road.getId());
        targetStrategy = RoadVerticalStrategy.FLAT;
        elevationDraft = (float) FlatVerticalIntentSupport.recommendBaseElevation(
            network, road, terrain, config);
        terrainRecommendedElevation = Float.NaN;
        terrainSummary = "";
        if (terrain != null && config != null) {
            FlatElevationRecommendation optimized =
                FlatVerticalIntentSupport.recommendOptimizedElevation(network, road, terrain, config);
            if (optimized.hasRecommendation()) {
                terrainRecommendedElevation = optimized.best().elevation();
                elevationDraft = terrainRecommendedElevation;
                terrainSummary = PlotI18n.tr(
                    "plugin.road.flat_elevation.switch_dialog_summary",
                    optimized.best().elevation(),
                    optimized.best().estimatedCutVolume(),
                    optimized.best().estimatedFillVolume(),
                    optimized.best().estimatedBridgeLength(),
                    optimized.best().estimatedTunnelLength(),
                    optimized.best().estimatedEarthworkBlocks(),
                    optimized.terrainSampleCount());
            } else {
                RoadUniformElevationUtils.FlatRoadRecommendation recommendation =
                    RoadUniformElevationUtils.recommendMedianForRoad(network, road, terrain, config);
                if (recommendation.sampleCount() > 0) {
                    terrainRecommendedElevation = recommendation.elevation();
                    terrainSummary = PlotI18n.tr(
                        "plugin.road.vertical_alignment_flat_recommendation",
                        recommendation.elevation(),
                        recommendation.sampleCount());
                }
            }
        }
        popupPending = true;
    }

    void requestBatchToFlatRecommended(Collection<String> selectedRoadIds) {
        if (selectedRoadIds == null || selectedRoadIds.isEmpty()) {
            return;
        }
        mode = Mode.BATCH_TO_FLAT_RECOMMENDED;
        roadIds = List.copyOf(selectedRoadIds);
        targetStrategy = RoadVerticalStrategy.FLAT;
        popupPending = true;
    }

    void requestBatchToFlatUniform(Collection<String> selectedRoadIds, float elevation) {
        if (selectedRoadIds == null || selectedRoadIds.isEmpty()) {
            return;
        }
        mode = Mode.BATCH_TO_FLAT_UNIFORM;
        roadIds = List.copyOf(selectedRoadIds);
        targetStrategy = RoadVerticalStrategy.FLAT;
        elevationDraft = elevation;
        popupPending = true;
    }

    void requestToTerrainAdaptive(Collection<String> selectedRoadIds) {
        if (selectedRoadIds == null || selectedRoadIds.isEmpty()) {
            return;
        }
        mode = Mode.TO_TERRAIN_ADAPTIVE;
        roadIds = List.copyOf(selectedRoadIds);
        targetStrategy = RoadVerticalStrategy.TERRAIN_ADAPTIVE;
        popupPending = true;
    }

    void renderPopup(RoadUiContext ctx) {
        if (!RoadUiWidgets.beginDeferredPopupModal(
                "##road_vertical_strategy_switch",
                popupPending,
                () -> popupPending = false)) {
            return;
        }
        try {
            renderPopupBody(ctx);
        } finally {
            ImGui.endPopup();
        }
    }

    private void renderPopupBody(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        switch (mode) {
            case SINGLE_TO_FLAT -> {
                ImGui.textWrapped(PlotI18n.tr(
                    "plugin.road.vertical_strategy_confirm_to_flat",
                    elevationDraft));
                if (!terrainSummary.isBlank()) {
                    RoadUiWidgets.textWrappedColored(PluginUiColors.STATUS_INFO, terrainSummary);
                }
                ImGui.setNextItemWidth(160f);
                float[] elevation = {elevationDraft};
                ImGui.dragFloat(
                    PlotI18n.tr("plugin.road.vertical_alignment_flat_elevation"),
                    elevation,
                    0.5f,
                    RoadParameterLimits.ELEVATION_MIN,
                    RoadParameterLimits.ELEVATION_MAX,
                    "%.1f");
                elevationDraft = elevation[0];
                if (!Float.isNaN(terrainRecommendedElevation)
                        && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_adopt_recommendation"))) {
                    elevationDraft = terrainRecommendedElevation;
                }
            }
            case BATCH_TO_FLAT_RECOMMENDED -> ImGui.textWrapped(PlotI18n.tr(
                "plugin.road.vertical_strategy_confirm_batch_flat_recommended",
                roadIds.size()));
            case BATCH_TO_FLAT_UNIFORM -> {
                ImGui.textWrapped(PlotI18n.tr(
                    "plugin.road.vertical_strategy_confirm_batch_flat_uniform",
                    roadIds.size(),
                    elevationDraft));
                ImGui.setNextItemWidth(160f);
                float[] elevation = {elevationDraft};
                ImGui.dragFloat(
                    PlotI18n.tr("plugin.road.vertical_alignment_flat_elevation"),
                    elevation,
                    0.5f,
                    RoadParameterLimits.ELEVATION_MIN,
                    RoadParameterLimits.ELEVATION_MAX,
                    "%.1f");
                elevationDraft = elevation[0];
            }
            case TO_TERRAIN_ADAPTIVE -> ImGui.textWrapped(roadIds.size() <= 1
                ? PlotI18n.tr("plugin.road.vertical_strategy_confirm_to_adaptive")
                : PlotI18n.tr(
                    "plugin.road.vertical_strategy_confirm_batch_to_adaptive",
                    roadIds.size()));
        }
        ImGui.separator();
        if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
            ctx.networkManager().pushHistory();
            applyConfirmed(network, config);
            ctx.onGenerationConfigChanged();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
            ImGui.closeCurrentPopup();
        }
    }

    private void applyConfirmed(RoadNetwork network, RoadSystemConfig config) {
        switch (mode) {
            case SINGLE_TO_FLAT -> {
                Road road = network.getRoad(roadIds.getFirst());
                if (road != null) {
                    FlatVerticalIntentSupport.enableFlatWithBase(
                        network, road, config, elevationDraft);
                }
            }
            case BATCH_TO_FLAT_RECOMMENDED ->
                FlatVerticalIntentSupport.applyBatchFlatRecommended(network, roadIds, config);
            case BATCH_TO_FLAT_UNIFORM ->
                FlatVerticalIntentSupport.applyBatchFlatUniformBase(
                    network, roadIds, elevationDraft, config);
            case TO_TERRAIN_ADAPTIVE -> {
                for (String roadId : roadIds) {
                    Road road = network.getRoad(roadId);
                    if (road == null || !RoadStationing.isStationable(network, road)) {
                        continue;
                    }
                    FlatVerticalIntentSupport.enableTerrainAdaptive(network, road, config);
                }
            }
        }
    }
}
