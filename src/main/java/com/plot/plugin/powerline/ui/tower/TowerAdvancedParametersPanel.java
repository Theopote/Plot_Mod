package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.ParameterRange;
import com.plot.plugin.powerline.design.parametric.TowerArmRole;
import com.plot.plugin.powerline.design.parametric.TowerArmTemplate;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfile;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.ui.PowerLineUiWidgets;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** Advanced parametric controls, expert mode toggle, and manual conversion. */
public final class TowerAdvancedParametersPanel {

    public void render(TowerDesignerContext context) {
        PoleDesign draft = context.draft();
        if (draft.isManualLegacyMode()) {
            renderManualLegacyHeader(context);
            return;
        }
        if (!draft.hasTowerStructure() || !draft.isParametricMode()) {
            return;
        }

        renderAdvancedParametricControls(context);
        renderExpertModeControls(context);
        renderConvertManualPopup(context);
    }

    private void renderManualLegacyHeader(TowerDesignerContext context) {
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.parametric_restore"), 0, 0)) {
            context.pushDraftSnapshot().run();
            if (context.session().restoreParametric(context.draft())) {
                context.uiState().showAdvancedStructure.set(false);
            }
        }
        ImGui.separator();
    }

    private void renderAdvancedParametricControls(TowerDesignerContext context) {
        PoleDesign draft = context.draft();
        TowerParameterProfile profile = TowerParametricEditor.findProfile(draft.getGeneratorConfig().profileId())
            .orElse(TowerParameterProfiles.classicDoubleArm());
        TowerParameterSet parameters = draft.getGeneratorConfig().parameters();

        ImGui.setNextItemOpen(false, imgui.flag.ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.design.parametric_advanced_parameters"),
                imgui.flag.ImGuiTreeNodeFlags.None)) {
            return;
        }

        if (!DialogLayoutHelper.beginForm("##tower_advanced_form")) {
            return;
        }
        float[] depthScale = {(float) parameters.depthScale()};
        TowerDesignerWidgets.formRowSliderTransaction(
            context.pushDraftSnapshot(),
            "plugin.powerline.design.parametric_depth_scale",
            "##param_depth_scale",
            depthScale,
            (float) profile.depthScaleRange().min(),
            (float) profile.depthScaleRange().max(),
            "%.2f",
            newDepth -> context.session().applyParametricChange(
                draft,
                source -> withDepthScale(source, newDepth)));
        DialogLayoutHelper.formRowHelp(PlotI18n.tr("plugin.powerline.design.parametric_depth_scale_hint"));

        if (profile.hasWaistControl()) {
            float[] waistRatio = {(float) parameters.waistRatio()};
            TowerDesignerWidgets.formRowSliderTransaction(
                context.pushDraftSnapshot(),
                "plugin.powerline.design.parametric_waist_ratio",
                "##param_waist_ratio",
                waistRatio,
                (float) ParameterRange.WAIST_RATIO.min(),
                (float) ParameterRange.WAIST_RATIO.max(),
                "%.2f",
                newWaist -> context.session().applyParametricChange(
                    draft,
                    source -> withWaistRatio(source, newWaist)));
            DialogLayoutHelper.formRowHelp(PlotI18n.tr("plugin.powerline.design.parametric_waist_ratio_hint"));
        }
        DialogLayoutHelper.endForm();

        renderArmLevelControls(context, profile, parameters);
    }

    private void renderArmLevelControls(
            TowerDesignerContext context,
            TowerParameterProfile profile,
            TowerParameterSet parameters) {
        List<TowerArmTemplate> armTemplates = profile.armTemplates();
        if (armTemplates.isEmpty()) {
            return;
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.parametric_arm_levels_section"));
        if (!DialogLayoutHelper.beginForm("##tower_arm_levels_form")) {
            return;
        }
        double towerHeight = parameters.height();
        for (int i = 0; i < armTemplates.size(); i++) {
            TowerArmTemplate armTemplate = armTemplates.get(i);
            float[] armHeight = {(float) TowerArmLevelUiMath.displayArmHeight(profile, parameters, armTemplate, i)};
            int armIndex = i;
            TowerDesignerWidgets.formRowSliderTransaction(
                context.pushDraftSnapshot(),
                armLevelLabelKey(armTemplate.role()),
                "##param_arm_level_" + armTemplate.id(),
                armHeight,
                TowerArmLevelUiMath.sliderMin(towerHeight, armTemplate),
                TowerArmLevelUiMath.sliderMax(towerHeight, armTemplate),
                "%.0f",
                requestedHeight -> context.session().applyParametricChange(
                    context.draft(),
                    source -> source.withArmLevelScale(
                        profile,
                        armIndex,
                        TowerArmLevelUiMath.scaleFromArmHeight(requestedHeight, source.height(), armTemplate))));
        }
        DialogLayoutHelper.formRowHelp(PlotI18n.tr("plugin.powerline.design.parametric_arm_levels_hint"));
        DialogLayoutHelper.endForm();
    }

    private void renderExpertModeControls(TowerDesignerContext context) {
        PoleDesign draft = context.draft();
        ImGui.checkbox(
            PlotI18n.tr("plugin.powerline.design.parametric_advanced_structure"),
            context.uiState().showAdvancedStructure);
        if (ImGui.button(PlotI18n.tr("plugin.powerline.design.parametric_convert_manual"), 0, 0)) {
            context.uiState().convertManualConfirmPending = true;
            ImGui.openPopup("##tower_convert_manual_confirm");
        }
    }

    private void renderConvertManualPopup(TowerDesignerContext context) {
        if (!PowerLineUiWidgets.beginDeferredPopupModal(
                "##tower_convert_manual_confirm",
                context.uiState().convertManualConfirmPending,
                () -> context.uiState().convertManualConfirmPending = false)) {
            return;
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.parametric_convert_manual_confirm_body"));
        if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
            context.pushDraftSnapshot().run();
            context.session().onConvertToManual(context.draft());
            context.uiState().showAdvancedStructure.set(true);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private static String armLevelLabelKey(TowerArmRole role) {
        return switch (role) {
            case LOWER -> "plugin.powerline.design.parametric_arm_level_lower";
            case MIDDLE -> "plugin.powerline.design.parametric_arm_level_middle";
            case UPPER -> "plugin.powerline.design.parametric_arm_level_upper";
            case MAIN -> "plugin.powerline.design.parametric_arm_level_main";
            case CUP -> "plugin.powerline.design.parametric_arm_level_cup";
            case AUXILIARY -> "plugin.powerline.design.parametric_arm_level_auxiliary";
        };
    }

    private static TowerParameterSet withDepthScale(TowerParameterSet source, float depthScale) {
        return new TowerParameterSet(
            source.height(),
            source.baseWidth(),
            source.armSpan(),
            depthScale,
            source.waistRatio(),
            source.armLevelScales(),
            source.density());
    }

    private static TowerParameterSet withWaistRatio(TowerParameterSet source, float waistRatio) {
        return new TowerParameterSet(
            source.height(),
            source.baseWidth(),
            source.armSpan(),
            source.depthScale(),
            waistRatio,
            source.armLevelScales(),
            source.density());
    }
}
