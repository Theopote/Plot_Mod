package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadParameterInheritance;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;
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
        if (road == null) {
            return;
        }
        String styleId = road.getStyleId();
        float maxSlope = road.getMaxSlope() != null ? road.getMaxSlope() : config.getMaxSlope();
        ImGui.text(PlotI18n.tr("plugin.road.cross_section_preview"));
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 56f;
        RoadCrossSectionPreviewRenderer.CrossSectionLayout layout;
        if (styleId != null && !styleId.isBlank()) {
            RoadStyle style = RoadStyleCatalog.findById(config, styleId);
            layout = style != null
                ? RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(
                    style, road.getEffectiveThemeId(config))
                : RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
                    road.getCrossSection().resolve(config), maxSlope);
        } else {
            layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
                road.getCrossSection().resolve(config), maxSlope);
        }
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            layout,
            origin.x,
            origin.y,
            width,
            height);
        ImGui.dummy(width, height);
        ResolvedCrossSection resolved = road.getCrossSection().resolve(config);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.lane_count_summary", resolved.laneCount, resolved.carriagewayWidth));
    }

    public static void renderPresetButtons(RoadUiContext ctx, Road road, Runnable onChanged) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        String themeId = road.getEffectiveThemeId(config);
        RoadUiWidgets.textWrapped(PlotI18n.tr("plugin.road.apply_preset_to_road"));
        float gap = ImGui.getStyle().getItemSpacingX();
        float avail = ImGui.getContentRegionAvail().x;
        int columns = avail >= 120f ? 2 : 1;
        float buttonWidth = columns == 2 ? (avail - gap) * 0.5f : avail;
        int column = 0;
        for (RoadStyle style : config.getStyles()) {
            if (column > 0) {
                ImGui.sameLine(0, gap);
            }
            if (ImGui.button(PlotI18n.tr("preset.road." + style.id) + "##road_style_" + style.id, buttonWidth, 0)) {
                ctx.networkManager().pushHistory();
                road.applyStyle(style, themeId);
                if (onChanged != null) {
                    onChanged.run();
                }
            }
            column = (column + 1) % columns;
        }
    }

    public static void renderFields(RoadUiContext ctx, Road road, Runnable onHistory) {
        renderRoadLevelCollapsibles(ctx, road, onHistory);
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
