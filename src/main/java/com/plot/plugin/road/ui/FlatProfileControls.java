package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
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
        syncFlatElevation(network, road);
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadUiSections.section("plugin.road.profile_flat_editor_section");
        renderBaseElevationField(ctx, network, road, config, onHistory);
        ImGui.spacing();
        recommendationUi.renderGenerateControls(ctx, network, road, onHistory, terrainSupplier);
        RoadUiWidgets.textWrappedColored(
            com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_strategy_flat_profile_hint"));
    }

    private void syncFlatElevation(RoadNetwork network, Road road) {
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        long key = flatIntentKey(road, intent);
        if (key == syncedFlatIntentKey) {
            return;
        }
        syncedFlatIntentKey = key;
        if (intent != null) {
            flatElevation = (float) intent.getBaseElevation();
        }
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
            Runnable onHistory) {
        float[] elevation = {flatElevation};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.dragFloat(
                PlotI18n.tr("plugin.road.vertical_alignment_flat_elevation"),
                elevation,
                0.5f,
                -64f,
                320f,
                "%.1f")) {
            flatElevation = elevation[0];
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            applyBaseElevation(ctx, network, road, config, onHistory, flatElevation);
        }
        double roadLength = RoadStationing.isStationable(network, road)
            ? RoadStationing.canonicalLength(network, road)
            : 0.0;
        if (roadLength > 1e-6) {
            FlatVerticalIntentSupport.syncCompiledAlignment(
                network, road, road.getEffectiveMaxSlope(config));
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
        syncedFlatIntentKey = flatIntentKey(
            road,
            FlatVerticalIntentSupport.resolveIntent(network, road));
        ctx.onGenerationConfigChanged();
    }
}
