package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.ui.BridgeStructureControls;
import com.plot.plugin.road.ui.CrossSectionDraftEditor;
import com.plot.plugin.road.ui.CrossSectionDraftEditorOptions;
import com.plot.plugin.road.ui.CrossSectionDraftMutator;
import com.plot.plugin.road.ui.RoadBatchCrossSectionEditor;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.ui.PluginSectionCard;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** 批量横断面浮动编辑器。 */
public final class BatchCrossSectionDesignerWindow extends AbstractRoadFloatingEditor {

    private static final int MAX_BATCH_UNDO = 32;

    private RoadNetworkManager.BatchEditDefaults openBatchDefaults;
    private RoadNetworkManager.BatchEditDefaults workingBatchDefaults;
    private java.util.Set<String> targetRoadIds = java.util.Set.of();
    private final Deque<RoadNetworkManager.BatchEditDefaults> batchUndo = new ArrayDeque<>();
    private final Deque<RoadNetworkManager.BatchEditDefaults> batchRedo = new ArrayDeque<>();

    /** 跨帧复用草稿/Mutator，避免每帧 fromBatchDefaults 分配。 */
    private CrossSectionDraft cachedDraft;
    private CrossSectionDraftMutator cachedMutator;
    private final RoadBatchCrossSectionEditor.PreviewCache previewCache =
        new RoadBatchCrossSectionEditor.PreviewCache();

    @Override
    protected RoadFloatingEditorKind editorKind() {
        return RoadFloatingEditorKind.BATCH_CROSS_SECTION;
    }

    @Override
    protected String windowTitleKey() {
        return "plugin.road.floating_editor.batch_cross_section.title";
    }

    @Override
    protected String windowImGuiId() {
        return "###road_batch_cross_section_designer";
    }

    @Override
    protected String closeConfirmPopupId() {
        return "##road_batch_cross_section_close_confirm";
    }

    @Override
    protected RoadChangeKind confirmChangeKind() {
        return RoadChangeKind.CROSS_SECTION;
    }

    @Override
    protected RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId) {
        invalidateUiCaches();
        targetRoadIds = java.util.Set.copyOf(ctx.networkManager().getSelectedRoadIds());
        if (targetRoadIds.isEmpty()) {
            targetRoadIds = java.util.Set.of(roadId);
        }
        openBatchDefaults = ctx.networkManager().loadBatchEditDefaults();
        workingBatchDefaults = openBatchDefaults;
        batchUndo.clear();
        batchRedo.clear();
        var live = ctx.networkManager().getNetwork();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            (open, working) -> batchDirty());
    }

    @Override
    protected void restoreSession() {
        invalidateUiCaches();
        super.restoreSession();
        workingBatchDefaults = openBatchDefaults;
        batchUndo.clear();
        batchRedo.clear();
    }

    @Override
    public void discard(RoadUiContext ctx) {
        invalidateUiCaches();
        super.discard(ctx);
    }

    @Override
    protected void renderWindowBody(RoadUiContext ctx) {
        if (workingBatchDefaults == null) {
            return;
        }
        int roadCount = targetRoadIds.size();
        ImGui.text(PlotI18n.tr("plugin.road.floating_editor.batch_cross_section.scope", roadCount));
        ImGui.spacing();
        com.plot.plugin.road.ui.RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_writes_explicit"));
        ImGui.spacing();

        CrossSectionDraftMutator mutator = mutatorForWorking();
        CrossSectionDraftEditorOptions options = CrossSectionDraftEditorOptions.batchSectionCards();
        CrossSectionDraftEditorOptions slopeOpts = CrossSectionDraftEditorOptions.styleFloatingSlope();

        sectionCard("##batch_xs_geometry", "plugin.road.floating_editor.batch_cross_section.section.geometry", () -> {
            CrossSectionDraftEditor.renderAppearanceStrips(ctx, mutator, options);
            CrossSectionDraftEditor.renderMarkings(ctx, mutator, options);
        });
        sectionCard("##batch_xs_slope", "plugin.road.floating_editor.batch_cross_section.section.slope", () -> {
            CrossSectionDraftEditor.renderRoadsideEdge(ctx, mutator, slopeOpts);
            CrossSectionDraftEditor.renderSlopeRatios(ctx, mutator, slopeOpts);
        });
        sectionCard("##batch_xs_materials", "plugin.road.floating_editor.batch_cross_section.section.materials", () ->
            CrossSectionDraftEditor.renderMaterials(ctx, mutator, options));
        sectionCard("##batch_xs_furniture", "plugin.road.floating_editor.batch_cross_section.section.furniture", () ->
            CrossSectionDraftEditor.renderFurniture(ctx, mutator, options));

        ImGui.spacing();
        sectionCard("##batch_xs_bridge_piers", "plugin.road.floating_editor.cross_section.section.piers", () ->
            BridgeStructureControls.renderBatchWorkingPiers(
                ctx,
                workingBatchDefaults,
                updated -> {
                    pushBatchCheckpoint();
                    workingBatchDefaults = updated;
                }));
        sectionCard("##batch_xs_guardrail", "plugin.road.floating_editor.cross_section.section.guardrail", () ->
            BridgeStructureControls.renderBatchWorkingGuardrail(
                ctx,
                workingBatchDefaults,
                updated -> {
                    pushBatchCheckpoint();
                    workingBatchDefaults = updated;
                }));

        syncWorkingFromDraft();
        ImGui.spacing();
        RoadBatchCrossSectionEditor.renderDraftPreview(ctx, cachedDraft, previewCache);
    }

    private CrossSectionDraftMutator mutatorForWorking() {
        if (cachedMutator == null || cachedDraft == null) {
            cachedDraft = CrossSectionDraft.fromBatchDefaults(workingBatchDefaults);
            cachedMutator = CrossSectionDraftMutator.forDraftWithHistory(
                cachedDraft,
                this::pushBatchCheckpoint);
            previewCache.invalidate();
        }
        return cachedMutator;
    }

    private void syncWorkingFromDraft() {
        if (cachedDraft == null || workingBatchDefaults == null) {
            return;
        }
        RoadNetworkManager.BatchEditDefaults next =
            cachedDraft.toBatchDefaultsPreservingBridge(workingBatchDefaults);
        if (!Objects.equals(next, workingBatchDefaults)) {
            workingBatchDefaults = next;
            previewCache.invalidate();
        }
    }

    private void pushBatchCheckpoint() {
        if (workingBatchDefaults == null) {
            return;
        }
        // 检查点前先把草稿写回，保证撤销目标与界面一致。
        syncWorkingFromDraft();
        batchUndo.push(workingBatchDefaults);
        batchRedo.clear();
        while (batchUndo.size() > MAX_BATCH_UNDO) {
            batchUndo.removeLast();
        }
    }

    @Override
    protected boolean canEditorUndo() {
        return !batchUndo.isEmpty();
    }

    @Override
    protected boolean canEditorRedo() {
        return !batchRedo.isEmpty();
    }

    @Override
    protected void undoEditorStep() {
        if (batchUndo.isEmpty() || workingBatchDefaults == null) {
            return;
        }
        syncWorkingFromDraft();
        batchRedo.push(workingBatchDefaults);
        workingBatchDefaults = batchUndo.pop();
        invalidateUiCaches();
        if (session != null) {
            session.refreshDirtyState();
        }
    }

    @Override
    protected void redoEditorStep() {
        if (batchRedo.isEmpty() || workingBatchDefaults == null) {
            return;
        }
        syncWorkingFromDraft();
        batchUndo.push(workingBatchDefaults);
        workingBatchDefaults = batchRedo.pop();
        invalidateUiCaches();
        if (session != null) {
            session.refreshDirtyState();
        }
    }

    private void invalidateUiCaches() {
        cachedDraft = null;
        cachedMutator = null;
        previewCache.invalidate();
    }

    private static void sectionCard(String id, String titleKey, Runnable body) {
        PluginSectionCard.render(
            id,
            PlotI18n.tr(titleKey),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            body);
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        if (workingBatchDefaults == null) {
            return;
        }
        syncWorkingFromDraft();
        ctx.networkManager().applyBatchEditWithinPendingEdit(openBatchDefaults, workingBatchDefaults, targetRoadIds);
    }

    @Override
    public boolean matchesScope(java.util.Set<String> selectedRoadIds) {
        return targetRoadIds.equals(selectedRoadIds);
    }

    /** 包内测试：替换工作副本批量默认值。 */
    void replaceWorkingBatchDefaults(RoadNetworkManager.BatchEditDefaults defaults) {
        workingBatchDefaults = defaults;
        invalidateUiCaches();
        if (session != null) {
            session.refreshDirtyState();
        }
    }

    /** 包内测试：压检查点后替换工作副本。 */
    void mutateWorkingWithCheckpoint(RoadNetworkManager.BatchEditDefaults defaults) {
        pushBatchCheckpoint();
        workingBatchDefaults = defaults;
        invalidateUiCaches();
        if (session != null) {
            session.refreshDirtyState();
        }
    }

    private boolean batchDirty() {
        return openBatchDefaults != null
            && workingBatchDefaults != null
            && !Objects.equals(openBatchDefaults, workingBatchDefaults);
    }
}
