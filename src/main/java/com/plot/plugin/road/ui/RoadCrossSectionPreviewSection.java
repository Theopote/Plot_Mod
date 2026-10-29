package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.LinkedHashSet;

/** 按选择状态渲染完整横断面预览（标题栏 + 关键指标 + 主视图）。 */
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
        ResolvedCrossSection resolved = ResolvedCrossSection.fromConfig(config);
        renderHeader(resolved);
        RoadCrossSectionPreviewRenderer.render(config);
    }

    private static void renderSingle(RoadUiContext ctx) {
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ResolvedCrossSection resolved = road.getCrossSection().resolve(config);
        renderHeader(resolved);
        RoadCrossSectionEditor.renderPreviewBody(road, config);
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
        ResolvedCrossSection resolved = draft.toCrossSection().resolve(ctx.networkManager().getConfig());
        renderHeader(resolved);
        renderBatchPreview(ctx, draft);
    }

    private static void renderHeader(ResolvedCrossSection resolved) {
        ImGui.text(PlotI18n.tr("plugin.road.cross_section_preview"));
        float avail = ImGui.getContentRegionAvail().x;
        float totalWidth = RoadCrossSectionPreviewRenderer.CrossSectionLayout
            .fromResolved(resolved, 0f)
            .totalWidthBlocks();
        String metrics = PlotI18n.tr(
            "plugin.road.cross_section_preview_metrics",
            Math.round(totalWidth),
            resolved.laneCount,
            resolved.carriagewayWidth,
            resolved.fillSlopeRatio,
            resolved.cutSlopeRatio);
        ImGui.sameLine(Math.max(ImGui.getCursorPosX(), avail - ImGui.calcTextSize(metrics).x));
        ImGui.textColored(PluginUiColors.HINT_GRAY, metrics);
        ImGui.spacing();
    }

    /**
     * 多选预览是否可共用一张断面图：比较 {@link ResolvedCrossSection#resolve} 后的最终显示，
     * 使「继承全局默认」与「显式写入相同有效值」视为一致。
     */
    static boolean selectedRoadsShareCrossSection(RoadUiContext ctx) {
        var network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        if (roadIds.size() < 2) {
            return true;
        }
        ResolvedCrossSection reference = null;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            ResolvedCrossSection current = road.getCrossSection().resolve(config);
            if (reference == null) {
                reference = current;
            } else if (!reference.displayEquals(current)) {
                return false;
            }
        }
        return true;
    }

    private static void renderBatchPreview(RoadUiContext ctx, RoadNetworkManager.BatchEditDefaults draft) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ResolvedCrossSection resolved = draft.toCrossSection().resolve(config);
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 64f;
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(resolved, 0f),
            origin.x,
            origin.y,
            width,
            height);
        ImGui.dummy(width, height);
    }
}
