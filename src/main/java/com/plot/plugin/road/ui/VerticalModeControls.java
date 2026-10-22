package com.plot.plugin.road.ui;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import java.util.function.Supplier;

/** Generate-tab vertical mode controls: strategy summary and flat base Y only (no PVI editing). */
final class VerticalModeControls {

    private final FlatProfileControls flatProfileControls = new FlatProfileControls();
    private final FlatElevationRecommendationUi flatElevationRecommendationUi =
        new FlatElevationRecommendationUi();

    void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Supplier<TerrainSampler> terrainSupplier,
            Runnable openProfileEditor) {
        if (road == null || network == null) {
            return;
        }
        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_requires_stationing"));
            return;
        }

        RoadVerticalStrategy strategy = RoadVerticalStrategy.fromRoad(road);
        if (strategy == RoadVerticalStrategy.FLAT) {
            flatProfileControls.render(
                ctx,
                network,
                road,
                FlatElevationEditHooks.forLiveNetwork(ctx),
                terrainSupplier);
            return;
        }
        ProfileSourceControls.renderGenerateSummary(ctx, network, road, openProfileEditor);
    }

    FlatElevationProfileOverlay flatElevationProfileOverlay(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        if (road == null || RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return FlatElevationProfileOverlay.EMPTY;
        }
        return flatElevationRecommendationUi.profileOverlay(network, road, config);
    }
}
