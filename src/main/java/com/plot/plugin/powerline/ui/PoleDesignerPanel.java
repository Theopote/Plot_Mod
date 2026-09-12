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
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.Map;

/** 杆塔分层设计器独立窗口（居中弹出、可拖动、不参与 DockSpace 停靠）。 */
public final class PoleDesignerPanel {
    private static final float DESIGNER_WIDTH = 660f;
    private static final float DESIGNER_HEIGHT = 760f;
    private static final int DESIGNER_WINDOW_FLAGS =
        ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings;

    private final PowerLineUiContext ctx;
    private PoleDesign draft;
    private final ImString designNameBuffer = new ImString(64);
    private final ImString saveAsNameBuffer = new ImString(64);
    private boolean closeConfirmPending = false;
    private String openedBaselineJson = "";
    private final ImBoolean designerWindowOpen = new ImBoolean(false);
    private boolean focusOnNextRender;
    private final TowerDesignerUiState towerUiState = new TowerDesignerUiState();
    private final TowerDesignerSession towerSession;
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
    }

    public void open(String designId) {
        PoleDesignResolver resolver = ctx.designResolver();
        PoleDesign source = designId != null ? resolver.find(designId) : null;
        if (source != null) {
            draft = source.copy();
            ctx.state().setPoleDesignerEditingId(source.getId());
            applyLineParametricOverride(source.getId());
        } else {
            draft = newBlankDesign();
            ctx.state().setPoleDesignerEditingId("");
        }
        towerUiState.syncFromDraft(draft);
        towerSession.refreshConstraints(draft);
        designNameBuffer.set(draft.getName());
        ctx.state().getDesignDraftHistory().clear();
        captureOpenedBaseline();
        TowerArmAttachmentBinding.inferArmBindings(draft);
        designerWindowOpen.set(true);
        focusOnNextRender = true;
        ctx.state().setPoleDesignerOpen(true);
    }

    /**
     * 塔型族没有单一 {@code poleDesignId}：先选角色，再打开对应设计。
     * 不要用 {@link #open}{@code null}——那会落到空白设计。
     */
    public void requestCustomizeFamily(String familyId) {
        familyRolePicker.request(familyId);
    }

    public void render() {
        familyRolePicker.render(this::open);
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
            ImGui.setNextWindowSize(DESIGNER_WIDTH, DESIGNER_HEIGHT, imgui.flag.ImGuiCond.Appearing);
            if (focusOnNextRender) {
                ImGui.setNextWindowFocus();
                focusOnNextRender = false;
            }
            designerWindowOpen.set(true);
            if (!ImGui.begin(
                    PlotI18n.tr("plugin.powerline.design.window", draft.getName()),
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
                    bodyHeight,
                    () -> PoleDesignPreviewRenderer.renderVerticalStack(
                        draft,
                        ImGui.getContentRegionAvail().x,
                        ImGui.getContentRegionAvail().y,
                        towerSession.previewShowsLastValidStructure()),
                    () -> {
                        renderStructureSection();
                        ImGui.separator();
                        layerPanel.render(draft, this::pushDraftSnapshot);
                        ImGui.separator();
                        attachmentPanel.render(draft, this::pushDraftSnapshot);
                    });
                renderFooter();
                toolbar.renderPresetConfirmPopup(preset -> {
                    pushDraftSnapshot();
                    draft = preset;
                    designNameBuffer.set(draft.getName());
                    towerUiState.syncFromDraft(draft);
                    towerSession.refreshConstraints(draft);
                    ctx.state().setPoleDesignerEditingId("");
                });
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
            finalizeClose();
            return;
        }
        designerWindowOpen.set(true);
        closeConfirmPending = true;
    }

    private void finalizeClose() {
        dismissCloseConfirmPopup();
        ctx.state().setPoleDesignerOpen(false);
        ctx.state().setPoleDesignerEditingId("");
        draft = null;
        openedBaselineJson = "";
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
            if (ImGui.button(PlotI18n.tr("plugin.powerline.design.save"), 120, 0)) {
                saveDraft(false);
                finalizeClose();
                return;
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.design.discard"), 120, 0)) {
                finalizeClose();
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
            DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.name"));
            if (ImGui.inputText("##design_name", designNameBuffer)) {
                draft.setName(designNameBuffer.get());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
            DialogLayoutHelper.endForm();
        }

        String saveLabel = PlotI18n.tr("plugin.powerline.design.save");
        String saveAsLabel = PlotI18n.tr("plugin.powerline.design.save_as");
        String cancelLabel = PlotI18n.tr("button.plot.cancel");
        float buttonWidth = DialogStyleManager.getStandardButtonWidth(width, 3, saveLabel, saveAsLabel, cancelLabel);
        float buttonsTotal = buttonWidth * 3f + DialogStyleManager.FOOTER_BUTTON_GAP * 2f;
        ImGui.setCursorPosX(DialogStyleManager.getContentStartX() + Math.max(0f, width - buttonsTotal));

        boolean saveBlocked = draft.isParametricMode() && !towerSession.canBuild();
        if (saveBlocked) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(saveLabel, buttonWidth, 0)) {
            saveDraft(false);
        }
        if (saveBlocked) {
            ImGui.endDisabled();
            if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(PlotI18n.tr("plugin.powerline.design.tower_status_save_blocked_tooltip"));
            }
        }
        ImGui.sameLine(0, DialogStyleManager.FOOTER_BUTTON_GAP);
        if (ImGui.button(saveAsLabel, buttonWidth, 0)) {
            saveAsNameBuffer.set(draft.getName() + " Copy");
            ImGui.openPopup("##pole_design_save_as");
        }
        ImGui.sameLine(0, DialogStyleManager.FOOTER_BUTTON_GAP);
        if (ImGui.button(cancelLabel, buttonWidth, 0)) {
            handleCloseRequest();
        }

        if (ImGui.beginPopup("##pole_design_save_as")) {
            ImGui.inputText(PlotI18n.tr("plugin.powerline.design.save_as_name"), saveAsNameBuffer);
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                draft.setName(saveAsNameBuffer.get());
                designNameBuffer.set(draft.getName());
                saveDraft(true);
                ImGui.closeCurrentPopup();
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
        designNameBuffer.set(draft.getName());
        towerUiState.syncFromDraft(draft);
        towerSession.refreshConstraints(draft);
    }

    private void renderStructureSection() {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure"));
        boolean useTower = draft.hasTowerStructure();
        if (ImGui.radioButton(PlotI18n.tr("plugin.powerline.design.structure_legacy"), !useTower)) {
            pushDraftSnapshot();
            draft.clearTowerStructure();
        }
        ImGui.sameLine();
        if (ImGui.radioButton(PlotI18n.tr("plugin.powerline.design.structure_tower"), useTower)) {
            if (!draft.hasTowerStructure()) {
                pushDraftSnapshot();
                draft.setTowerStructure(TowerStructurePresets.taperedLatticeTower());
            }
        }

        TowerDesignerContext towerContext = new TowerDesignerContext(
            draft,
            towerSession,
            towerUiState,
            this::pushDraftSnapshot);
        towerBasicPanel.render(towerContext);
        towerAdvancedPanel.render(towerContext);
        towerStatusPanel.render(towerContext);

        if (!draft.hasTowerStructure()) {
            return;
        }

        towerManualPanel.renderIfVisible(
            towerContext,
            ignored -> towerStructurePanel.render(draft, this::pushDraftSnapshot));
    }

    private void applyLineParametricOverride(String designId) {
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

    private void saveDraft(boolean forceNewId) {
        if (draft == null) {
            return;
        }
        if (forceNewId || PoleDesignCatalog.isBuiltinId(draft.getId())) {
            PoleDesign saved = new PoleDesign(draft.getName());
            saved.setLayers(draft.getLayers());
            saved.setAttachments(draft.getAttachments());
            saved.setTowerStructure(draft.getTowerStructure());
            saved.setEngineeringMetadata(draft.getEngineeringMetadata());
            saved.setGeneratorConfig(draft.getGeneratorConfig());
            ctx.actions().savePoleDesign(saved);
            ctx.state().setPoleDesignerEditingId(saved.getId());
            draft = saved.copy();
        } else {
            ctx.actions().savePoleDesign(draft);
        }
        towerSession.syncParametricConfigToSelectedLine(draft);
        designNameBuffer.set(draft.getName());
        ctx.state().getDesignDraftHistory().clear();
        captureOpenedBaseline();
    }

    /** 塔族可编辑角色 → designId（Regular / Corner / Dead-end / Terminal）。 */
    static Map<TowerRole, String> editableFamilyRoles(TowerFamily family) {
        return PoleDesignerFamilyRolePicker.editableFamilyRoles(family);
    }

    private static PoleDesign newBlankDesign() {
        PoleDesign design = new PoleDesign(PlotI18n.tr("plugin.powerline.design.new_name"));
        design.addLayer(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL)));
        return design;
    }
}
