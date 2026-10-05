package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;
import java.util.function.Supplier;

/**
 * 路径 Tab 纵向方式与水平道路基准高程。
 * <p>
 * PVI / 纵断面详细编辑在 {@link VerticalProfileEditor} + {@link com.plot.plugin.road.profile.edit.ProfileEditSession}。
 */
public final class VerticalAlignmentEditor {

    private float flatElevation = 64f;

    private final FlatElevationRecommendationUi flatElevationRecommendationUi =
        new FlatElevationRecommendationUi();

    /** 路径 Tab：纵向方式与水平道路基准高程。 */
    public void renderPathProperty(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Runnable onStrategyHistory,
            Runnable onProfileHistory,
            RoadVerticalStrategySwitchDialog switchDialog,
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
        double roadLength = RoadStationing.canonicalLength(network, road);
        renderVerticalStrategy(
            network, road, roadLength, config, onStrategyHistory, switchDialog, terrainSupplier);
        if (RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT) {
            flatElevationRecommendationUi.renderPathControls(
                ctx, network, road, onProfileHistory, terrainSupplier);
            return;
        }
        if (road.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_path_manual_profile_hint"));
        }
    }

    private void renderVerticalStrategy(
            RoadNetwork network,
            Road road,
            double roadLength,
            RoadSystemConfig config,
            Runnable onStrategyHistory,
            RoadVerticalStrategySwitchDialog switchDialog,
            Supplier<TerrainSampler> terrainSupplier) {
        RoadVerticalStrategy current = RoadVerticalStrategy.fromRoad(road);
        if (ImGui.beginCombo(
                PlotI18n.tr("plugin.road.vertical_strategy"),
                current.label())) {
            for (RoadVerticalStrategy strategy : RoadVerticalStrategy.values()) {
                if (!VerticalProfileDesignRules.slopeAllowed(roadLength)
                        && strategy == RoadVerticalStrategy.TERRAIN_ADAPTIVE) {
                    continue;
                }
                if (ImGui.selectable(strategy.label(), strategy == current)
                        && strategy != current) {
                    if (switchDialog != null) {
                        if (strategy == RoadVerticalStrategy.FLAT) {
                            TerrainSampler terrain = terrainSupplier != null ? terrainSupplier.get() : null;
                            switchDialog.requestSingleToFlat(network, road, config, terrain);
                        } else {
                            switchDialog.requestToTerrainAdaptive(List.of(road.getId()));
                        }
                    } else {
                        if (onStrategyHistory != null) {
                            onStrategyHistory.run();
                        }
                        strategy.applyToRoad(network, road, config);
                        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
                        if (intent != null) {
                            flatElevation = (float) intent.getBaseElevation();
                        }
                    }
                }
            }
            ImGui.endCombo();
        }
        if (current == RoadVerticalStrategy.FLAT) {
            renderFlatElevationField(network, road, config, onStrategyHistory, terrainSupplier);
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_strategy_flat_hint"));
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_strategy_terrain_adaptive_hint"));
        }
    }

    private void renderFlatElevationField(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Runnable onHistory,
            Supplier<TerrainSampler> terrainSupplier) {
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        if (intent != null) {
            flatElevation = (float) intent.getBaseElevation();
        }
        RoadElevationBounds bounds = resolveElevationBounds(terrainSupplier);
        float[] elevation = {flatElevation};
        if (RoadElevationInput.renderDragFloat(
                PlotI18n.tr("plugin.road.vertical_alignment_flat_elevation"),
                elevation,
                bounds,
                0.5f,
                "%.1f")) {
            if (ImGui.isItemDeactivatedAfterEdit()) {
                if (onHistory != null) {
                    onHistory.run();
                }
                flatElevation = elevation[0];
                FlatVerticalIntentSupport.setBaseElevation(
                    network,
                    road,
                    flatElevation,
                    road.getEffectiveMaxSlope(config));
            } else {
                flatElevation = elevation[0];
            }
        }
    }

    private static RoadElevationBounds resolveElevationBounds(Supplier<TerrainSampler> terrainSupplier) {
        TerrainSampler terrain = terrainSupplier != null ? terrainSupplier.get() : null;
        return RoadWorldElevationBounds.resolve(terrain);
    }
}
