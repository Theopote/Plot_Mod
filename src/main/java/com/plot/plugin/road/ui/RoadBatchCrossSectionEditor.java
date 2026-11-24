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
        renderDraftFieldsWithoutPreview(ctx, draft);
        renderDraftPreview(ctx, draft);
    }

    public static void renderDraftFieldsWithoutPreview(
            RoadUiContext ctx,
            RoadNetworkManager.BatchEditDefaults draft) {
        CrossSectionDraft sectionDraft = CrossSectionDraft.fromBatchDefaults(draft);
        CrossSectionDraftEditor.render(
            ctx,
            sectionDraft,
            CrossSectionDraftEditorOptions.batch(),
            () -> ctx.networkManager().updateBatchEditDraft(
                sectionDraft.toBatchDefaultsPreservingBridge(
                    ctx.networkManager().currentBatchEditDefaults())));

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        RoadUiSections.section("plugin.road.edit_bridge_structure");
        BridgeStructureControls.renderBatch(ctx, ctx.networkManager().currentBatchEditDefaults());

        renderApplyButton(ctx, ctx.networkManager().currentBatchEditDefaults());
    }

    static void renderApplyButton(RoadUiContext ctx, RoadNetworkManager.BatchEditDefaults draft) {
        int pending = ctx.networkManager().batchEditPendingFieldCount();
        if (pending > 0) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.batch_pending_changes", pending));
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.batch_no_pending_changes"));
        }
        if (pending <= 0) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.apply_batch"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.networkManager().applyBatchEdit(draft);
        }
        if (pending <= 0) {
            ImGui.endDisabled();
        }
    }

    public static void renderDraftPreview(RoadUiContext ctx, RoadNetworkManager.BatchEditDefaults draft) {
        if (draft == null) {
            return;
        }
        renderDraftPreview(ctx, CrossSectionDraft.fromBatchDefaults(draft), null);
    }

    /**
     * 批量预览；传入 {@link PreviewCache} 时按草稿指纹复用布局，避免每帧 resolve。
     */
    public static void renderDraftPreview(
            RoadUiContext ctx,
            CrossSectionDraft sectionDraft,
            PreviewCache cache) {
        if (sectionDraft == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ResolvedCrossSection resolved;
        RoadCrossSectionPreviewRenderer.CrossSectionLayout layout;
        if (cache != null) {
            long fingerprint = RoadCrossSectionEditor.previewFingerprint(sectionDraft);
            if (cache.layout == null || cache.fingerprint != fingerprint) {
                resolved = ResolvedCrossSection.resolve(sectionDraft.toCrossSection(), config);
                cache.resolved = resolved;
                cache.layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(resolved, 0f);
                cache.fingerprint = fingerprint;
            } else {
                resolved = cache.resolved;
            }
            layout = cache.layout;
        } else {
            resolved = ResolvedCrossSection.resolve(sectionDraft.toCrossSection(), config);
            layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(resolved, 0f);
        }
        renderMiniPreview(resolved, layout);
    }

    private static void renderMiniPreview(
            ResolvedCrossSection resolved,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout layout) {
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
            layout,
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
                    RoadUiFormat.format(resolved.fillSlopeRatio),
                    RoadUiFormat.format(resolved.cutSlopeRatio)));
        } else if (resolved.includeRetainingWall) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.roadside_edge.retaining_wall"));
        }
    }

    /** 批量横断面预览布局缓存。 */
    public static final class PreviewCache {
        long fingerprint = Long.MIN_VALUE;
        ResolvedCrossSection resolved;
        RoadCrossSectionPreviewRenderer.CrossSectionLayout layout;

        public void invalidate() {
            fingerprint = Long.MIN_VALUE;
            resolved = null;
            layout = null;
        }
    }
}
