package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * 批量横断面编辑：与单条编辑能力对齐（含自行车道、中央分隔、路灯）。
 */
public final class RoadBatchCrossSectionEditor {
    private RoadBatchCrossSectionEditor() {
    }

    public static void renderDraftFields(RoadUiContext ctx, RoadNetworkManager.BatchEditDefaults draft) {
        CrossSectionDraft sectionDraft = CrossSectionDraft.fromBatchDefaults(draft);
        CrossSectionDraftEditor.render(
            ctx,
            sectionDraft,
            CrossSectionDraftEditorOptions.batch(),
            () -> ctx.networkManager().updateBatchEditDraft(sectionDraft.toBatchDefaults()));

        RoadNetworkManager.BatchEditDefaults updatedDraft = sectionDraft.toBatchDefaults();
        renderDraftPreview(ctx, updatedDraft);
        if (ImGui.button(PlotI18n.tr("plugin.road.apply_batch"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.networkManager().applyBatchEdit(updatedDraft);
        }
    }

    private static void renderDraftPreview(RoadUiContext ctx, RoadNetworkManager.BatchEditDefaults draft) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ResolvedCrossSection resolved = draft.toCrossSection().resolve(config);
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.batch_cross_section_preview"));
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
            PlotI18n.tr(
                "plugin.road.lane_count_summary",
                resolved.laneCount,
                resolved.carriagewayWidth));
        if (resolved.includeSlopeBatter) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.batch_slope_preview_summary",
                    String.format("%.1f", resolved.fillSlopeRatio),
                    String.format("%.1f", resolved.cutSlopeRatio)));
        }
    }
}
