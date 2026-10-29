package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadParameterInheritance;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * 道路横断面编辑（行车道、路肩、人行道、标线等）。
 */
public final class RoadCrossSectionEditor {
    private RoadCrossSectionEditor() {
    }

    public static void renderPreview(Road road, RoadSystemConfig config) {
        renderPreview(null, road, config);
    }

    public static void renderPreview(RoadNetwork network, Road road, RoadSystemConfig config) {
        if (road == null) {
            return;
        }
        String styleId = road.getStyleId();
        String presetName = styleId != null && !styleId.isBlank()
            ? PlotI18n.tr("preset.road." + styleId)
            : PlotI18n.tr("plugin.road.preset_custom");
        ResolvedCrossSection resolved = road.getCrossSection().resolve(config);
        ImGui.text(PlotI18n.tr(
            "plugin.road.edit.style_summary",
            presetName,
            resolved.carriagewayWidth,
            resolved.laneCount));
        renderPreviewBody(network, road, config);
    }

    public static void renderPreviewBody(RoadNetwork network, Road road, RoadSystemConfig config) {
        if (road == null) {
            return;
        }
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 64f;
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            previewLayoutForRoad(network, road, config),
            origin.x,
            origin.y,
            width,
            height);
        ImGui.dummy(width, height);
    }

    /**
     * 设计器/编辑区预览布局：用道路当前已解析断面，不用预设 style 快照，
     * 否则改参后面板左侧图不会变。纵坡取有效限值，并汇总分段 override 区间。
     */
    static RoadCrossSectionPreviewRenderer.CrossSectionLayout previewLayoutForRoad(
            Road road,
            RoadSystemConfig config) {
        return previewLayoutForRoad(null, road, config);
    }

    static RoadCrossSectionPreviewRenderer.CrossSectionLayout previewLayoutForRoad(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        float designMax = road.getEffectiveMaxSlope(config);
        float min = designMax;
        float max = designMax;
        boolean hasOverrideBand = false;
        if (network != null) {
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
        return RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
            road.getCrossSection().resolve(config),
            max,
            hasOverrideBand ? min : -1f);
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
    }
}
