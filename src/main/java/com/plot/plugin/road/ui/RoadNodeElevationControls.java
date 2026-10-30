package com.plot.plugin.road.ui;

import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNodeElevationUtils;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.RoadVerticalJunctionService;
import com.plot.plugin.road.vertical.VerticalProfileNetworkPropagator;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import net.minecraft.world.World;

import java.util.Map;

/**
 * 平交节点共享标高控件：属性侧栏与复杂交叉口设计器共用。
 */
public final class RoadNodeElevationControls {
    private RoadNodeElevationControls() {
    }

    public static void render(
            RoadUiContext ctx,
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        if (ctx == null || node == null || network == null || config == null) {
            return;
        }
        if (RoadIntersectionPropertySupport.isGradeSeparatedSelection(ctx.networkManager())
                || node.isGradeSeparated()) {
            RoadUiWidgets.textWrappedColored(
                com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.node_grade_separated_elevation_hint"));
            return;
        }

        boolean autoMode = node.getManualElevation() == null;
        imgui.type.ImBoolean autoRef = new imgui.type.ImBoolean(autoMode);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.node_elevation_auto") + "##auto_elev", autoRef)) {
            ctx.networkManager().pushHistory();
            if (autoRef.get()) {
                RoadVerticalJunctionService.clearAtGradeSharedElevation(network, node.getId(), config);
            } else {
                RoadVerticalJunctionService.setAtGradeSharedElevation(
                    network,
                    node.getId(),
                    resolveManualLockElevation(ctx, node, network, config),
                    config);
                propagateNodeElevation(node, network, config);
            }
        }

        if (!autoRef.get()) {
            ImGui.sameLine();
            int initial = node.getManualElevation() != null
                ? (int) Math.round(node.getManualElevation())
                : resolveManualLockElevation(ctx, node, network, config);
            int[] elevation = {initial};
            boolean elevationChanged = ImGui.sliderInt("##elevation", elevation,
                RoadParameterLimits.ELEVATION_MIN,
                RoadParameterLimits.ELEVATION_MAX,
                "Y=%d");
            if (ImGui.isItemActivated()) {
                ctx.networkManager().pushHistory();
            }
            if (elevationChanged) {
                RoadVerticalJunctionService.setAtGradeSharedElevation(
                    network, node.getId(), elevation[0], config);
                propagateNodeElevation(node, network, config);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.node_elevation"));
            }
        }
    }

    private static void propagateNodeElevation(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        VerticalProfileNetworkPropagator.propagateNode(
            network, node, road -> road.getEffectiveMaxSlope(config));
    }

    private static int resolveManualLockElevation(
            RoadUiContext ctx,
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        RoadGenerator generator = new RoadGenerator(
            config, ctx.host().coordinates(), ctx.host().projection());
        TerrainSampler terrain = resolveTerrainSampler(generator);
        Map<String, Integer> previewElevations = ctx.previewManager().hasValidPreview()
            ? ctx.previewManager().getLastNodeElevations()
            : null;
        return RoadNodeElevationUtils.resolveForManualLock(
            node, network, previewElevations, terrain, generator);
    }

    private static TerrainSampler resolveTerrainSampler(RoadGenerator generator) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world != null) {
            return generator.createTerrainSampler(world);
        }
        return new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
    }
}
