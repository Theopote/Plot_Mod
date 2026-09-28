package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.FlatRoadJunctionConflictResolver;
import com.plot.plugin.road.vertical.FlatRoadJunctionConflictResolver.FlatFlatConflict;
import com.plot.plugin.road.vertical.FlatRoadJunctionConflictResolver.FlatRoadAtJunction;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** Per-junction UI for resolving Flat x Flat at-grade elevation conflicts. */
public final class FlatFlatJunctionConflictUi {

    private FlatFlatJunctionConflictUi() {
    }

    public static void render(RoadUiContext ctx, RoadNetwork network, String suffix) {
        if (ctx == null || network == null) {
            return;
        }
        List<FlatFlatConflict> conflicts = FlatRoadJunctionConflictResolver.findFlatFlatConflicts(network);
        if (conflicts.isEmpty()) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        double defaultClearance = config.getDefaultCrossingClearance();
        for (int i = 0; i < conflicts.size(); i++) {
            FlatFlatConflict conflict = conflicts.get(i);
            ImGui.pushID("flat_flat_" + conflict.nodeId() + "_" + i);
            renderConflict(ctx, network, conflict, defaultClearance, suffix);
            ImGui.popID();
            if (i < conflicts.size() - 1) {
                ImGui.separator();
            }
        }
    }

    private static void renderConflict(
            RoadUiContext ctx,
            RoadNetwork network,
            FlatFlatConflict conflict,
            double defaultClearance,
            String suffix) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING_LIGHT,
            PlotI18n.tr("plugin.road.flat_flat_conflict.summary"));
        for (FlatRoadAtJunction entry : conflict.roads()) {
            Road road = network.getRoad(entry.roadId());
            String label = road != null
                ? RoadEdgeListHelper.formatRoadLabel(network, road)
                : entry.roadId();
            ImGui.bulletText(PlotI18n.tr(
                "plugin.road.flat_flat_conflict.road_elevation",
                label,
                entry.baseElevation()));
        }
        ImGui.indent();
        for (int roadIndex = 0; roadIndex < conflict.roads().size(); roadIndex++) {
            FlatRoadAtJunction entry = conflict.roads().get(roadIndex);
            Road road = network.getRoad(entry.roadId());
            String label = road != null
                ? RoadEdgeListHelper.formatRoadLabel(network, road)
                : entry.roadId();
            if (ImGui.button(PlotI18n.tr(
                    "plugin.road.flat_flat_conflict.unify_to",
                    entry.baseElevation())
                    + "##flat_flat_unify_" + entry.roadId() + suffix)) {
                ctx.networkManager().pushHistory();
                int changed = FlatRoadJunctionConflictResolver.unifyToRoadBaseAtJunction(
                    network, conflict.nodeId(), entry.roadId());
                if (changed > 0) {
                    ctx.onGenerationConfigChanged();
                    ctx.status().success(PlotI18n.tr(
                        "plugin.road.flat_flat_conflict.unify_success", changed));
                }
            }
            if (roadIndex + 1 < conflict.roads().size()) {
                ImGui.sameLine();
            }
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.flat_flat_conflict.grade_separate") + suffix)) {
            ctx.networkManager().pushHistory();
            if (FlatRoadJunctionConflictResolver.convertToGradeSeparation(
                    network, conflict.nodeId(), defaultClearance)) {
                ctx.onGenerationConfigChanged();
                ctx.status().success(PlotI18n.tr("plugin.road.flat_flat_conflict.grade_separate_success"));
            }
        }
        ImGui.unindent();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.flat_flat_conflict.drag_hint"));
    }
}
