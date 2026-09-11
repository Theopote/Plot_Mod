package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentBindingMode;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerDecorationCatalog;
import com.plot.plugin.powerline.design.structure.TowerDecorationKind;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.parametric.ConstraintIssue;
import com.plot.plugin.powerline.design.parametric.StructureDensity;
import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelopeResolver;
import com.plot.plugin.powerline.design.parametric.TowerLineBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerConstraintResult;
import com.plot.plugin.powerline.design.parametric.TowerConstraintSolver;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.ParameterRange;
import com.plot.plugin.powerline.design.parametric.TowerParametricHeightLimits;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfile;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
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
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.ui.dialog.DialogStyleManager;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 杆塔分层设计器独立窗口（居中弹出、可拖动、不参与 DockSpace 停靠）。 */
public final class PoleDesignerPanel {
    private static final float DESIGNER_WIDTH = 660f;
    private static final float DESIGNER_HEIGHT = 760f;
    private static final float DEFAULT_PREVIEW_COLUMN_WIDTH = 272f;
    private static final float MIN_PREVIEW_COLUMN_WIDTH = 200f;
    private static final float MIN_PARAMS_COLUMN_WIDTH = 280f;
    private static final float SPLITTER_WIDTH = 6f;
    private static final int DESIGNER_WINDOW_FLAGS =
        ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings;
    private static final int PREVIEW_COLUMN_FLAGS =
        ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

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
    private boolean focusOnNextRender;
    private float previewColumnWidth = DEFAULT_PREVIEW_COLUMN_WIDTH;
    private final ImBoolean showAdvancedStructure = new ImBoolean(false);

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
        focusOnNextRender = true;
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
                renderToolbar();
                float footerHeight = footerReservedHeight();
                float bodyHeight = Math.max(0f, ImGui.getContentRegionAvail().y - footerHeight);
                renderSplitBody(bodyHeight);
                renderFooter();
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

    private void renderToolbar() {
        renderDraftHistoryControls();
        ImGui.separator();
        renderPresetSelector();
        ImGui.separator();
    }

    private void renderSplitBody(float bodyHeight) {
        float totalWidth = ImGui.getContentRegionAvail().x;
        float maxPreviewWidth = Math.max(
            MIN_PREVIEW_COLUMN_WIDTH,
            totalWidth - MIN_PARAMS_COLUMN_WIDTH - SPLITTER_WIDTH);
        previewColumnWidth = Math.min(maxPreviewWidth, Math.max(MIN_PREVIEW_COLUMN_WIDTH, previewColumnWidth));

        if (ImGui.beginChild("##designer_preview_column", previewColumnWidth, bodyHeight, false, PREVIEW_COLUMN_FLAGS)) {
            PoleDesignPreviewRenderer.renderVerticalStack(
                draft,
                ImGui.getContentRegionAvail().x,
                ImGui.getContentRegionAvail().y);
        }
        ImGui.endChild();

        ImGui.sameLine(0, 0);
        renderColumnSplitter(bodyHeight, totalWidth);

        ImGui.sameLine(0, 0);
        float paramsWidth = Math.max(0f, totalWidth - previewColumnWidth - SPLITTER_WIDTH);
        if (ImGui.beginChild("##designer_params_column", paramsWidth, bodyHeight, false)) {
            renderStructureSection();
            ImGui.separator();
            renderLayerList();
            ImGui.separator();
            renderAttachmentList();
        }
        ImGui.endChild();
    }

    private void renderColumnSplitter(float height, float totalWidth) {
        ImGui.pushID("designer_column_splitter");
        ImGui.invisibleButton("##grab", SPLITTER_WIDTH, height);
        if (ImGui.isItemActive()) {
            previewColumnWidth += ImGui.getIO().getMouseDeltaX();
            float maxPreviewWidth = Math.max(
                MIN_PREVIEW_COLUMN_WIDTH,
                totalWidth - MIN_PARAMS_COLUMN_WIDTH - SPLITTER_WIDTH);
            previewColumnWidth = Math.min(maxPreviewWidth, Math.max(MIN_PREVIEW_COLUMN_WIDTH, previewColumnWidth));
        }
        if (ImGui.isItemHovered() || ImGui.isItemActive()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }

        ImVec2 min = ImGui.getItemRectMin();
        ImVec2 max = ImGui.getItemRectMax();
        float centerX = (min.x + max.x) * 0.5f;
        ImDrawList drawList = ImGui.getWindowDrawList();
        boolean active = ImGui.isItemActive() || ImGui.isItemHovered();
        int lineColor = active ? 0xFF90CAF9 : 0xFF606060;
        drawList.addLine(centerX, min.y, centerX, max.y, lineColor, active ? 2f : 1f);
        ImGui.popID();
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

        if (ImGui.button(saveLabel, buttonWidth, 0)) {
            saveDraft(false);
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

        renderParametricSection();

        if (!draft.hasTowerStructure()) {
            return;
        }

        if (draft.isParametricMode() && !showAdvancedStructure.get()) {
            renderParametricGeometrySummary();
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

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_stations"));
        for (int i = 0; i < structure.getStations().size(); i++) {
            TowerStation station = structure.getStations().get(i);
            ImGui.pushID("station_" + i);
            renderStationRow(station, structure);
            ImGui.popID();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.structure_add_station"), 0, 0)) {
            pushDraftSnapshot();
            double nextHeight = structure.maxHeight() + 8;
            structure.addStation(new TowerStation(null, nextHeight, 2, 2));
            structure.setBays(TowerStructurePresets.defaultBaysForStations(structure.getStations()));
        }

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_arms"));
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

    private void renderParametricSection() {
        if (!draft.hasTowerStructure() && !draft.isParametricMode()) {
            renderParametricEnableButtons(true);
            return;
        }

        if (!draft.isParametricMode()) {
            renderParametricEnableButtons(false);
            return;
        }

        TowerParameterProfile profile = TowerParametricEditor.findProfile(draft.getGeneratorConfig().profileId())
            .orElse(TowerParameterProfiles.classicDoubleArm());
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.parametric_section"));
        PowerLineUiWidgets.textColored(0xFF9E9E9E, profileLabel(profile.id()));
        TowerParameterSet parameters = draft.getGeneratorConfig().parameters();
        Optional<TowerLineBuildEnvelope> lineEnvelope = tryResolveLineEnvelope();
        TowerBuildEnvelope constraintEnvelope = lineEnvelope
            .map(TowerLineBuildEnvelope::constraintEnvelope)
            .orElse(TowerBuildEnvelopeResolver.tryFromClientPlayer().orElse(null));
        TowerParametricHeightLimits.EffectiveHeightRange heightRange = TowerParametricHeightLimits.heightRange(
            profile,
            parameters,
            constraintEnvelope);

        float[] height = {(float) parameters.height()};
        if (formRowSliderFloat(
                "plugin.powerline.design.parametric_height",
                "##param_height",
                height,
                (float) heightRange.min(),
                (float) heightRange.max(),
                "%.0f")) {
            applyParametricParameters(withHeight(parameters, height[0]));
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }
        if (heightRange.worldLimitedMax() != null) {
            if (lineEnvelope.isPresent()) {
                TowerLineBuildEnvelope envelope = lineEnvelope.get();
                PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
                    "plugin.powerline.design.parametric_height_line_limit",
                    envelope.limitingSiteIndex() + 1,
                    (int) Math.floor(heightRange.worldLimitedMax())));
            } else {
                PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
                    "plugin.powerline.design.parametric_height_world_limit",
                    (int) Math.floor(heightRange.worldLimitedMax())));
            }
        }

        float[] baseWidth = {(float) parameters.baseWidth()};
        if (formRowSliderFloat(
                "plugin.powerline.design.parametric_base_width",
                "##param_base_width",
                baseWidth,
                (float) profile.baseWidthRange().min(),
                (float) profile.baseWidthRange().max(),
                "%.0f")) {
            applyParametricParameters(withBaseWidth(parameters, baseWidth[0]));
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        float[] armSpan = {(float) parameters.armSpan()};
        if (formRowSliderFloat(
                "plugin.powerline.design.parametric_arm_span",
                "##param_arm_span",
                armSpan,
                (float) profile.armSpanRange().min(),
                (float) profile.armSpanRange().max(),
                "%.0f")) {
            applyParametricParameters(withArmSpan(parameters, armSpan[0]));
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        renderDensityButtons(parameters);
        renderAdvancedParametricControls(profile, parameters);
        renderParametricConstraintHints();

        ImGui.checkbox(
            PlotI18n.tr("plugin.powerline.design.parametric_advanced_structure"),
            showAdvancedStructure);
        if (draft.isParametricMode() && ImGui.button(PlotI18n.tr("plugin.powerline.design.parametric_convert_manual"), 0, 0)) {
            pushDraftSnapshot();
            TowerParametricEditor.convertToManual(draft);
            showAdvancedStructure.set(true);
        }
        ImGui.separator();
    }

    private void renderAdvancedParametricControls(TowerParameterProfile profile, TowerParameterSet parameters) {
        ImGui.setNextItemOpen(false, imgui.flag.ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.design.parametric_advanced_parameters"),
                imgui.flag.ImGuiTreeNodeFlags.None)) {
            return;
        }

        float[] depthScale = {(float) parameters.depthScale()};
        if (formRowSliderFloat(
                "plugin.powerline.design.parametric_depth_scale",
                "##param_depth_scale",
                depthScale,
                (float) profile.depthScaleRange().min(),
                (float) profile.depthScaleRange().max(),
                "%.2f")) {
            applyParametricParameters(withDepthScale(parameters, depthScale[0]));
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }
        PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr("plugin.powerline.design.parametric_depth_scale_hint"));

        if (profile.hasWaistControl()) {
            float[] waistRatio = {(float) parameters.waistRatio()};
            if (formRowSliderFloat(
                    "plugin.powerline.design.parametric_waist_ratio",
                    "##param_waist_ratio",
                    waistRatio,
                    (float) ParameterRange.WAIST_RATIO.min(),
                    (float) ParameterRange.WAIST_RATIO.max(),
                    "%.2f")) {
                applyParametricParameters(withWaistRatio(parameters, waistRatio[0]));
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
            PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr("plugin.powerline.design.parametric_waist_ratio_hint"));
        }
    }

    private void renderDensityButtons(TowerParameterSet parameters) {
        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.parametric_density"));
        for (StructureDensity density : StructureDensity.values()) {
            boolean selected = parameters.density() == density;
            if (selected) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0xFF455A64);
            }
            if (ImGui.button(densityLabel(density) + "##density_" + density.name(), 0, 0)) {
                pushDraftSnapshot();
                applyParametricParameters(withDensity(parameters, density));
            }
            if (selected) {
                ImGui.popStyleColor();
            }
            if (density != StructureDensity.HIGH) {
                ImGui.sameLine();
            }
        }
    }

    private static String densityLabel(StructureDensity density) {
        return switch (density) {
            case LOW -> PlotI18n.tr("plugin.powerline.design.parametric_density_low");
            case MEDIUM -> PlotI18n.tr("plugin.powerline.design.parametric_density_medium");
            case HIGH -> PlotI18n.tr("plugin.powerline.design.parametric_density_high");
        };
    }

    private void renderParametricEnableButtons(boolean fullSize) {
        record ParametricEnableAction(String labelKey, Runnable action) {}
        List<ParametricEnableAction> actions = List.of(
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_classic",
                () -> TowerParametricEditor.enableParametricClassic(draft, TowerParameterSet.classicDefaults())),
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_small_lattice",
                () -> TowerParametricEditor.enableParametricSmallLattice(draft, TowerParameterSet.smallLatticeDefaults())),
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_triple_arm",
                () -> TowerParametricEditor.enableParametricTripleArm(draft, TowerParameterSet.tripleArmDefaults())),
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_cup",
                () -> TowerParametricEditor.enableParametricCup(draft, TowerParameterSet.cupDefaults())),
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_heavy",
                () -> TowerParametricEditor.enableParametricHeavy(draft, TowerParameterSet.heavyDefaults())),
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_mega",
                () -> TowerParametricEditor.enableParametricMega(draft, TowerParameterSet.megaDefaults())),
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_portal",
                () -> TowerParametricEditor.enableParametricPortal(draft, TowerParameterSet.portalDefaults())),
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_drum",
                () -> TowerParametricEditor.enableParametricDrum(draft, TowerParameterSet.drumDefaults())),
            new ParametricEnableAction(
                "plugin.powerline.design.parametric_enable_uhv",
                () -> TowerParametricEditor.enableParametricUhv(draft, TowerParameterSet.uhvDefaults())));

        for (int i = 0; i < actions.size(); i++) {
            ParametricEnableAction action = actions.get(i);
            boolean clicked = fullSize
                ? ImGui.button(PlotI18n.tr(action.labelKey), 0, 0)
                : ImGui.smallButton(PlotI18n.tr(action.labelKey));
            if (clicked) {
                pushDraftSnapshot();
                action.action().run();
            }
            if (i < actions.size() - 1 && (i + 1) % 3 != 0) {
                ImGui.sameLine();
            }
        }
    }

    private static String profileLabel(String profileId) {
        if (TowerParameterProfiles.SMALL_LATTICE_ID.equals(profileId)) {
            return PlotI18n.tr("plugin.powerline.design.parametric_profile_small_lattice");
        }
        if (TowerParameterProfiles.TRIPLE_ARM_ID.equals(profileId)) {
            return PlotI18n.tr("plugin.powerline.design.parametric_profile_triple_arm");
        }
        if (TowerParameterProfiles.CUP_ID.equals(profileId)) {
            return PlotI18n.tr("plugin.powerline.design.parametric_profile_cup");
        }
        if (TowerParameterProfiles.HEAVY_ID.equals(profileId)) {
            return PlotI18n.tr("plugin.powerline.design.parametric_profile_heavy");
        }
        if (TowerParameterProfiles.MEGA_ID.equals(profileId)) {
            return PlotI18n.tr("plugin.powerline.design.parametric_profile_mega");
        }
        if (TowerParameterProfiles.PORTAL_ID.equals(profileId)) {
            return PlotI18n.tr("plugin.powerline.design.parametric_profile_portal");
        }
        if (TowerParameterProfiles.DRUM_ID.equals(profileId)) {
            return PlotI18n.tr("plugin.powerline.design.parametric_profile_drum");
        }
        if (TowerParameterProfiles.UHV_ID.equals(profileId)) {
            return PlotI18n.tr("plugin.powerline.design.parametric_profile_uhv");
        }
        return PlotI18n.tr("plugin.powerline.design.parametric_profile_classic");
    }

    private void renderParametricConstraintHints() {
        Optional<TowerLineBuildEnvelope> lineEnvelope = tryResolveLineEnvelope();
        TowerBuildEnvelope envelope = lineEnvelope
            .map(TowerLineBuildEnvelope::constraintEnvelope)
            .orElse(TowerBuildEnvelopeResolver.tryFromClientPlayer().orElse(null));
        TowerConstraintResult advisory = TowerParametricEditor.preview(
            draft,
            draft.getGeneratorConfig().parameters(),
            envelope);
        if (advisory == null) {
            return;
        }
        for (ConstraintIssue issue : advisory.issues()) {
            if (TowerConstraintSolver.CODE_WORLD_HEIGHT_EXCEEDED.equals(issue.code())) {
                if (lineEnvelope.isPresent()) {
                    PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.parametric_world_height_line_warning"));
                } else {
                    PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.parametric_world_height_warning"));
                }
            }
        }
        for (var adjustment : advisory.adjustments()) {
            if (adjustment.requestedValue() != adjustment.resolvedValue()) {
                PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
                    "plugin.powerline.design.parametric_clamped",
                    adjustment.parameter(),
                    adjustment.resolvedValue()));
            }
        }
    }

    private void renderParametricGeometrySummary() {
        TowerStructureDesign structure = draft.getTowerStructure();
        TowerParameterSet parameters = draft.getGeneratorConfig().parameters();
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.design.parametric_summary",
            (int) Math.round(parameters.height()),
            (int) Math.round(parameters.baseWidth()),
            (int) Math.round(parameters.armSpan()),
            densityLabel(parameters.density())));
        PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
            "plugin.powerline.design.parametric_station_count",
            structure.getStations().size(),
            structure.getArms().size()));
    }

    private void applyParametricParameters(TowerParameterSet parameters) {
        draft.setGeneratorConfig(draft.getGeneratorConfig().withParameters(parameters));
        TowerParametricEditor.recompile(draft, resolveParametricConstraintEnvelope());
    }

    private Optional<TowerLineBuildEnvelope> tryResolveLineEnvelope() {
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            return Optional.empty();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return Optional.empty();
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(client.world, ctx.coordinates());
        return TowerBuildEnvelopeResolver.tryFromFootprint(line, terrain, ctx.coordinates());
    }

    private TowerBuildEnvelope resolveParametricConstraintEnvelope() {
        return tryResolveLineEnvelope()
            .map(TowerLineBuildEnvelope::constraintEnvelope)
            .orElse(TowerBuildEnvelopeResolver.tryFromClientPlayer().orElse(null));
    }

    private static TowerParameterSet withHeight(TowerParameterSet source, float height) {
        return new TowerParameterSet(
            height, source.baseWidth(), source.armSpan(), source.depthScale(), source.waistRatio(), source.density());
    }

    private static TowerParameterSet withBaseWidth(TowerParameterSet source, float baseWidth) {
        return new TowerParameterSet(
            source.height(), baseWidth, source.armSpan(), source.depthScale(), source.waistRatio(), source.density());
    }

    private static TowerParameterSet withArmSpan(TowerParameterSet source, float armSpan) {
        return new TowerParameterSet(
            source.height(), source.baseWidth(), armSpan, source.depthScale(), source.waistRatio(), source.density());
    }

    private static TowerParameterSet withDepthScale(TowerParameterSet source, float depthScale) {
        return new TowerParameterSet(
            source.height(), source.baseWidth(), source.armSpan(), depthScale, source.waistRatio(), source.density());
    }

    private static TowerParameterSet withWaistRatio(TowerParameterSet source, float waistRatio) {
        return new TowerParameterSet(
            source.height(), source.baseWidth(), source.armSpan(), source.depthScale(), waistRatio, source.density());
    }

    private static TowerParameterSet withDensity(TowerParameterSet source, StructureDensity density) {
        return new TowerParameterSet(
            source.height(), source.baseWidth(), source.armSpan(), source.depthScale(), source.waistRatio(), density);
    }

    private void renderDecorationSection(TowerStructureDesign structure) {
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.structure_decorations"));
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
        if (!DialogLayoutHelper.beginForm("##deco_form")) {
            return;
        }
        TowerDecorationKind[] kinds = TowerDecorationKind.values();
        String[] kindLabels = new String[kinds.length];
        int selectedKind = 0;
        for (int i = 0; i < kinds.length; i++) {
            kindLabels[i] = PlotI18n.tr(kinds[i].labelKey());
            if (decoration.getKind() == kinds[i]) {
                selectedKind = i;
            }
        }
        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.decoration_kind"));
        ImInt kindIndex = new ImInt(selectedKind);
        if (ImGui.combo("##deco_kind", kindIndex, kindLabels)) {
            pushDraftSnapshot();
            decoration.setKind(kinds[kindIndex.get()]);
        }

        float[] baseHeight = {(float) decoration.getBaseHeight()};
        if (formRowSliderFloat(
                "plugin.powerline.design.structure_arm_height",
                "##deco_height",
                baseHeight,
                0f,
                256f,
                "%.1f")) {
            decoration.setBaseHeight(baseHeight[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        if (decoration.getKind() == TowerDecorationKind.ANTENNA
                || decoration.getKind() == TowerDecorationKind.PLATFORM) {
            float[] size = {(float) decoration.getSize()};
            String sizeKey = decoration.getKind() == TowerDecorationKind.PLATFORM
                ? "plugin.powerline.design.decoration_platform_radius"
                : "plugin.powerline.design.decoration_antenna_height";
            if (formRowSliderFloat(sizeKey, "##deco_size", size, 0.5f, 32f, "%.1f")) {
                decoration.setSize(size[0]);
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.decoration_snap_top") + "##snap", 0, 0)) {
            pushDraftSnapshot();
            decoration.setBaseHeight(structureTopSnapHeight(decoration));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot();
            draft.getTowerStructure().removeDecoration(decoration.getId());
        }
        DialogLayoutHelper.endForm();
        DialogLayoutHelper.subsectionGap();
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
        if (!DialogLayoutHelper.beginForm("##arm_form")) {
            return;
        }
        float[] baseHeight = {(float) arm.getBaseHeight()};
        if (formRowSliderFloat(
                "plugin.powerline.design.structure_arm_height",
                "##arm_height",
                baseHeight,
                0f,
                256f,
                "%.1f")) {
            arm.setBaseHeight(baseHeight[0]);
            TowerArmAttachmentBinding.syncBoundVerticalOffsets(arm, draft.getAttachments());
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        float[] reach = {(float) arm.getLateralReach()};
        if (formRowSliderFloat(
                "plugin.powerline.design.structure_arm_reach",
                "##arm_reach",
                reach,
                1f,
                32f,
                "%.1f")) {
            arm.setLateralReach(reach[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        float[] verticalDrop = {(float) arm.getVerticalDrop()};
        if (formRowSliderFloat(
                "plugin.powerline.design.structure_arm_drop",
                "##arm_drop",
                verticalDrop,
                0f,
                32f,
                "%.1f")) {
            arm.setVerticalDrop(verticalDrop[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.arm_sync_height") + "##sync_h", 0, 0)) {
            pushDraftSnapshot();
            TowerArmAttachmentBinding.syncBoundVerticalOffsets(arm, draft.getAttachments());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.arm_sync_spread") + "##sync_s", 0, 0)) {
            pushDraftSnapshot();
            TowerArmAttachmentBinding.syncBoundLateralSpread(arm, draft.getAttachments());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot();
            TowerArmAttachmentBinding.clearArmBindings(draft.getAttachments(), arm.getId());
            structure.removeArm(arm.getId());
        }
        DialogLayoutHelper.endForm();
    }

    private void renderStationRow(TowerStation station, TowerStructureDesign structure) {
        if (!DialogLayoutHelper.beginForm("##station_form")) {
            return;
        }
        float[] height = {(float) station.getHeight()};
        if (formRowSliderFloat("plugin.powerline.design.station_height", "##h", height, 1f, 256f, "%.1f")) {
            station.setHeight(height[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        float[] width = {(float) station.getHalfWidth()};
        if (formRowSliderFloat("plugin.powerline.design.station_width", "##w", width, 0.5f, 16f, "%.1f")) {
            station.setHalfWidth(width[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        float[] depth = {(float) station.getHalfDepth()};
        if (formRowSliderFloat("plugin.powerline.design.station_depth", "##d", depth, 0.5f, 16f, "%.1f")) {
            station.setHalfDepth(depth[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot();
            structure.removeStation(station.getId());
        }
        DialogLayoutHelper.endForm();
        DialogLayoutHelper.subsectionGap();
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
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.attachments"));
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
                for (ConductorAttachment deckAttachment : deck) {
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
                for (ConductorAttachment freeAttachment : unassigned) {
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
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.attachment_row", attachment.getName()));
        if (!DialogLayoutHelper.beginForm("##attachment_form")) {
            return;
        }
        if (draft.hasTowerStructure()) {
            renderAttachmentArmBindingRow(attachment, boundArm);
        }

        if (attachment.isBound()) {
            float[] normalized = {(float) attachment.getNormalizedPosition()};
            if (formRowSliderFloat(
                    "plugin.powerline.design.attachment_normalized_position",
                    "normalized",
                    normalized,
                    -1f,
                    1f,
                    "%.2f")) {
                attachment.setNormalizedPosition(normalized[0]);
                TowerArmAttachmentBinding.refreshBoundCache(attachment, draft.getTowerStructure());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }

            float[] anchor = {(float) attachment.getVerticalAnchorOffset()};
            if (formRowSliderFloat(
                    "plugin.powerline.design.attachment_vertical_anchor",
                    "anchor",
                    anchor,
                    -8f,
                    8f,
                    "%.1f")) {
                attachment.setVerticalAnchorOffset(anchor[0]);
                TowerArmAttachmentBinding.refreshBoundCache(attachment, draft.getTowerStructure());
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }

            TowerArmAttachmentBinding.ResolvedLocalOffsets resolved =
                TowerArmAttachmentBinding.resolveLocalOffsets(attachment, draft.getTowerStructure());
            PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
                "plugin.powerline.design.attachment_resolved_offsets",
                resolved.lateral(),
                resolved.vertical()));
        } else {
            float[] lateral = {(float) attachment.getLateralOffset()};
            if (formRowSliderFloat(
                    "plugin.powerline.design.attachment_lateral",
                    "lateral",
                    lateral,
                    -8f,
                    8f,
                    "%.1f")) {
                attachment.setLateralOffset(lateral[0]);
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }

            float[] vertical = {(float) attachment.getVerticalOffset()};
            if (formRowSliderFloat(
                    "plugin.powerline.design.attachment_vertical",
                    "vertical",
                    vertical,
                    1f,
                    64f,
                    "%.1f")) {
                attachment.setVerticalOffset(vertical[0]);
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
        }

        float[] longitudinal = {(float) attachment.getLongitudinalOffset()};
        if (formRowSliderFloat(
                "plugin.powerline.design.attachment_longitudinal",
                "longitudinal",
                longitudinal,
                -4f,
                4f,
                "%.1f")) {
            attachment.setLongitudinalOffset(longitudinal[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        int[] insulatorLength = {attachment.getInsulatorLength()};
        if (formRowSliderInt(
                "plugin.powerline.design.attachment_insulator",
                "##insulator",
                insulatorLength,
                0,
                16)) {
            attachment.setInsulatorLength(insulatorLength[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        DialogLayoutHelper.formRowLabel(" ");
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete", 0, 0)) {
            pushDraftSnapshot();
            draft.removeAttachment(attachment.getId());
        }
        DialogLayoutHelper.endForm();
        DialogLayoutHelper.subsectionGap();
    }

    private void renderAttachmentArmBindingRow(ConductorAttachment attachment, TowerArm boundArm) {
        if (boundArm != null) {
            DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.attachment_arm_bind"));
            PowerLineUiWidgets.textColored(0xFF90CAF9, PlotI18n.tr(
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
        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.attachment_arm_bind"));
        ImInt armIndex = new ImInt(selected);
        if (ImGui.combo("##arm_bind", armIndex, labels)) {
            pushDraftSnapshot();
            if (armIndex.get() == 0) {
                attachment.setArmId(null);
                attachment.setBindingMode(AttachmentBindingMode.FREE);
            } else {
                TowerArm arm = arms.get(armIndex.get() - 1);
                TowerArmAttachmentBinding.bindToArm(arm, attachment);
            }
        }
    }

    private void renderLayerList() {
        pendingLayerActions.clear();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.layers"));
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
        if (index > 0) {
            ImGui.separator();
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.layer_index", index + 1));
        if (!DialogLayoutHelper.beginForm("##layer_form")) {
            return;
        }

        String[] shapeLabels = {
            PlotI18n.tr("plugin.powerline.design.shape.column"),
            PlotI18n.tr("plugin.powerline.design.shape.crossarm"),
            PlotI18n.tr("plugin.powerline.design.shape.cap")
        };
        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.layer_shape"));
        ImInt shapeIndex = new ImInt(layer.getShape().ordinal());
        if (ImGui.combo("##shape", shapeIndex, shapeLabels)) {
            pushDraftSnapshot();
            layer.setShape(PoleLayer.Shape.values()[shapeIndex.get()]);
        }

        int[] height = {layer.getHeight()};
        if (formRowSliderInt("plugin.powerline.design.layer_height", "##height", height, 1, 64)) {
            layer.setHeight(height[0]);
        }
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot();
        }

        if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
            int[] span = {layer.getCrossarmLength()};
            if (formRowSliderInt(
                    "plugin.powerline.design.layer_crossarm_span",
                    "##span",
                    span,
                    1,
                    32)) {
                layer.setCrossarmLength(span[0]);
            }
            if (ImGui.isItemActivated()) {
                pushDraftSnapshot();
            }
        }

        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.layer_material"));
        UIUtils.renderMaterialMixPickerControl(
            "pick",
            layer.getMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL),
            layer::setMaterial,
            this::pushDraftSnapshot);

        MaterialMix mix = layer.getMaterial();
        if (mix != null
                && mix.getAccentMaterial() != null
                && !mix.getAccentMaterial().isBlank()) {
            DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.material.accent_ratio"));
            UIUtils.renderAccentRatioSliderControl(mix, layer::setMaterial, "accent", this::pushDraftSnapshot);
        }

        DialogLayoutHelper.endForm();
        renderLayerActionButtons(index);
        DialogLayoutHelper.subsectionGap();
    }

    private void renderLayerActionButtons(int index) {
        float contentWidth = DialogStyleManager.getContentWidth();
        float buttonWidth = DialogStyleManager.getStandardButtonWidth(
            contentWidth,
            3,
            PlotI18n.tr("plugin.powerline.design.move_up"),
            PlotI18n.tr("plugin.powerline.design.move_down"),
            PlotI18n.tr("plugin.powerline.design.delete_layer"));
        if (index > 0) {
            if (ImGui.button(
                    PlotI18n.tr("plugin.powerline.design.move_up") + "##up",
                    buttonWidth,
                    0)) {
                pendingLayerActions.add(new LayerAction(LayerAction.Type.MOVE_UP, index));
            }
            ImGui.sameLine();
        }
        if (index < draft.getLayers().size() - 1) {
            if (ImGui.button(
                    PlotI18n.tr("plugin.powerline.design.move_down") + "##down",
                    buttonWidth,
                    0)) {
                pendingLayerActions.add(new LayerAction(LayerAction.Type.MOVE_DOWN, index));
            }
            ImGui.sameLine();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.powerline.design.delete_layer") + "##delete",
                buttonWidth,
                0)) {
            pendingLayerActions.add(new LayerAction(LayerAction.Type.DELETE, index));
        }
    }

    private boolean formRowSliderInt(
            String labelKey,
            String fieldId,
            int[] value,
            int min,
            int max) {
        DialogLayoutHelper.formRowLabel(PlotI18n.tr(labelKey));
        return ImGui.sliderInt(fieldId, value, min, max);
    }

    private boolean formRowSliderFloat(
            String labelKey,
            String fieldId,
            float[] value,
            float min,
            float max,
            String format) {
        DialogLayoutHelper.formRowLabel(PlotI18n.tr(labelKey));
        return ImGui.sliderFloat(fieldId, value, min, max, format);
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
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.preset_confirm"));
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
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.family_pick_title"));
        if (family != null) {
            PowerLineUiWidgets.textColored(
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
