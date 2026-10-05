package com.plot.plugin.road.ui;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadVerticalMode;
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
            Runnable onHistory) {
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
            flatProfileControls.render(ctx, network, road, onHistory, terrainSupplier);
            return;
        }
        if (road.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_mode_manual_profile_generate_hint"));
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_terrain_adaptive_generate_hint"));
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
