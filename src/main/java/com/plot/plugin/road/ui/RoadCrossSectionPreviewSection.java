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
    /** 快速设计预览高度：需容纳挖/填三层整格，并保证方块可读。 */
    private static final float PREVIEW_HEIGHT = 168f;

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
        var layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
            resolved,
            config.getMaxSlope(),
            -1f,
            com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure.fromConfig(
                config, resolved.carriagewayWidth));
        renderHeader(resolved);
        renderPreviewBody(layout);
    }

    private static void renderSingle(RoadUiContext ctx) {
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        var network = ctx.networkManager().getNetwork();
        ResolvedCrossSection resolved =
            RoadCrossSectionEditor.resolvePreviewCrossSection(network, road, config);
        var layout = RoadCrossSectionEditor.previewLayoutForRoad(network, road, config);
        renderHeader(resolved);
        renderPreviewBody(layout);
    }

    private static void renderPreviewBody(RoadCrossSectionPreviewRenderer.CrossSectionLayout layout) {
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f || layout == null) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        // 与上方道路概览条同色深底，让断面图落在明确的深色预览井里。
        drawList.addRectFilled(
            origin.x,
            origin.y,
            origin.x + width,
            origin.y + PREVIEW_HEIGHT,
            PluginUiColors.PANEL_BG_DARK);
        drawList.addRect(
            origin.x,
            origin.y,
            origin.x + width,
            origin.y + PREVIEW_HEIGHT,
            PluginUiColors.PANEL_BORDER);
        // 标题栏只列总宽/边坡；图内保留坡比标注，并拉高预览区放大断面。
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            layout,
            origin.x,
            origin.y,
            width,
            PREVIEW_HEIGHT,
            RoadCrossSectionPreviewRenderer.MiniRenderOptions.quickDesign());
        ImGui.dummy(width, PREVIEW_HEIGHT);
    }

    private static void renderMulti(RoadUiContext ctx) {
        if (!selectedRoadsShareCrossSection(ctx)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.cross_section_preview_mixed"));
            return;
        }
        RoadNetworkManager.BatchEditDefaults draft = ctx.networkManager().loadBatchEditDefaults();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ResolvedCrossSection resolved = draft.toCrossSection().resolve(config);
        float grade = config.getMaxSlope();
        Road primary = ctx.networkManager().getPrimarySelectedRoad();
        if (primary != null) {
            grade = primary.getEffectiveMaxSlope(config);
        }
        var layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(resolved, grade);
        renderHeader(resolved);
        renderBatchPreview(layout);
    }

    private static void renderHeader(ResolvedCrossSection resolved) {
        float totalWidth = RoadCrossSectionPreviewRenderer.CrossSectionLayout
            .fromResolved(resolved, 0f)
            .totalWidthBlocks();
        String metrics = PlotI18n.tr(
            "plugin.road.cross_section_preview_metrics",
            RoadUiFormat.format(totalWidth),
            resolved.laneCount,
            RoadUiFormat.format(resolved.carriagewayWidth),
            RoadUiFormat.format(resolved.fillSlopeRatio),
            RoadUiFormat.format(resolved.cutSlopeRatio));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, metrics);
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

    private static void renderBatchPreview(RoadCrossSectionPreviewRenderer.CrossSectionLayout layout) {
        renderPreviewBody(layout);
    }
}
