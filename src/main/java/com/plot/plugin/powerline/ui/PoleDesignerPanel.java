package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerDecorationCatalog;
import com.plot.plugin.powerline.design.structure.TowerDecorationKind;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.ui.component.UIUtils;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 杆塔分层设计器独立窗口。 */
public final class PoleDesignerPanel {
    private static final TowerRole[] FAMILY_EDIT_ROLES = {
        TowerRole.SUSPENSION,
        TowerRole.ANGLE,
        TowerRole.DEAD_END,
        TowerRole.TERMINAL
    };

    private final PowerLineUiContext ctx;
    private PoleDesign draft;
    private final ImString designNameBuffer = new ImString(64);
    private final ImString saveAsNameBuffer = new ImString(64);
    private int selectedPresetIndex = 0;
    private String pendingPresetId = "";
    private boolean presetConfirmPending = false;
    private boolean familyRolePickerPending = false;
    private String pendingFamilyId = "";
    private boolean closeConfirmPending = false;
    private String openedBaselineJson = "";
    private final List<LayerAction> pendingLayerActions = new ArrayList<>();
    private final ImBoolean designerWindowOpen = new ImBoolean(false);

    public PoleDesignerPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void open(String designId) {
        PoleDesignResolver resolver = ctx.designResolver();
        PoleDesign source = designId != null ? resolver.find(designId) : null;
        if (source != null) {
            draft = source.copy();
            ctx.state().setPoleDesignerEditingId(source.getId());
        } else {
            draft = newBlankDesign();
            ctx.state().setPoleDesignerEditingId("");
        }
        designNameBuffer.set(draft.getName());
        ctx.state().getDesignDraftHistory().clear();
        captureOpenedBaseline();
        TowerArmAttachmentBinding.inferArmBindings(draft);
        designerWindowOpen.set(true);
        ctx.state().setPoleDesignerOpen(true);
    }

    /**
     * 塔型族没有单一 {@code poleDesignId}：先选角色，再打开对应设计。
     * 不要用 {@link #open}{@code null}——那会落到空白设计。
     */
    public void requestCustomizeFamily(String familyId) {
        if (familyId == null || familyId.isBlank()) {
            return;
        }
        TowerFamily family = new TowerFamilyResolver().find(familyId);
        if (family == null || editableFamilyRoles(family).isEmpty()) {
            return;
        }
        pendingFamilyId = familyId;
        familyRolePickerPending = true;
    }

    public void render() {
        renderFamilyRolePickerPopup();
        renderCloseConfirmPopup();
        if (!ctx.state().isPoleDesignerOpen() || draft == null) {
            return;
        }

        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        try {
            ImGui.setNextWindowSize(480, 560, imgui.flag.ImGuiCond.FirstUseEver);
            designerWindowOpen.set(true);
            if (!ImGui.begin(
                    PlotI18n.tr("plugin.powerline.design.window", draft.getName()),
                    designerWindowOpen,
                    ImGuiWindowFlags.None)) {
                ImGui.end();
                if (!designerWindowOpen.get()) {
                    handleCloseRequest();
                }
                return;
            }

            try {
                renderDraftHistoryControls();
                ImGui.separator();
                renderPresetSelector();
                ImGui.separator();
                PoleDesignPreviewRenderer.render(draft);
                ImGui.text(PlotI18n.tr("plugin.powerline.design.total_height", draft.totalHeight()));
                ImGui.separator();
                renderStructureSection();
                ImGui.separator();
                renderLayerList();
                ImGui.separator();
                renderAttachmentList();
                ImGui.separator();
                renderSaveActions();

                renderPresetConfirmPopup();
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
        ctx.state().setPoleDesignerOpen(false);
        ctx.state().setPoleDesignerEditingId("");
        draft = null;
        openedBaselineJson = "";
        closeConfirmPending = false;
        designerWindowOpen.set(false);
    }

    private void captureOpenedBaseline() {
        openedBaselineJson = PoleDesignerDraftBaseline.capture(draft);
    }

    private boolean isDraftDirty() {
        return PoleDesignerDraftBaseline.isDirty(draft, openedBaselineJson);
    }

    private void renderCloseConfirmPopup() {
        if (!PowerLineUiWidgets.beginDeferredPopupModal(
                "##pole_design_close_confirm",
                closeConfirmPending,
                () -> closeConfirmPending = false)) {
            return;
        }
        ImGui.textWrapped(PlotI18n.tr("plugin.powerline.design.close_confirm"));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.save"), 120, 0)) {
            saveDraft(false);
            closeConfirmPending = false;
            finalizeClose();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.discard"), 120, 0)) {
            closeConfirmPending = false;
            finalizeClose();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
            closeConfirmPending = false;
        }
        ImGui.endPopup();
    }

    private void renderDraftHistoryControls() {
        boolean undoDisabled = !ctx.state().getDesignDraftHistory().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.undo"), 0, 0)) {
            applyDraftFromHistory(ctx.state().getDesignDraftHistory().undo(draft));
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        boolean redoDisabled = !ctx.state().getDesignDraftHistory().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.redo"), 0, 0)) {
            applyDraftFromHistory(ctx.state().getDesignDraftHistory().redo(draft));
        }
        if (redoDisabled) {
            ImGui.endDisabled();
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
    }

    private void renderPresetSelector() {
        List<PoleDesign> presets = PoleDesignCatalog.defaultDesigns();
        String[] labels = presets.stream()
            .map(d -> PlotI18n.tr("plugin.powerline.design.preset_label", d.getName()))
            .toArray(String[]::new);
        String[] ids = presets.stream().map(PoleDesign::getId).toArray(String[]::new);
        selectedPresetIndex = Math.min(Math.max(0, selectedPresetIndex), labels.length - 1);

        if (ImGui.beginCombo(
                PowerLineUiWidgets.stableLabel("plugin.powerline.design.load_preset", "load_preset"),
                labels[selectedPresetIndex])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(
                        PowerLineUiWidgets.stableSelectableLabel(labels[i], ids[i]),
                        selectedPresetIndex == i)) {
                    selectedPresetIndex = i;
                    pendingPresetId = ids[i];
                    presetConfirmPending = true;
                }
            }
            ImGui.endCombo();
        }
    }

    private void renderStructureSection() {
        ImGui.text(PlotI18n.tr("plugin.powerline.design.structure"));
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

        if (!draft.hasTowerStructure()) {
            return;
        }

        TowerStructureDesign structure = draft.getTowerStructure();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_lattice"), 0, 0)) {
            pushDraftSnapshot();
            draft.setTowerStructure(TowerStructurePresets.taperedLatticeTower());
            syncLatticePresetAttachments(draft.getTowerStructure());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_mega"), 0, 0)) {
            pushDraftSnapshot();
            draft.setTowerStructure(TowerStructurePresets.megaLatticeTower());
            TowerArmAttachmentBinding.inferArmBindings(draft);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_preset_monster"), 0, 0)) {
            pushDraftSnapshot();
            draft.setTowerStructure(TowerStructurePresets.monsterPylonTower());
            TowerArmAttachmentBinding.inferArmBindings(draft);
        }

        ImGui.text(PlotI18n.tr("plugin.powerline.design.structure_stations"));
        for (int i = 0; i < structure.getStations().size(); i++) {
            TowerStation station = structure.getStations().get(i);
            ImGui.pushID("station_" + i);
            ImFloat height = new ImFloat((float) station.getHeight());
            ImFloat width = new ImFloat((float) station.getHalfWidth());
            ImFloat depth = new ImFloat((float) station.getHalfDepth());
            ImGui.setNextItemWidth(50);
            if (ImGui.inputFloat("H", height)) {
                station.setHeight(height.get());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(50);
            if (ImGui.inputFloat("W", width)) {
                station.setHalfWidth(width.get());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(50);
            if (ImGui.inputFloat("D", depth)) {
                station.setHalfDepth(depth.get());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.delete_layer"))) {
                pushDraftSnapshot();
                structure.removeStation(station.getId());
            }
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_add_station"), 0, 0)) {
            pushDraftSnapshot();
            double nextHeight = structure.maxHeight() + 8;
            structure.addStation(new TowerStation(null, nextHeight, 2, 2));
            structure.setBays(TowerStructurePresets.defaultBaysForStations(structure.getStations()));
        }

        ImGui.text(PlotI18n.tr("plugin.powerline.design.structure_arms"));
        List<TowerArm> sortedArms = TowerArmAttachmentBinding.sortedArms(structure);
        for (int i = 0; i < sortedArms.size(); i++) {
            TowerArm arm = sortedArms.get(i);
            ImGui.pushID("arm_" + i);
            int boundCount = countAttachmentsForArm(arm.getId());
            String armHeader = PlotI18n.tr(
                "plugin.powerline.design.arm_deck_header",
                i + 1,
                (int) Math.round(arm.getBaseHeight()),
                boundCount);
            ImGui.setNextItemOpen(i == 0, imgui.flag.ImGuiCond.FirstUseEver);
            if (ImGui.collapsingHeader(armHeader, ImGuiTreeNodeFlags.DefaultOpen)) {
                renderArmControls(structure, arm);
                renderArmDeckActions(arm);
            }
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_add_arm"), 0, 0)) {
            pushDraftSnapshot();
            TowerArm arm = new TowerArm("arm_" + structure.getArms().size(), structure.maxHeight() - 2, 6);
            arm.setVerticalDrop(4);
            structure.addArm(arm);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_add_arm_with_deck"), 0, 0)) {
            pushDraftSnapshot();
            TowerArm arm = new TowerArm("arm_" + structure.getArms().size(), structure.maxHeight() - 2, 6);
            arm.setVerticalDrop(4);
            structure.addArm(arm);
            for (ConductorAttachment attachment : TowerArmAttachmentBinding.createThreePhaseDeck(arm)) {
                draft.addAttachment(attachment);
            }
        }

        renderDecorationSection(structure);
    }

    private void renderDecorationSection(TowerStructureDesign structure) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.design.structure_decorations"));
        for (int i = 0; i < structure.getDecorations().size(); i++) {
            TowerDecoration decoration = structure.getDecorations().get(i);
            ImGui.pushID("deco_" + i);
            renderDecorationRow(decoration);
            ImGui.popID();
        }
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_add_beacon"))) {
            pushDraftSnapshot();
            structure.addDecoration(TowerDecorationCatalog.beaconAtTop(structure.maxHeight()));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_add_antenna"))) {
            pushDraftSnapshot();
            structure.addDecoration(TowerDecorationCatalog.antennaAtTop(structure.maxHeight()));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_add_platform"))) {
            pushDraftSnapshot();
            structure.addDecoration(TowerDecorationCatalog.platformAtTop(structure.maxHeight()));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_add_warning_light"))) {
            pushDraftSnapshot();
            structure.addDecoration(TowerDecorationCatalog.warningLightAtTop(structure.maxHeight()));
        }
    }

    private void renderDecorationRow(TowerDecoration decoration) {
        TowerDecorationKind[] kinds = TowerDecorationKind.values();
        String[] kindLabels = new String[kinds.length];
        int selectedKind = 0;
        for (int i = 0; i < kinds.length; i++) {
            kindLabels[i] = PlotI18n.tr(kinds[i].labelKey());
            if (decoration.getKind() == kinds[i]) {
                selectedKind = i;
            }
        }
        ImInt kindIndex = new ImInt(selectedKind);
        ImGui.setNextItemWidth(110);
        if (ImGui.combo("##deco_kind", kindIndex, kindLabels)) {
            pushDraftSnapshot();
            decoration.setKind(kinds[kindIndex.get()]);
        }
        ImGui.sameLine();
        ImFloat baseHeight = new ImFloat((float) decoration.getBaseHeight());
        ImGui.setNextItemWidth(55);
        if (ImGui.inputFloat(PlotI18n.tr("plugin.powerline.design.structure_arm_height"), baseHeight)) {
            decoration.setBaseHeight(baseHeight.get());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }
        if (decoration.getKind() == TowerDecorationKind.ANTENNA
                || decoration.getKind() == TowerDecorationKind.PLATFORM) {
            ImGui.sameLine();
            ImFloat size = new ImFloat((float) decoration.getSize());
            ImGui.setNextItemWidth(55);
            String sizeLabel = decoration.getKind() == TowerDecorationKind.PLATFORM
                ? PlotI18n.tr("plugin.powerline.design.decoration_platform_radius")
                : PlotI18n.tr("plugin.powerline.design.decoration_antenna_height");
            if (ImGui.inputFloat(sizeLabel, size)) {
                decoration.setSize(size.get());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.decoration_snap_top"))) {
            pushDraftSnapshot();
            decoration.setBaseHeight(structureTopSnapHeight(decoration));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.delete_layer"))) {
            pushDraftSnapshot();
            draft.getTowerStructure().removeDecoration(decoration.getId());
        }
    }

    private double structureTopSnapHeight(TowerDecoration decoration) {
        TowerStructureDesign structure = draft.getTowerStructure();
        double top = structure.maxHeight();
        return decoration.getKind() == TowerDecorationKind.ANTENNA
            || decoration.getKind() == TowerDecorationKind.PLATFORM
            ? top
            : top + 1;
    }

    private void renderArmControls(TowerStructureDesign structure, TowerArm arm) {
        ImFloat baseHeight = new ImFloat((float) arm.getBaseHeight());
        ImFloat reach = new ImFloat((float) arm.getLateralReach());
        ImFloat verticalDrop = new ImFloat((float) arm.getVerticalDrop());
        ImGui.setNextItemWidth(60);
        if (ImGui.inputFloat(PlotI18n.tr("plugin.powerline.design.structure_arm_height"), baseHeight)) {
            arm.setBaseHeight(baseHeight.get());
            TowerArmAttachmentBinding.syncBoundVerticalOffsets(arm, draft.getAttachments());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(60);
        if (ImGui.inputFloat(PlotI18n.tr("plugin.powerline.design.structure_arm_reach"), reach)) {
            arm.setLateralReach(reach.get());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(60);
        if (ImGui.inputFloat(PlotI18n.tr("plugin.powerline.design.structure_arm_drop"), verticalDrop)) {
            arm.setVerticalDrop(verticalDrop.get());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.arm_sync_height"))) {
            pushDraftSnapshot();
            TowerArmAttachmentBinding.syncBoundVerticalOffsets(arm, draft.getAttachments());
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.arm_sync_spread"))) {
            pushDraftSnapshot();
            TowerArmAttachmentBinding.syncBoundLateralSpread(arm, draft.getAttachments());
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.delete_layer"))) {
            pushDraftSnapshot();
            TowerArmAttachmentBinding.clearArmBindings(draft.getAttachments(), arm.getId());
            structure.removeArm(arm.getId());
        }
    }

    private void renderArmDeckActions(TowerArm arm) {
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.arm_add_3phase"))) {
            pushDraftSnapshot();
            for (ConductorAttachment attachment : TowerArmAttachmentBinding.createThreePhaseDeck(arm)) {
                draft.addAttachment(attachment);
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.arm_add_bundled_3phase"))) {
            pushDraftSnapshot();
            for (ConductorAttachment attachment : TowerArmAttachmentBinding.createBundledThreePhaseDeck(arm, 2)) {
                draft.addAttachment(attachment);
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.arm_clear_deck"))) {
            pushDraftSnapshot();
            TowerArmAttachmentBinding.removeArmAttachments(draft, arm.getId());
        }
    }

    private int countAttachmentsForArm(String armId) {
        int count = 0;
        for (ConductorAttachment attachment : draft.getAttachments()) {
            if (armId.equals(attachment.getArmId())) {
                count++;
            }
        }
        return count;
    }

    private void syncLatticePresetAttachments(TowerStructureDesign structure) {
        draft.getAttachments().clear();
        TowerArm mainArm = structure.getArms().isEmpty() ? null : structure.getArms().getFirst();
        if (mainArm != null) {
            for (ConductorAttachment attachment : TowerArmAttachmentBinding.createThreePhaseDeck(mainArm)) {
                draft.addAttachment(attachment);
            }
        } else {
            draft.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(18.0, -6, 0, 6));
        }
    }

    private void renderAttachmentList() {
        ImGui.text(PlotI18n.tr("plugin.powerline.design.attachments"));
        if (draft.hasTowerStructure()) {
            renderTowerAttachmentDecks();
            return;
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.attachment_preset_single"), 0, 0)) {
            pushDraftSnapshot();
            draft.setAttachments(ConductorAttachmentPresets.singleConductor(12.0));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.attachment_preset_3phase_h"), 0, 0)) {
            pushDraftSnapshot();
            draft.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(12.0));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.attachment_preset_3phase_v"), 0, 0)) {
            pushDraftSnapshot();
            draft.setAttachments(ConductorAttachmentPresets.threePhaseVertical(12.0));
        }

        for (int i = 0; i < draft.getAttachments().size(); i++) {
            ConductorAttachment attachment = draft.getAttachments().get(i);
            ImGui.pushID("att_" + attachment.getId());
            renderAttachmentRow(attachment);
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.add_attachment"), 0, 0)) {
            pushDraftSnapshot();
            draft.addAttachment(new ConductorAttachment());
        }
    }

    private void renderTowerAttachmentDecks() {
        Map<String, List<ConductorAttachment>> grouped = TowerArmAttachmentBinding.groupByArm(draft);
        TowerStructureDesign structure = draft.getTowerStructure();
        List<TowerArm> arms = TowerArmAttachmentBinding.sortedArms(structure);
        for (TowerArm arm : arms) {
            List<ConductorAttachment> deck = grouped.getOrDefault(arm.getId(), List.of());
            ImGui.pushID("deck_" + arm.getId());
            if (ImGui.treeNode(PlotI18n.tr(
                "plugin.powerline.design.arm_attachment_deck",
                arm.getId(),
                deck.size()))) {
                for (int i = 0; i < deck.size(); i++) {
                    ConductorAttachment deckAttachment = deck.get(i);
                    ImGui.pushID("att_" + deckAttachment.getId());
                    renderAttachmentRow(deckAttachment, arm);
                    ImGui.popID();
                }
                ImGui.treePop();
            }
            ImGui.popID();
        }
        List<ConductorAttachment> unassigned = grouped.get(null);
        if (unassigned != null && !unassigned.isEmpty()) {
            if (ImGui.treeNode(PlotI18n.tr(
                "plugin.powerline.design.arm_unassigned_attachments",
                unassigned.size()))) {
                for (int i = 0; i < unassigned.size(); i++) {
                    ConductorAttachment freeAttachment = unassigned.get(i);
                    ImGui.pushID("free_" + freeAttachment.getId());
                    renderAttachmentRow(freeAttachment, null);
                    ImGui.popID();
                }
                ImGui.treePop();
            }
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.arm_add_top_wires"), 0, 0)) {
            pushDraftSnapshot();
            double lift = structure.maxHeight() - 4;
            for (ConductorAttachment wire : ConductorAttachmentPresets.twinTopWires(lift, 2.5)) {
                draft.addAttachment(wire);
            }
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.add_attachment"), 0, 0)) {
            pushDraftSnapshot();
            draft.addAttachment(new ConductorAttachment());
        }
    }

    private void renderAttachmentRow(ConductorAttachment attachment) {
        renderAttachmentRow(attachment, null);
    }

    private void renderAttachmentRow(ConductorAttachment attachment, TowerArm boundArm) {
        ImGui.text(PlotI18n.tr("plugin.powerline.design.attachment_row", attachment.getName()));
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.delete_layer"))) {
            pushDraftSnapshot();
            draft.removeAttachment(attachment.getId());
        }
        if (draft.hasTowerStructure()) {
            renderAttachmentArmBinding(attachment, boundArm);
        }

        float[] lateral = {(float) attachment.getLateralOffset()};
        PowerLineUiWidgets.sliderFloatStable(
            "attachment_lateral",
            "plugin.powerline.design.attachment_lateral",
            lateral,
            -8f,
            8f,
            "%.1f",
            this::pushDraftSnapshot,
            value -> attachment.setLateralOffset(value),
            null);
        float[] vertical = {(float) attachment.getVerticalOffset()};
        PowerLineUiWidgets.sliderFloatStable(
            "attachment_vertical",
            "plugin.powerline.design.attachment_vertical",
            vertical,
            1f,
            64f,
            "%.1f",
            this::pushDraftSnapshot,
            value -> attachment.setVerticalOffset(value),
            null);
        float[] longitudinal = {(float) attachment.getLongitudinalOffset()};
        PowerLineUiWidgets.sliderFloatStable(
            "attachment_longitudinal",
            "plugin.powerline.design.attachment_longitudinal",
            longitudinal,
            -4f,
            4f,
            "%.1f",
            this::pushDraftSnapshot,
            value -> attachment.setLongitudinalOffset(value),
            null);
        ImInt insulatorLength = new ImInt(attachment.getInsulatorLength());
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt(PlotI18n.tr("plugin.powerline.design.attachment_insulator"), insulatorLength)) {
            attachment.setInsulatorLength(insulatorLength.get());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }
    }

    private void renderAttachmentArmBinding(ConductorAttachment attachment, TowerArm boundArm) {
        if (boundArm != null) {
            ImGui.textColored(0xFF90CAF9, PlotI18n.tr(
                "plugin.powerline.design.attachment_bound_arm",
                boundArm.getId()));
            return;
        }
        List<TowerArm> arms = TowerArmAttachmentBinding.sortedArms(draft.getTowerStructure());
        if (arms.isEmpty()) {
            return;
        }
        int selected = 0;
        String currentArmId = attachment.getArmId();
        for (int i = 0; i < arms.size(); i++) {
            if (arms.get(i).getId().equals(currentArmId)) {
                selected = i + 1;
                break;
            }
        }
        String[] labels = new String[arms.size() + 1];
        labels[0] = PlotI18n.tr("plugin.powerline.design.attachment_arm_none");
        for (int i = 0; i < arms.size(); i++) {
            labels[i + 1] = PlotI18n.tr(
                "plugin.powerline.design.attachment_arm_option",
                i + 1,
                (int) Math.round(arms.get(i).getBaseHeight()));
        }
        ImInt armIndex = new ImInt(selected);
        ImGui.setNextItemWidth(140);
        if (ImGui.combo(PlotI18n.tr("plugin.powerline.design.attachment_arm_bind"), armIndex, labels)) {
            pushDraftSnapshot();
            if (armIndex.get() == 0) {
                attachment.setArmId(null);
            } else {
                TowerArm arm = arms.get(armIndex.get() - 1);
                TowerArmAttachmentBinding.bindToArm(arm, attachment);
            }
        }
    }

    private void renderLayerList() {
        pendingLayerActions.clear();
        ImGui.text(PlotI18n.tr("plugin.powerline.design.layers"));
        for (int i = 0; i < draft.getLayers().size(); i++) {
            PoleLayer layer = draft.getLayers().get(i);
            ImGui.pushID("layer_" + i);
            renderLayerRow(layer, i);
            ImGui.popID();
        }
        applyPendingLayerActions();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.add_layer"), 0, 0)) {
            pushDraftSnapshot();
            draft.getLayers().add(new PoleLayer(
                PoleLayer.Shape.COLUMN,
                1,
                MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL)));
        }
    }

    private void renderLayerRow(PoleLayer layer, int index) {
        String[] shapeLabels = {
            PlotI18n.tr("plugin.powerline.design.shape.column"),
            PlotI18n.tr("plugin.powerline.design.shape.crossarm"),
            PlotI18n.tr("plugin.powerline.design.shape.cap")
        };
        ImInt shapeIndex = new ImInt(layer.getShape().ordinal());
        ImGui.setNextItemWidth(90);
        if (ImGui.combo("##shape", shapeIndex, shapeLabels)) {
            pushDraftSnapshot();
            layer.setShape(PoleLayer.Shape.values()[shapeIndex.get()]);
        }
        ImGui.sameLine();

        ImInt height = new ImInt(layer.getHeight());
        ImGui.setNextItemWidth(60);
        if (ImGui.inputInt("##height", height)) {
            layer.setHeight(height.get());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }
        ImGui.sameLine();

        if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
            ImInt armLength = new ImInt(layer.getCrossarmLength());
            ImGui.setNextItemWidth(60);
            if (ImGui.inputInt("##arm", armLength)) {
                layer.setCrossarmLength(armLength.get());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
            ImGui.sameLine();
        }

        UIUtils.renderMaterialMixPicker(
            "layer_mat_" + index,
            "",
            layer.getMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL),
            layer::setMaterial,
            this::pushDraftSnapshot);
        ImGui.sameLine();

        if (index > 0 && ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.move_up"))) {
            pendingLayerActions.add(new LayerAction(LayerAction.Type.MOVE_UP, index));
        }
        ImGui.sameLine();
        if (index < draft.getLayers().size() - 1
                && ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.move_down"))) {
            pendingLayerActions.add(new LayerAction(LayerAction.Type.MOVE_DOWN, index));
        }
        ImGui.sameLine();
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.design.delete_layer"))) {
            pendingLayerActions.add(new LayerAction(LayerAction.Type.DELETE, index));
        }
    }

    private void applyPendingLayerActions() {
        if (pendingLayerActions.isEmpty()) {
            return;
        }
        pushDraftSnapshot();
        for (LayerAction action : pendingLayerActions) {
            switch (action.type()) {
                case MOVE_UP -> moveLayer(action.index(), -1);
                case MOVE_DOWN -> moveLayer(action.index(), 1);
                case DELETE -> draft.getLayers().remove(action.index());
            }
        }
    }

    private void moveLayer(int index, int offset) {
        int target = index + offset;
        if (target < 0 || target >= draft.getLayers().size()) {
            return;
        }
        PoleLayer current = draft.getLayers().remove(index);
        draft.getLayers().add(target, current);
    }

    private void renderSaveActions() {
        if (ImGui.inputText(PlotI18n.tr("plugin.powerline.design.name"), designNameBuffer)) {
            draft.setName(designNameBuffer.get());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.save"), 0, 0)) {
            saveDraft(false);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.save_as"), 0, 0)) {
            saveAsNameBuffer.set(draft.getName() + " Copy");
            ImGui.openPopup("##pole_design_save_as");
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 0, 0)) {
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

    private void saveDraft(boolean forceNewId) {
        if (forceNewId || PoleDesignCatalog.isBuiltinId(draft.getId())) {
            PoleDesign saved = new PoleDesign(draft.getName());
            saved.setLayers(draft.getLayers());
            saved.setAttachments(draft.getAttachments());
            saved.setTowerStructure(draft.getTowerStructure());
            saved.setEngineeringMetadata(draft.getEngineeringMetadata());
            ctx.actions().savePoleDesign(saved);
            ctx.state().setPoleDesignerEditingId(saved.getId());
            draft = saved.copy();
        } else {
            ctx.actions().savePoleDesign(draft);
        }
        designNameBuffer.set(draft.getName());
        ctx.state().getDesignDraftHistory().clear();
        captureOpenedBaseline();
    }

    private void renderPresetConfirmPopup() {
        if (PowerLineUiWidgets.beginDeferredPopupModal(
                "##pole_preset_confirm",
                presetConfirmPending,
                () -> presetConfirmPending = false)) {
            ImGui.text(PlotI18n.tr("plugin.powerline.design.preset_confirm"));
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                PoleDesign preset = PoleDesignCatalog.findBuiltin(pendingPresetId);
                if (preset != null) {
                    pushDraftSnapshot();
                    draft = preset.copy();
                    designNameBuffer.set(draft.getName());
                    ctx.state().setPoleDesignerEditingId("");
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderFamilyRolePickerPopup() {
        if (!PowerLineUiWidgets.beginDeferredPopupModal(
                "##pole_family_role_picker",
                familyRolePickerPending,
                () -> familyRolePickerPending = false)) {
            return;
        }
        TowerFamily family = new TowerFamilyResolver().find(pendingFamilyId);
        ImGui.text(PlotI18n.tr("plugin.powerline.design.family_pick_title"));
        if (family != null) {
            ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
                family.getName());
            ImGui.spacing();
            for (Map.Entry<TowerRole, String> entry : editableFamilyRoles(family).entrySet()) {
                String label = familyRoleLabel(entry.getKey());
                if (ImGui.button(label + "##family_role_" + entry.getKey().name(), 220, 0)) {
                    open(entry.getValue());
                    ImGui.closeCurrentPopup();
                }
            }
        }
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    /** 塔族可编辑角色 → designId（Regular / Corner / Dead-end / Terminal）。 */
    static Map<TowerRole, String> editableFamilyRoles(TowerFamily family) {
        Map<TowerRole, String> roles = new LinkedHashMap<>();
        if (family == null) {
            return roles;
        }
        for (TowerRole role : FAMILY_EDIT_ROLES) {
            String designId = family.getDesignId(role);
            if (designId == null || designId.isBlank()) {
                continue;
            }
            roles.put(role, designId);
        }
        return roles;
    }

    private static String familyRoleLabel(TowerRole role) {
        return switch (role) {
            case SUSPENSION -> PlotI18n.tr("plugin.powerline.design.family_role_regular");
            case ANGLE -> PlotI18n.tr("plugin.powerline.design.family_role_corner");
            case DEAD_END -> PlotI18n.tr("plugin.powerline.design.family_role_dead_end");
            case TERMINAL -> PlotI18n.tr("plugin.powerline.design.family_role_terminal");
            case SPECIAL -> PlotI18n.tr("plugin.powerline.pole_role_special");
        };
    }

    private static PoleDesign newBlankDesign() {
        PoleDesign design = new PoleDesign(PlotI18n.tr("plugin.powerline.design.new_name"));
        design.getLayers().add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL)));
        return design;
    }

    private record LayerAction(Type type, int index) {
        enum Type {
            MOVE_UP,
            MOVE_DOWN,
            DELETE
        }
    }
}
