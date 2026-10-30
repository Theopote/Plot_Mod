package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 画布属性侧栏：交叉关系、平交标高与路口几何（圆角/标线）。
 */
public final class RoadNodePropertyPanel {

    private final RoadUiContext ctx;
    private final RoadIntersectionDetailPanel intersectionDetailPanel;

    public RoadNodePropertyPanel(RoadUiContext ctx, RoadIntersectionDetailPanel intersectionDetailPanel) {
        this.ctx = ctx;
        this.intersectionDetailPanel = intersectionDetailPanel;
    }

    /** PropertyPanel 侧栏：交叉关系、标高与路口几何。 */
    public void renderPropertySection(RoadJunctionPanel junctionPanel) {
        if (!RoadIntersectionPropertySupport.hasIntersectionSelection(ctx.networkManager())) {
            return;
        }

        float scrollHeight = Math.clamp(ImGui.getContentRegionAvailY() * 0.45f, 120f, 320f);
        boolean scroll = ImGui.beginChild(
            "##road_junction_property_scroll",
            0f,
            scrollHeight,
            false,
            ImGuiWindowFlags.AlwaysVerticalScrollbar);
        try {
            if (scroll) {
                renderPropertyContent(junctionPanel);
            }
        } finally {
            ImGui.endChild();
        }
    }

    private void renderPropertyContent(RoadJunctionPanel junctionPanel) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadNode junctionNode = RoadIntersectionPropertySupport.resolveJunctionNode(
            network, ctx.networkManager());

        if (junctionNode != null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, formatNodeLabel(junctionNode));
        }

        if (intersectionDetailPanel != null) {
            intersectionDetailPanel.renderFocusedSelection(
                network, RoadGradeSeparationControls.Layout.BLOCK);
        }

        if (junctionNode != null) {
            ImGui.separator();
            RoadNodeElevationControls.render(ctx, junctionNode, network, config);

            ImGui.separator();
            junctionPanel.renderForNode(junctionNode, true);
        }
    }

    private static String formatNodeLabel(RoadNode node) {
        Vec2d pos = node.getPosition();
        return PlotI18n.tr("plugin.road.node_position_label", pos.x, pos.y, node.getDegree());
    }
}
