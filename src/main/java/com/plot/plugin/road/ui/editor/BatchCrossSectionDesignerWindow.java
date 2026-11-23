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
            (open, working) -> batchDirty());
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

        CrossSectionDraft sectionDraft = CrossSectionDraft.fromBatchDefaults(workingBatchDefaults);
        CrossSectionDraftEditorOptions options = CrossSectionDraftEditorOptions.batchSectionCards();
        CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forDraft(
            sectionDraft,
            () -> workingBatchDefaults = sectionDraft.toBatchDefaultsPreservingBridge(workingBatchDefaults));

        sectionCard("##batch_xs_geometry", "plugin.road.floating_editor.batch_cross_section.section.geometry", () -> {
            CrossSectionDraftEditor.renderAppearanceStrips(ctx, mutator, options);
            CrossSectionDraftEditor.renderMarkings(ctx, mutator, options);
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
                updated -> workingBatchDefaults = updated));
        sectionCard("##batch_xs_guardrail", "plugin.road.floating_editor.cross_section.section.guardrail", () ->
            BridgeStructureControls.renderBatchWorkingGuardrail(
                ctx,
                workingBatchDefaults,
                updated -> workingBatchDefaults = updated));

        ImGui.spacing();
        RoadBatchCrossSectionEditor.renderDraftPreview(ctx, workingBatchDefaults);
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
