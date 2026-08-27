package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

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
        ResolvedCrossSection resolved = road.getCrossSection().resolve(config);
        float maxSlope = road.getMaxSlope() != null ? road.getMaxSlope() : config.getMaxSlope();
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
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(resolved, maxSlope),
            origin.x,
            origin.y,
            width,
            height);
        ImGui.dummy(width, height);
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.lane_count_summary", resolved.laneCount, resolved.carriagewayWidth));
    }

    public static void renderPresetButtons(RoadUiContext ctx, Road road, Runnable onChanged) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ImGui.text(PlotI18n.tr("plugin.road.apply_preset_to_road"));
        float gap = ImGui.getStyle().getItemSpacingX();
        float buttonWidth = (ImGui.getContentRegionAvail().x - gap) * 0.5f;
        int column = 0;
        for (RoadStyle style : config.getStyles()) {
            if (column > 0) {
                ImGui.sameLine(0, gap);
            }
            if (ImGui.button(PlotI18n.tr("preset.road." + style.id) + "##road_style_" + style.id, buttonWidth, 0)) {
                ctx.networkManager().pushHistory();
                road.applyStyle(style);
                if (onChanged != null) {
                    onChanged.run();
                }
            }
            column = (column + 1) % 2;
        }
    }

    public static void renderFields(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();

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

        CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forRoad(road, config, onHistory);
        CrossSectionDraftEditor.render(ctx, mutator, CrossSectionDraftEditorOptions.roadEdit());
    }
}
