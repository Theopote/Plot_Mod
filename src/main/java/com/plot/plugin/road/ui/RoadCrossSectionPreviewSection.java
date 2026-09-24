package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.LinkedHashSet;

/** 按选择状态渲染完整横断面预览。 */
public final class RoadCrossSectionPreviewSection {
    private RoadCrossSectionPreviewSection() {
    }

    public static void render(RoadUiContext ctx) {
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        switch (mode) {
            case NONE -> renderDefault(ctx);
            case SINGLE -> renderSingle(ctx);
            case MULTI -> renderMulti(ctx);
        }
    }

    private static void renderDefault(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadCrossSectionPreviewRenderer.render(config);
    }

    private static void renderSingle(RoadUiContext ctx) {
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }
        RoadCrossSectionEditor.renderPreview(road, ctx.networkManager().getConfig());
    }

    private static void renderMulti(RoadUiContext ctx) {
        if (!selectedRoadsShareCrossSection(ctx)) {
            ImGui.text(PlotI18n.tr("plugin.road.cross_section_preview"));
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.cross_section_preview_mixed"));
            return;
        }
        RoadNetworkManager.BatchEditDefaults draft = ctx.networkManager().loadBatchEditDefaults();
        renderBatchPreview(ctx, draft);
    }

    static boolean selectedRoadsShareCrossSection(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        if (roadIds.size() < 2) {
            return true;
        }
        String signature = null;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            ResolvedCrossSection resolved = road.getCrossSection().resolve(config);
            String current = crossSectionSignature(resolved);
            if (signature == null) {
                signature = current;
            } else if (!signature.equals(current)) {
                return false;
            }
        }
        return true;
    }

    private static String crossSectionSignature(ResolvedCrossSection resolved) {
        return resolved.laneCount + "|"
            + resolved.carriagewayWidth + "|"
            + resolved.includeSidewalk + "|"
            + resolved.sidewalkWidth + "|"
            + resolved.includeMedian + "|"
            + resolved.medianWidth + "|"
            + resolved.includeShoulder + "|"
            + resolved.includeBikeLane;
    }

    private static void renderBatchPreview(RoadUiContext ctx, RoadNetworkManager.BatchEditDefaults draft) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ResolvedCrossSection resolved = draft.toCrossSection().resolve(config);
        ImGui.text(PlotI18n.tr("plugin.road.cross_section_preview"));
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 56f;
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(resolved, draft.maxSlope()),
            origin.x,
            origin.y,
            width,
            height);
        ImGui.dummy(width, height);
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.lane_count_summary", resolved.laneCount, resolved.carriagewayWidth));
    }
}
