package com.plot.plugin.road.ui;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import net.minecraft.world.World;

/**
 * 路径 Tab：当前选中道路的路径属性（纵向方式、水平道路基准高程；多选批量纵向）。
 */
final class RoadPathVerticalPropertyPanel {
    private final VerticalAlignmentEditor verticalAlignmentEditor = new VerticalAlignmentEditor();
    private final RoadVerticalBatchEditor verticalBatchEditor = new RoadVerticalBatchEditor();
    private final RoadVerticalStrategySwitchDialog strategySwitchDialog;

    RoadPathVerticalPropertyPanel(RoadVerticalStrategySwitchDialog strategySwitchDialog) {
        this.strategySwitchDialog = strategySwitchDialog;
    }

    void render(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            return;
        }
        ctx.networkManager().ensureSelectionValid();

        ImGui.separator();
        RoadUiSections.section("plugin.road.path.road_property");

        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        switch (mode) {
            case NONE -> renderNoSelection(network);
            case SINGLE -> renderSingle(ctx, network);
            case MULTI -> verticalBatchEditor.render(
                ctx, ctx.networkManager().getSelectedRoadIds(), strategySwitchDialog);
        }
    }

    void renderDeferredModals(RoadUiContext ctx) {
        strategySwitchDialog.renderPopup(ctx);
    }

    private void renderNoSelection(RoadNetwork network) {
        if (network.getEdges().isEmpty()) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.path.no_road_selected_hint"));
    }

    private void renderSingle(RoadUiContext ctx, RoadNetwork network) {
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.path.current_road"));
        ImGui.sameLine();
        ImGui.text(RoadEdgeListHelper.formatRoadLabel(network, road));
        ImGui.spacing();
        verticalAlignmentEditor.renderPathProperty(
            ctx,
            network,
            road,
            ctx.networkManager().getConfig(),
            ctx.networkManager()::pushHistory,
            strategySwitchDialog,
            () -> requireTerrainOrNull(ctx));
    }

    private static TerrainSampler requireTerrainOrNull(RoadUiContext ctx) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            ctx.status().error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return null;
        }
        return MinecraftTerrainSampler.of(world, ctx.host().coordinates());
    }
}
