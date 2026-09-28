package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNodeElevationUtils;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.vertical.RoadVerticalJunctionService;
import com.plot.plugin.road.vertical.VerticalProfileNetworkPropagator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import net.minecraft.world.World;

import java.util.Map;

/**
 * 画布属性侧栏：节点标高与路口几何（圆角/标线）。
 * 立交关系在路径 Tab 编辑。
 */
public final class RoadNodePropertyPanel {

    private final RoadUiContext ctx;

    public RoadNodePropertyPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    /** PropertyPanel 侧栏：节点标高与路口几何；立交关系在路径 Tab 编辑。 */
    public void renderPropertySection(RoadJunctionPanel junctionPanel) {
        RoadNode node = ctx.networkManager().getSelectedNode();
        if (node == null) {
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, formatNodeLabel(node));

        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        renderNodeElevationControls(node, network, config);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.node_grade_separation_in_path_hint"));

        junctionPanel.renderPropertySection();
    }

    private void renderNodeElevationControls(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config) {
        boolean autoMode = node.getManualElevation() == null;
        imgui.type.ImBoolean autoRef = new imgui.type.ImBoolean(autoMode);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.node_elevation_auto") + "##auto", autoRef)) {
            ctx.networkManager().pushHistory();
            if (autoRef.get()) {
                RoadVerticalJunctionService.clearAtGradeSharedElevation(network, node.getId(), config);
            } else {
                RoadVerticalJunctionService.setAtGradeSharedElevation(
                    network,
                    node.getId(),
                    resolveManualLockElevation(node, network, config),
                    config);
                propagateNodeElevation(node, network, config);
            }
        }

        if (!autoRef.get()) {
            ImGui.sameLine();
            int initial = node.getManualElevation() != null
                ? (int) Math.round(node.getManualElevation())
                : resolveManualLockElevation(node, network, config);
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

    private static String formatNodeLabel(RoadNode node) {
        Vec2d pos = node.getPosition();
        return String.format("(%.0f, %.0f) deg=%d", pos.x, pos.y, node.getDegree());
    }

    private int resolveManualLockElevation(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
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
