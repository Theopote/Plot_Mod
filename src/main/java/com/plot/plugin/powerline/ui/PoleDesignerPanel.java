package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.ui.tower.TowerAdvancedParametersPanel;
import com.plot.plugin.powerline.ui.tower.TowerBasicParametersPanel;
import com.plot.plugin.powerline.ui.tower.TowerDesignerContext;
import com.plot.plugin.powerline.ui.tower.TowerDesignerSession;
import com.plot.plugin.powerline.ui.tower.TowerDesignerUiState;
import com.plot.plugin.powerline.ui.tower.TowerManualStructurePanel;
import com.plot.plugin.powerline.ui.tower.TowerParameterStatusPanel;
import com.plot.plugin.powerline.ui.tower.TowerProfileUiCatalog;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.LinePoleDesignOverrides;
import com.plot.plugin.powerline.style.ParametricFootprintSync;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;


/** 杆塔分层设计器独立窗口（居中弹出、可拖动、不参与 DockSpace 停靠）。 */
public final class PoleDesignerPanel {
    private static final int DESIGNER_WINDOW_FLAGS =
        ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings;
    private static final int NAME_BUFFER_CAPACITY = 128;

    private final PowerLineUiContext ctx;
    private PoleDesign draft;
    private final ImString designNameBuffer = new ImString(NAME_BUFFER_CAPACITY);
    private final ImString saveAsNameBuffer = new ImString(NAME_BUFFER_CAPACITY);
    private boolean closeConfirmPending = false;
    private String openedBaselineJson = "";
    private final ImBoolean designerWindowOpen = new ImBoolean(false);
    private boolean focusOnNextRender;
    private final TowerDesignerUiState towerUiState = new TowerDesignerUiState();
    private final TowerDesignerSession towerSession;
    private PoleDesignerEditScope editScope = PoleDesignerEditScope.DESIGN_TEMPLATE;
    private final TowerBasicParametersPanel towerBasicPanel = new TowerBasicParametersPanel();
    private final TowerAdvancedParametersPanel towerAdvancedPanel = new TowerAdvancedParametersPanel();
    private final TowerManualStructurePanel towerManualPanel = new TowerManualStructurePanel();
    private final TowerParameterStatusPanel towerStatusPanel = new TowerParameterStatusPanel();
    private final PoleDesignerLayoutPanel layoutPanel = new PoleDesignerLayoutPanel();
    private final PoleDesignerToolbar toolbar = new PoleDesignerToolbar();
    private final PoleDesignerLayerPanel layerPanel = new PoleDesignerLayerPanel();
    private final PoleDesignerTowerStructurePanel towerStructurePanel = new PoleDesignerTowerStructurePanel();
    private final PoleDesignerAttachmentPanel attachmentPanel = new PoleDesignerAttachmentPanel();
    private final PoleDesignerFamilyRolePicker familyRolePicker = new PoleDesignerFamilyRolePicker();

    public PoleDesignerPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
        this.towerSession = new TowerDesignerSession(ctx);
        PoleDesignerLayoutPanel.restorePreviewColumnWidth(ctx.state());
    }

    /** 从线路插件打开：调整选中线路上的杆塔参数，不可更换塔型种类。 */
    public void openLineInstance(String baseDesignId) {
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        String openId = LinePoleDesignOverrides.resolveOpenDesignId(
            line,
            baseDesignId,
            ctx.state().getDesignProject());
        openDesign(openId, PoleDesignerEditScope.LINE_INSTANCE, true);
    }

    /** 编辑共享造型模板（内置或用户造型库）。 */
    public void openTemplate(String designId) {
        if (designId == null || designId.isBlank()) {
            createTemplate();
            return;
        }
        openDesign(designId, PoleDesignerEditScope.DESIGN_TEMPLATE, false);
    }

    /** 新建空白共享造型模板。 */
    public void createTemplate() {
        openDesign(null, PoleDesignerEditScope.DESIGN_TEMPLATE, false);
    }

    private void openDesign(String designId, PoleDesignerEditScope scope, boolean applyLineOverrides) {
        editScope = scope;
        PoleDesignResolver resolver = ctx.designResolver();
        PoleDesign source = designId != null ? resolver.find(designId) : null;
        if (source != null) {
            draft = source.copy();
            ctx.state().setPoleDesignerEditingId(source.getId());
            if (applyLineOverrides) {
                applyLineParametricOverride(source.getId());
            }
        } else {
            draft = newBlankDesign();
            ctx.state().setPoleDesignerEditingId("");
        }
        TowerArmAttachmentBinding.inferArmBindings(draft);
        towerUiState.syncFromDraft(draft);
        towerSession.beginSession(draft);
        towerSession.refreshConstraints(draft);
        assignImString(designNameBuffer, draft.getName());
        ctx.state().getDesignDraftHistory().clear();
        captureOpenedBaseline();
        designerWindowOpen.set(true);
        focusOnNextRender = true;
        ctx.state().setPoleDesignerOpen(true);
    }

    /**
     * 塔型族没有单一 {@code poleDesignId}：先选角色，再打开对应设计。
     * 不要用 {@link #createTemplate} 代替空设计——那会落到空白模板。
     */
    public void requestCustomizeFamily(String familyId) {
        familyRolePicker.request(familyId);
    }

    public void render() {
        familyRolePicker.render(this::openLineInstance);
        if (draft != null) {
            renderCloseConfirmPopup();
        } else {
            closeConfirmPending = false;
        }
        if (!ctx.state().isPoleDesignerOpen() || draft == null) {
            return;
        }

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            var center = ImGui.getMainViewport().getCenter();
            ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
            float viewportWidth = ImGui.getIO().getDisplaySizeX();
            float viewportHeight = ImGui.getIO().getDisplaySizeY();
            ImGui.setNextWindowSize(
                PoleDesignerWindowMetrics.initialWidth(viewportWidth),
                PoleDesignerWindowMetrics.initialHeight(viewportHeight),
                imgui.flag.ImGuiCond.Appearing);
            ImGui.setNextWindowSizeConstraints(
                PoleDesignerWindowMetrics.MIN_WIDTH,
                PoleDesignerWindowMetrics.MIN_HEIGHT,
                PoleDesignerWindowMetrics.maxWidth(viewportWidth),
                PoleDesignerWindowMetrics.maxHeight(viewportHeight));
            if (focusOnNextRender) {
                ImGui.setNextWindowFocus();
                focusOnNextRender = false;
            }
            designerWindowOpen.set(true);
            if (!ImGui.begin(
                    windowTitle(draft),
                    designerWindowOpen,
                    DESIGNER_WINDOW_FLAGS)) {
                ImGui.end();
                if (!designerWindowOpen.get()) {
                    handleCloseRequest();
                }
                return;
            }

            try {
                toolbar.render(
                    ctx.state().getDesignDraftHistory().canUndo(),
                    ctx.state().getDesignDraftHistory().canRedo(),
                    () -> applyDraftFromHistory(ctx.state().getDesignDraftHistory().undo(draft)),
                    () -> applyDraftFromHistory(ctx.state().getDesignDraftHistory().redo(draft)));
                float footerHeight = footerReservedHeight();
                float bodyHeight = Math.max(0f, ImGui.getContentRegionAvail().y - footerHeight);
                layoutPanel.render(
                    ctx.state(),
                    bodyHeight,
                    () -> PoleDesignPreviewRenderer.renderVerticalStack(
                        draft,
                        ImGui.getContentRegionAvail().x,
                        ImGui.getContentRegionAvail().y,
                        towerSession.previewShowsLastValidStructure(),
                        towerUiState),
                    () -> {
                        TowerDesignerContext towerContext = new TowerDesignerContext(
                            draft,
                            towerSession,
                            towerUiState,
                            this::pushDraftSnapshot,
                            editScope);
                        renderStructureSection(towerContext);
                        if (!draft.hasTowerStructure()) {
                            ImGui.separator();
                            layerPanel.render(draft, this::pushDraftSnapshot);
                        }
                        ImGui.separator();
                        attachmentPanel.render(draft, towerUiState, this::pushDraftSnapshot);
                        towerStatusPanel.render(towerContext);
                    });
                renderFooter();
            } finally {
                ImGui.end();
                if (!designerWindowOpen.get()) {
                    handleCloseRequest();
                }
            }
        } finally {
            DialogStyleManager.popDialogStyle(styleScope);
        }
    }

    private void handleCloseRequest() {
        if (draft == null || !isDraftDirty()) {
            finalizeClose(false);
            return;
        }
        designerWindowOpen.set(true);
        closeConfirmPending = true;
    }

    private void finalizeClose(boolean committed) {
        dismissCloseConfirmPopup();
        if (draft != null) {
            towerSession.endSession(committed, draft);
        }
        ctx.state().setPoleDesignerOpen(false);
        ctx.state().setPoleDesignerEditingId("");
        draft = null;
        openedBaselineJson = "";
        editScope = PoleDesignerEditScope.DESIGN_TEMPLATE;
        designerWindowOpen.set(false);
    }

    private void dismissCloseConfirmPopup() {
        closeConfirmPending = false;
        if (ImGui.isPopupOpen("##pole_design_close_confirm")) {
            ImGui.closeCurrentPopup();
        }
    }

    private void captureOpenedBaseline() {
        openedBaselineJson = PoleDesignerDraftBaseline.capture(draft);
    }

    private boolean isDraftDirty() {
        return PoleDesignerDraftBaseline.isDirty(draft, openedBaselineJson);
    }

    private void renderCloseConfirmPopup() {
        if (draft == null) {
            dismissCloseConfirmPopup();
            return;
        }
        if (!PowerLineUiWidgets.beginDeferredPopupModal(
                "##pole_design_close_confirm",
                closeConfirmPending,
                () -> closeConfirmPending = false)) {
            return;
        }
        try {
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.close_confirm"));
            boolean saveBlocked = !towerSession.canSaveDraft(draft);
            if (saveBlocked) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.powerline.design.save"), 120, 0)) {
                if (saveDraft(false)) {
                    finalizeClose(true);
                }
                return;
            }
            if (saveBlocked) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.design.discard"), 120, 0)) {
                finalizeClose(false);
                return;
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                dismissCloseConfirmPopup();
            }
        } finally {
            ImGui.endPopup();
        }
    }

    private float footerReservedHeight() {
        return ImGui.getFrameHeight() * 2f
            + DialogStyleManager.SECTION_GAP
            + DialogStyleManager.ITEM_SPACING * 3f;
    }

    private void renderFooter() {
        ImGui.separator();
        DialogLayoutHelper.beginFooter();
        float width = DialogStyleManager.getContentWidth();

        if (DialogLayoutHelper.beginForm("##designer_footer_form")) {
            if (editScope != PoleDesignerEditScope.LINE_INSTANCE) {
                DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.name"));
                if (ImGui.inputText("##design_name", designNameBuffer)) {
                    draft.setName(designNameBuffer.get());
                }
                if (ImGui.isItemActivated()) {
                    pushDraftSnapshot();
                }
            }
            DialogLayoutHelper.endForm();
        }

        boolean lineInstance = editScope == PoleDesignerEditScope.LINE_INSTANCE;
        String saveLabel = lineInstance
            ? PlotI18n.tr("plugin.powerline.design.apply_to_line")
            : PlotI18n.tr("plugin.powerline.design.save");
        String saveAsLabel = lineInstance
            ? PlotI18n.tr("plugin.powerline.design.save_as_template")
            : PlotI18n.tr("plugin.powerline.design.save_as");
        String cancelLabel = PlotI18n.tr("button.plot.cancel");
        float buttonWidth = DialogStyleManager.getStandardButtonWidth(width, 3, saveLabel, saveAsLabel, cancelLabel);
        float buttonsTotal = buttonWidth * 3f + DialogStyleManager.FOOTER_BUTTON_GAP * 2f;
        ImGui.setCursorPosX(DialogStyleManager.getContentStartX() + Math.max(0f, width - buttonsTotal));

        boolean saveBlocked = !towerSession.canSaveDraft(draft);
        if (saveBlocked) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(saveLabel, buttonWidth, 0)) {
            saveDraft(false);
        }
        ImGui.sameLine(0, DialogStyleManager.FOOTER_BUTTON_GAP);
        if (ImGui.button(saveAsLabel, buttonWidth, 0)) {
            assignImString(saveAsNameBuffer, defaultSaveAsName());
            ImGui.openPopup("##pole_design_save_as");
        }
        if (saveBlocked) {
            ImGui.endDisabled();
            if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(PlotI18n.tr("plugin.powerline.design.tower_status_save_blocked_tooltip"));
            }
        }
        ImGui.sameLine(0, DialogStyleManager.FOOTER_BUTTON_GAP);
        if (ImGui.button(cancelLabel, buttonWidth, 0)) {
            handleCloseRequest();
        }

        if (ImGui.beginPopup("##pole_design_save_as")) {
            if (DialogLayoutHelper.beginForm("##pole_design_save_as_form")) {
                DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.save_as_name"));
                ImGui.inputText("##pole_design_save_as_name", saveAsNameBuffer);
                DialogLayoutHelper.endForm();
            }
            if (saveBlocked) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                draft.setName(saveAsNameBuffer.get());
                assignImString(designNameBuffer, draft.getName());
                if (saveDraft(true)) {
                    ImGui.closeCurrentPopup();
                }
            }
            if (saveBlocked) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void pushDraftSnapshot() {
        if (draft != null) {
            ctx.state().getDesignDraftHistory().push(draft);
        }
    }

    private void applyDraftFromHistory(PoleDesign restored) {
        draft = restored;
        assignImString(designNameBuffer, draft.getName());
        towerUiState.syncFromDraft(draft);
        towerSession.afterDraftRestored(draft);
    }

    private void renderStructureSection(TowerDesignerContext towerContext) {
        if (editScope == PoleDesignerEditScope.LINE_INSTANCE) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.design.line_instance_hint"));
            ImGui.separator();
        }

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure"));
        if (editScope == PoleDesignerEditScope.LINE_INSTANCE) {
            renderLockedStructureKind();
        } else {
            renderStructureModeRadios();
        }

        towerBasicPanel.render(towerContext);
        towerAdvancedPanel.render(towerContext);

        if (!draft.hasTowerStructure()) {
            return;
        }

        boolean structureReadOnly = draft.isParametricMode() && !draft.isManualLegacyMode();
        towerManualPanel.renderIfVisible(
            towerContext,
            ignored -> renderTowerStructureEditor(structureReadOnly));
    }

    private String windowTitle(PoleDesign draft) {
        if (editScope == PoleDesignerEditScope.LINE_INSTANCE) {
            return PlotI18n.tr("plugin.powerline.design.window_line_tune", draft.getName());
        }
        return PlotI18n.tr("plugin.powerline.design.window", draft.getName());
    }

    private void renderStructureModeRadios() {
        StructureKind current = currentStructureKind();
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.powerline.design.structure_layered"),
                current == StructureKind.LAYERED)) {
            if (current != StructureKind.LAYERED) {
                pushDraftSnapshot();
                towerSession.syncStructureMode(draft, false);
            }
        }
        ImGui.sameLine();
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.powerline.design.structure_parametric"),
                current == StructureKind.PARAMETRIC)) {
            if (current != StructureKind.PARAMETRIC) {
                pushDraftSnapshot();
                towerSession.switchToParametricTower(draft);
            }
        }
        ImGui.sameLine();
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.powerline.design.structure_manual"),
                current == StructureKind.MANUAL)) {
            if (current != StructureKind.MANUAL) {
                pushDraftSnapshot();
                towerSession.switchToManualTower(draft);
                towerUiState.showAdvancedStructure.set(true);
            }
        }
    }

    private StructureKind currentStructureKind() {
        if (!draft.hasTowerStructure()) {
            return StructureKind.LAYERED;
        }
        if (draft.isManualLegacyMode()) {
            return StructureKind.MANUAL;
        }
        return StructureKind.PARAMETRIC;
    }

    private enum StructureKind {
        LAYERED,
        PARAMETRIC,
        MANUAL
    }

    private void renderLockedStructureKind() {
        if (!draft.hasTowerStructure()) {
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_kind_legacy_layers"));
            return;
        }
        if (draft.isManualLegacyMode()) {
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_kind_manual_legacy"));
            return;
        }
        if (draft.isParametricMode() && draft.getGeneratorConfig() != null) {
            String profileLabel = TowerProfileUiCatalog.labelFor(draft.getGeneratorConfig().profileId());
            PowerLineUiWidgets.text(PlotI18n.tr(
                "plugin.powerline.design.structure_kind_parametric",
                profileLabel));
            return;
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_tower"));
    }

    private void renderTowerStructureEditor(boolean readOnly) {
        if (readOnly) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.design.tower_structure_readonly_hint"));
            towerStructurePanel.renderInspectSummary(draft);
            ImGui.checkbox(
                PlotI18n.tr("plugin.powerline.design.tower_structure_expand_geometry"),
                towerUiState.expandGeneratedGeometry);
            if (towerUiState.expandGeneratedGeometry.get()) {
                towerStructurePanel.renderInspectDetails(draft);
            }
            return;
        }
        towerStructurePanel.render(draft, this::pushDraftSnapshot);
    }

    private void applyLineParametricOverride(String designId) {
        if (draft == null || !draft.hasTowerStructure()) {
            return;
        }
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null || !line.hasParametricTowerConfig()) {
            return;
        }
        TowerGeneratorConfig lineConfig = line.getParametricTowerConfig();
        if (lineConfig == null || !targetsLineParametricOverride(line, designId, lineConfig)) {
            return;
        }
        TowerGeneratorConfig draftConfig = draft.getGeneratorConfig();
        if (draftConfig != null && draft.isParametricMode()
                && draftConfig.profileId().equals(lineConfig.profileId())) {
            draft.setGeneratorConfig(draftConfig.withParameters(lineConfig.parameters()));
        } else if (lineConfig.isParametric()) {
            draft.setGeneratorConfig(lineConfig.copy());
        }
        if (draft.isParametricMode()) {
            towerSession.refreshConstraints(draft);
        }
    }

    private boolean targetsLineParametricOverride(
            PowerLineFootprint line,
            String designId,
            TowerGeneratorConfig lineConfig) {
        if (line.hasPoleDesign() && designId != null && designId.equals(line.getPoleDesignId())) {
            return true;
        }
        if (!line.hasTowerFamily()) {
            return false;
        }
        TowerGeneratorConfig draftConfig = draft.getGeneratorConfig();
        if (draftConfig != null && draft.isParametricMode()) {
            return lineConfig.profileId().equals(draftConfig.profileId());
        }
        return lineConfig.isParametric();
    }

    private boolean saveDraft(boolean forceNewId) {
        if (draft == null) {
            return false;
        }
        if (!towerSession.canSaveDraft(draft)) {
            return false;
        }
        if (editScope == PoleDesignerEditScope.LINE_INSTANCE) {
            if (forceNewId) {
                return saveAsSharedTemplate();
            }
            return applyLineInstanceDraft();
        }
        return saveSharedDesignDraft(forceNewId);
    }

    private boolean applyLineInstanceDraft() {
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (ParametricFootprintSync.usesParametricTower(draft)) {
            towerSession.syncParametricConfigToSelectedLine(draft);
        } else if (line != null) {
            ctx.actions().saveLineInstancePoleDesign(line, draft);
            String instanceId = LinePoleDesignOverrides.lineInstanceDesignId(line);
            ctx.state().setPoleDesignerEditingId(instanceId);
            PoleDesign saved = ctx.designResolver().find(instanceId);
            if (saved != null) {
                draft = saved.copy();
            }
        }
        towerSession.commitFootprintBaselineAfterSave();
        assignImString(designNameBuffer, draft.getName());
        ctx.state().getDesignDraftHistory().clear();
        captureOpenedBaseline();
        ctx.state().setProjectStatus(
            PlotI18n.tr("plugin.powerline.design.applied_to_line"),
            ProjectStatusSeverity.SUCCESS);
        return true;
    }

    private boolean saveAsSharedTemplate() {
        PoleDesign saved = new PoleDesign(saveAsNameBuffer.get());
        saved.setLayers(draft.getLayers());
        saved.setAttachments(draft.getAttachments());
        saved.setTowerStructure(draft.getTowerStructure());
        saved.setGeneratorConfig(draft.getGeneratorConfig());
        ctx.actions().savePoleDesign(saved);
        ctx.state().notifyStyleGalleryOpenCustomTemplates();
        ctx.state().setProjectStatus(
            PlotI18n.tr("plugin.powerline.design.saved_as_template", saved.getName()),
            ProjectStatusSeverity.SUCCESS);
        return true;
    }

    private boolean saveSharedDesignDraft(boolean forceNewId) {
        if (forceNewId || PoleDesignCatalog.isBuiltinId(draft.getId())) {
            PoleDesign saved = new PoleDesign(draft.getName());
            saved.setLayers(draft.getLayers());
            saved.setAttachments(draft.getAttachments());
            saved.setTowerStructure(draft.getTowerStructure());
            saved.setGeneratorConfig(draft.getGeneratorConfig());
            ctx.actions().savePoleDesign(saved);
            ctx.state().setPoleDesignerEditingId(saved.getId());
            draft = saved.copy();
        } else {
            ctx.actions().savePoleDesign(draft);
        }
        towerSession.syncParametricConfigToSelectedLine(draft);
        towerSession.commitFootprintBaselineAfterSave();
        assignImString(designNameBuffer, draft.getName());
        ctx.state().getDesignDraftHistory().clear();
        captureOpenedBaseline();
        return true;
    }

    private static PoleDesign newBlankDesign() {
        PoleDesign design = new PoleDesign(PlotI18n.tr("plugin.powerline.design.new_name"));
        design.addLayer(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL)));
        return design;
    }

    private String defaultSaveAsName() {
        String base = draft != null ? draft.getName() : "";
        if (editScope == PoleDesignerEditScope.LINE_INSTANCE) {
            base = PlotI18n.tr("plugin.powerline.design.new_name");
        }
        return base + " Copy";
    }

    private static void assignImString(ImString buffer, String text) {
        String value = text != null ? text : "";
        if (value.length() >= NAME_BUFFER_CAPACITY) {
            value = value.substring(0, NAME_BUFFER_CAPACITY - 1);
        }
        buffer.set(value);
    }
}
