package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadParameterInheritance;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.Objects;

/**
 * 道路横断面编辑（行车道、路肩、人行道、标线等）。
 */
public final class RoadCrossSectionEditor {
    private RoadCrossSectionEditor() {
    }

    public static void renderPreview(Road road, RoadSystemConfig config) {
        renderPreview(null, road, config);
    }

    /** 设计器左栏等大预览默认高度下限。 */
    private static final float DESIGNER_PREVIEW_MIN_HEIGHT = 168f;

    public static void renderPreview(RoadNetwork network, Road road, RoadSystemConfig config) {
        // 折叠面板内用固定高度，避免吃光下方控件空间。
        renderPreview(network, road, config, DESIGNER_PREVIEW_MIN_HEIGHT);
    }

    /**
     * @param previewHeight 预览图高度；{@code <= 0} 时吃满当前可用高度（设计器左栏）。
     */
    public static void renderPreview(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            float previewHeight) {
        if (road == null) {
            return;
        }
        renderPreviewSummary(network, road, config);
        renderPreviewBody(network, road, config, previewHeight);
    }

    /**
     * 使用已算好的 layout 画预览（设计器拖滑条时复用缓存，避免每帧重解析）。
     */
    public static void renderPreviewWithLayout(
            Road road,
            RoadSystemConfig config,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout layout,
            float previewHeight) {
        if (road == null || layout == null) {
            return;
        }
        String styleId = road.getStyleId();
        String presetName = styleId != null && !styleId.isBlank()
            ? PlotI18n.tr("preset.road." + styleId)
            : PlotI18n.tr("plugin.road.preset_custom");
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.edit.style_summary",
                presetName,
                Math.round(layout.roadBlocks),
                Math.max(1, road.getCrossSection().getCarriageway().getEffectiveLaneCount())));
        ImGui.spacing();
        renderPreviewBodyWithLayout(layout, previewHeight);
    }

    private static void renderPreviewSummary(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        String styleId = road.getStyleId();
        String presetName = styleId != null && !styleId.isBlank()
            ? PlotI18n.tr("preset.road." + styleId)
            : PlotI18n.tr("plugin.road.preset_custom");
        ResolvedCrossSection resolved = resolvePreviewCrossSection(network, road, config);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.edit.style_summary",
                presetName,
                resolved.carriagewayWidth,
                resolved.laneCount));
        ImGui.spacing();
    }

    public static void renderPreviewBody(RoadNetwork network, Road road, RoadSystemConfig config) {
        renderPreviewBody(network, road, config, DESIGNER_PREVIEW_MIN_HEIGHT);
    }

    public static void renderPreviewBody(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            float previewHeight) {
        if (road == null) {
            return;
        }
        renderPreviewBodyWithLayout(previewLayoutForRoad(network, road, config), previewHeight);
    }

    static void renderPreviewBodyWithLayout(
            RoadCrossSectionPreviewRenderer.CrossSectionLayout layout,
            float previewHeight) {
        if (layout == null) {
            return;
        }
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }
        float availY = ImGui.getContentRegionAvail().y;
        float height = previewHeight > 0f
            ? previewHeight
            : Math.max(DESIGNER_PREVIEW_MIN_HEIGHT, availY > 40f ? availY - 4f : DESIGNER_PREVIEW_MIN_HEIGHT);
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(
            origin.x,
            origin.y,
            origin.x + width,
            origin.y + height,
            PluginUiColors.PANEL_BG_DARK);
        drawList.addRect(
            origin.x,
            origin.y,
            origin.x + width,
            origin.y + height,
            PluginUiColors.PANEL_BORDER);
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            layout,
            origin.x,
            origin.y,
            width,
            height,
            RoadCrossSectionPreviewRenderer.MiniRenderOptions.quickDesign());
        ImGui.dummy(width, height);
    }

    /**
     * 设计器/编辑区预览布局：用道路当前已解析断面，不用预设 style 快照，
     * 否则改参后面板左侧图不会变。纵坡取有效限值，并汇总分段 override 区间。
     */
    static RoadCrossSectionPreviewRenderer.CrossSectionLayout previewLayoutForRoad(
            Road road,
            RoadSystemConfig config) {
        return previewLayoutForRoad(null, road, config, true);
    }

    static RoadCrossSectionPreviewRenderer.CrossSectionLayout previewLayoutForRoad(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        return previewLayoutForRoad(network, road, config, true);
    }

    /**
     * @param includeSlopeOverrideBand 为 false 时跳过全网分段纵坡扫描（浮动设计器拖参用）。
     */
    public static RoadCrossSectionPreviewRenderer.CrossSectionLayout previewLayoutForRoad(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            boolean includeSlopeOverrideBand) {
        float designMax = road.getEffectiveMaxSlope(config);
        float min = designMax;
        float max = designMax;
        boolean hasOverrideBand = false;
        if (includeSlopeOverrideBand && network != null) {
            for (String segmentId : road.getSegmentIds()) {
                RoadEdge edge = network.getEdge(segmentId);
                if (edge == null) {
                    continue;
                }
                for (RoadEdge.SlopeOverride override : edge.getSlopeOverrides()) {
                    hasOverrideBand = true;
                    min = Math.min(min, override.maxSlope);
                    max = Math.max(max, override.maxSlope);
                }
            }
        }
        ResolvedCrossSection previewSection = resolvePreviewCrossSection(network, road, config);
        return RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
            previewSection,
            max,
            hasOverrideBand ? min : -1f,
            com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure.resolve(road, config));
    }

    /**
     * 预览缓存指纹：用道路显式/有效字段快速哈希，避免每帧完整 resolve。
     * 值变化时再走 {@link #previewLayoutForRoad}。
     */
    public static long previewFingerprint(Road road, RoadSystemConfig config) {
        if (road == null) {
            return 0L;
        }
        int width = road.getEffectiveWidth(config);
        int laneCount = road.getCrossSection().getCarriageway().getEffectiveLaneCount();
        long h = width;
        h = 31 * h + laneCount;
        h = 31 * h + road.getEffectiveShoulderWidth(config);
        h = 31 * h + (road.getEffectiveIncludeShoulder(config) ? 1 : 0);
        h = 31 * h + road.getEffectiveBikeLaneWidth(config);
        h = 31 * h + (road.getEffectiveIncludeBikeLane(config) ? 1 : 0);
        h = 31 * h + road.getEffectiveSidewalkWidth(config);
        h = 31 * h + (road.getEffectiveIncludeSidewalk(config) ? 1 : 0);
        h = 31 * h + (road.getIncludeMedian() != null && road.getIncludeMedian() ? 1 : 0);
        h = 31 * h + (road.getMedianWidth() != null ? road.getMedianWidth() : 0);
        h = 31 * h + (road.getEffectiveIncludeSlopeBatter(config) ? 1 : 0);
        h = 31 * h + road.getEffectiveRoadsideEdgeMode(config).ordinal();
        h = 31 * h + Float.floatToIntBits(road.getEffectiveFillSlopeRatio(config));
        h = 31 * h + Float.floatToIntBits(road.getEffectiveCutSlopeRatio(config));
        h = 31 * h + Float.floatToIntBits(road.getEffectiveMaxSlope(config));
        h = 31 * h + Objects.hashCode(road.getGenerateBridgePillars());
        h = 31 * h + Objects.hashCode(road.getBridgePierStyle());
        h = 31 * h + Objects.hashCode(road.getBridgePierDiameterBlocks());
        h = 31 * h + Objects.hashCode(road.getBridgePierOffsetBlocks());
        h = 31 * h + Objects.hashCode(road.getIncludeBridgeGuardrail());
        h = 31 * h + Objects.hashCode(road.getMaterial());
        h = 31 * h + Objects.hashCode(road.getSidewalkMaterial());
        h = 31 * h + Objects.hashCode(road.getMarkingMaterial());
        for (int w : road.getCrossSection().getCarriageway().resolveLaneWidths(width)) {
            h = 31 * h + w;
        }
        return h;
    }

    /** 迷你预览：有可变断面时用道路中点桩号的解析结果，否则道路默认断面。 */
    static ResolvedCrossSection resolvePreviewCrossSection(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        if (road == null) {
            return ResolvedCrossSection.fromConfig(config);
        }
        if (network != null && VariableCrossSectionResolver.hasVariableSections(road)) {
            double length = RoadPlanGeometry.canonicalLength(network, road);
            double station = Math.max(0.0, length * 0.5);
            return VariableCrossSectionResolver.resolve(network, road, station, config);
        }
        return road.getCrossSection() != null
            ? road.getCrossSection().resolve(config)
            : ResolvedCrossSection.fromConfig(config);
    }

    public static void renderPresetButtons(RoadUiContext ctx, Road road, Runnable onChanged) {
        RoadPresetCards.renderForRoad(ctx, road, onChanged);
    }


    /**
     * Edit Tab ROAD-LEVEL：横断面 / 材质 / 附属设施三个折叠区。
     */
    public static void renderRoadLevelCollapsibles(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forRoad(road, config, onHistory);
        CrossSectionDraftEditorOptions options = CrossSectionDraftEditorOptions.roadEdit();

        if (ImGui.button(PlotI18n.tr("plugin.road.inherit_all_defaults") + "##inherit_all")) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.inheritAllDefaults();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.inherit_all_defaults"));
        }
        ImGui.spacing();

        if (RoadParameterInheritance.inheritsAny(road)) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.ACCENT_BLUE, PlotI18n.tr("plugin.road.inheritance_mode_active"));
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.inheritance_mode_active"));
            }
        } else {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.explicit_params_mode"));
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.explicit_params_mode"));
            }
        }
        ImGui.spacing();

        if (ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.edit_cross_section"),
            ImGuiTreeNodeFlags.DefaultOpen)) {
            RoadThemeSelector.renderForRoad(road, config, onHistory);
            ImGui.spacing();
            renderPreview(road, config);
            renderPresetButtons(ctx, road, null);
            CrossSectionDraftEditor.renderCrossSection(ctx, mutator, options);
        }
        if (ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.edit_materials"),
            ImGuiTreeNodeFlags.DefaultOpen)) {
            CrossSectionDraftEditor.renderMaterials(ctx, mutator, options);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.edit_furniture"))) {
            CrossSectionDraftEditor.renderFurniture(ctx, mutator, options);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.edit_bridge_structure"))) {
            BridgeStructureControls.renderRoadOverride(ctx, road, onHistory);
        }
    }
}
