package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.ui.CrossSectionDraftEditor;
import com.plot.plugin.road.ui.CrossSectionDraftEditorOptions;
import com.plot.plugin.road.ui.RoadBatchCrossSectionEditor;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.Objects;

/** 批量横断面浮动编辑器。 */
public final class BatchCrossSectionDesignerWindow extends AbstractRoadFloatingEditor {

    private RoadNetworkManager.BatchEditDefaults openBatchDefaults;
    private RoadNetworkManager.BatchEditDefaults workingBatchDefaults;
    private java.util.Set<String> targetRoadIds = java.util.Set.of();

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
        targetRoadIds = java.util.Set.copyOf(ctx.networkManager().getSelectedRoadIds());
        if (targetRoadIds.isEmpty()) targetRoadIds = java.util.Set.of(roadId);
        openBatchDefaults = ctx.networkManager().loadBatchEditDefaults();
        workingBatchDefaults = openBatchDefaults;
        var live = ctx.networkManager().getNetwork();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            working -> batchDirty());
    }

    @Override
    protected void renderWindowBody(RoadUiContext ctx) {
        if (workingBatchDefaults == null) {
            return;
        }
        int roadCount = targetRoadIds.size();
        ImGui.text(PlotI18n.tr("plugin.road.floating_editor.batch_cross_section.scope", roadCount));
        ImGui.spacing();
        CrossSectionDraft sectionDraft = CrossSectionDraft.fromBatchDefaults(workingBatchDefaults);
        CrossSectionDraftEditorOptions options = CrossSectionDraftEditorOptions.batch();
        CrossSectionDraftEditor.render(
            ctx,
            sectionDraft,
            options,
            () -> workingBatchDefaults = sectionDraft.toBatchDefaults());
        ImGui.spacing();
        RoadBatchCrossSectionEditor.renderDraftPreview(ctx, workingBatchDefaults);
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        if (workingBatchDefaults == null) {
            return;
        }
        ctx.networkManager().applyBatchEditWithinPendingEdit(openBatchDefaults, workingBatchDefaults, targetRoadIds);
    }

    @Override
    public boolean matchesScope(java.util.Set<String> selectedRoadIds) {
        return targetRoadIds.equals(selectedRoadIds);
    }

    @Override
    protected void restoreSession() {
        super.restoreSession();
        workingBatchDefaults = openBatchDefaults;
    }

    /** 包内测试：替换工作副本批量默认值。 */
    void replaceWorkingBatchDefaults(RoadNetworkManager.BatchEditDefaults defaults) {
        workingBatchDefaults = defaults;
    }

    private boolean batchDirty() {
        return openBatchDefaults != null
            && workingBatchDefaults != null
            && !Objects.equals(openBatchDefaults, workingBatchDefaults);
    }
}
