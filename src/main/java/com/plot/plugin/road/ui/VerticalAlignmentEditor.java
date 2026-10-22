package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
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

    private final FlatProfileControls flatProfileControls = new FlatProfileControls();

    /** 路径 Tab：纵向方式与水平道路基准高程。 */
    public void renderPathProperty(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Runnable onStrategyHistory,
            FlatElevationEditHooks profileHooks,
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
            flatProfileControls.renderPath(
                ctx, network, road, profileHooks, terrainSupplier);
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
                    }
                }
            }
            ImGui.endCombo();
        }
        if (current == RoadVerticalStrategy.FLAT) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_strategy_flat_hint"));
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_strategy_terrain_adaptive_hint"));
        }
    }

}
