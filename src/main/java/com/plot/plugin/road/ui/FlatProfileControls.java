package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.Objects;
import java.util.function.Supplier;

/** 水平道路纵断面编辑器：Base Y、推荐 Y 与交叉约束，不暴露 PVI 编辑。 */
final class FlatProfileControls {
    private final FlatElevationRecommendationUi recommendationUi = new FlatElevationRecommendationUi();
    private float flatElevation = 64f;
    private long syncedFlatIntentKey = Long.MIN_VALUE;

    void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            Runnable onHistory,
            Supplier<com.plot.core.terrain.TerrainSampler> terrainSupplier) {
        if (road == null || RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        syncFlatElevation(network, road, config);
        RoadUiSections.section("plugin.road.profile_flat_editor_section");
        renderBaseElevationField(ctx, network, road, config, onHistory, terrainSupplier);
        ImGui.spacing();
        recommendationUi.renderGenerateControls(ctx, network, road, onHistory, terrainSupplier);
        RoadUiWidgets.textWrappedColored(
            com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_strategy_flat_profile_hint"));
    }

    private void syncFlatElevation(RoadNetwork network, Road road, RoadSystemConfig config) {
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        long key = flatIntentKey(road, intent);
        if (key == syncedFlatIntentKey) {
            return;
        }
        syncedFlatIntentKey = key;
        if (intent != null) {
            flatElevation = (float) intent.getBaseElevation();
            syncCompiledAlignmentIfStationable(network, road, config);
        }
    }

    private static void syncCompiledAlignmentIfStationable(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        if (!RoadStationing.isStationable(network, road)) {
            return;
        }
        double roadLength = RoadStationing.canonicalLength(network, road);
        if (roadLength <= 1e-6) {
            return;
        }
        FlatVerticalIntentSupport.syncCompiledAlignment(
            network, road, road.getEffectiveMaxSlope(config));
    }

    private static long flatIntentKey(Road road, FlatVerticalIntent intent) {
        if (road == null) {
            return 0L;
        }
        return Objects.hash(
            road.getId(),
            intent != null ? Double.hashCode(intent.getBaseElevation()) : 0L);
    }

    private void renderBaseElevationField(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Runnable onHistory,
            Supplier<com.plot.core.terrain.TerrainSampler> terrainSupplier) {
        RoadElevationBounds bounds = resolveElevationBounds(terrainSupplier);
        float[] elevation = {flatElevation};
        if (RoadElevationInput.renderDragFloat(
                PlotI18n.tr("plugin.road.vertical_alignment_flat_elevation"),
                elevation,
                bounds,
                0.5f,
                "%.1f")) {
            flatElevation = elevation[0];
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            applyBaseElevation(ctx, network, road, config, onHistory, flatElevation);
        }
    }

    private void applyBaseElevation(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Runnable onHistory,
            float elevation) {
        if (onHistory != null) {
            onHistory.run();
        }
        FlatVerticalIntentSupport.setBaseElevation(
            network,
            road,
            elevation,
            road.getEffectiveMaxSlope(config));
        syncCompiledAlignmentIfStationable(network, road, config);
        syncedFlatIntentKey = flatIntentKey(
            road,
            FlatVerticalIntentSupport.resolveIntent(network, road));
        ctx.requestOverlayRefresh();
    }

    private static RoadElevationBounds resolveElevationBounds(
            Supplier<com.plot.core.terrain.TerrainSampler> terrainSupplier) {
        com.plot.core.terrain.TerrainSampler terrain =
            terrainSupplier != null ? terrainSupplier.get() : null;
        return RoadWorldElevationBounds.resolve(terrain);
    }
}
