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

/** 生成 Tab 水平道路基准高程控件。 */
final class VerticalModeControls {

    private final FlatProfileControls flatProfileControls = new FlatProfileControls();
    private final FlatElevationRecommendationUi flatElevationRecommendationUi =
        new FlatElevationRecommendationUi();

    void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Supplier<TerrainSampler> terrainSupplier) {
        if (road == null || network == null) {
            return;
        }
        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_requires_stationing"));
            return;
        }

        if (RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return;
        }
        flatProfileControls.render(
            ctx,
            network,
            road,
            FlatElevationEditHooks.forLiveNetwork(ctx),
            terrainSupplier);
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
