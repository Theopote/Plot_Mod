package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.ConstraintAdjustment;
import com.plot.plugin.powerline.design.parametric.StructureDensity;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerLineBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParametricHeightLimits;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfile;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.ui.PowerLineUiWidgets;
import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.List;
import java.util.Optional;

/** Default parametric controls: profile, height, base width, arm span, density. */
public final class TowerBasicParametersPanel {

    public void render(TowerDesignerContext context) {
        PoleDesign draft = context.draft();
        if (draft.isManualLegacyMode()) {
            return;
        }

        if (!draft.hasTowerStructure() && !draft.isParametricMode()) {
            renderProfileForm(context, true);
            return;
        }

        if (!draft.isParametricMode()) {
            renderProfileForm(context, true);
            return;
        }

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.tower_basic_section"));
        renderProfileForm(context, false);
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.tower_basic_dimensions"));
        renderDimensionsForm(context);
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.tower_structure_section"));
        renderDensityForm(context);
    }

    private void renderProfileForm(TowerDesignerContext context, boolean enableOnChange) {
        if (!DialogLayoutHelper.beginForm("##tower_profile_form")) {
            return;
        }
        renderProfileCombo(context, enableOnChange);
        DialogLayoutHelper.endForm();
    }

    private void renderDimensionsForm(TowerDesignerContext context) {
        if (!DialogLayoutHelper.beginForm("##tower_basic_dims_form")) {
            return;
        }
        renderBasicSliders(context);
        DialogLayoutHelper.endForm();
    }

    private void renderDensityForm(TowerDesignerContext context) {
        if (!DialogLayoutHelper.beginForm("##tower_density_form")) {
            return;
        }
        renderDensityButtons(context);
        DialogLayoutHelper.endForm();
    }

    private void renderProfileCombo(TowerDesignerContext context, boolean enableOnChange) {
        PoleDesign draft = context.draft();
        List<TowerProfileUiCatalog.ProfileOption> options = TowerProfileUiCatalog.all();
        String[] labels = options.stream()
            .map(option -> displayLabel(option.labelKey()))
            .toArray(String[]::new);

        String currentProfileId = draft.getGeneratorConfig() != null
            ? draft.getGeneratorConfig().profileId()
            : TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID;
        int selectedIndex = TowerProfileUiCatalog.indexOf(currentProfileId);
        selectedIndex = Math.min(Math.max(0, selectedIndex), labels.length - 1);

        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.tower_type_label"));
        ImInt profileIndex = new ImInt(selectedIndex);
        if (ImGui.combo("##tower_profile", profileIndex, labels)) {
            context.pushDraftSnapshot().run();
            TowerProfileUiCatalog.ProfileOption selected = options.get(profileIndex.get());
            if (enableOnChange || !draft.isParametricMode()) {
                context.session().enableProfile(draft, selected.id());
            } else if (!context.session().isSameProfile(draft, selected.id())) {
                context.session().switchProfile(draft, selected.id());
            }
        }
    }

    private void renderBasicSliders(TowerDesignerContext context) {
        PoleDesign draft = context.draft();
        TowerParameterProfile profile = TowerParametricEditor.findProfile(draft.getGeneratorConfig().profileId())
            .orElse(TowerParameterProfiles.classicDoubleArm());
        TowerParameterSet parameters = draft.getGeneratorConfig().parameters();
        Optional<TowerLineBuildEnvelope> lineEnvelope = context.session().tryResolveLineEnvelope();
        TowerBuildEnvelope constraintEnvelope = context.session().resolveConstraintEnvelope();
        TowerParametricHeightLimits.EffectiveHeightRange heightRange = TowerParametricHeightLimits.heightRange(
            profile,
            parameters,
            constraintEnvelope);

        float[] height = {(float) parameters.height()};
        TowerDesignerWidgets.formRowSliderTransaction(
            context.pushDraftSnapshot(),
            "plugin.powerline.design.parametric_height",
            "##param_height",
            height,
            (float) heightRange.min(),
            (float) heightRange.max(),
            "%.0f",
            newHeight -> context.session().applyParametricChange(draft, source -> withHeight(source, newHeight)));
        renderHeightLimitHint(heightRange, lineEnvelope);
        renderClampHint(context, "height");

        float[] baseWidth = {(float) parameters.baseWidth()};
        TowerDesignerWidgets.formRowSliderTransaction(
            context.pushDraftSnapshot(),
            "plugin.powerline.design.parametric_base_width",
            "##param_base_width",
            baseWidth,
            (float) profile.baseWidthRange().min(),
            (float) profile.baseWidthRange().max(),
            "%.0f",
            newWidth -> context.session().applyParametricChange(draft, source -> withBaseWidth(source, newWidth)));
        renderClampHint(context, "baseWidth");

        float[] armSpan = {(float) parameters.armSpan()};
        TowerDesignerWidgets.formRowSliderTransaction(
            context.pushDraftSnapshot(),
            "plugin.powerline.design.parametric_arm_span",
            "##param_arm_span",
            armSpan,
            (float) profile.armSpanRange().min(),
            (float) profile.armSpanRange().max(),
            "%.0f",
            newSpan -> context.session().applyParametricChange(draft, source -> withArmSpan(source, newSpan)));
        renderClampHint(context, "armSpan");
    }

    private void renderHeightLimitHint(
            TowerParametricHeightLimits.EffectiveHeightRange heightRange,
            Optional<TowerLineBuildEnvelope> lineEnvelope) {
        if (heightRange.worldLimitedMax() == null) {
            return;
        }
        if (lineEnvelope.isPresent()) {
            TowerLineBuildEnvelope envelope = lineEnvelope.get();
            DialogLayoutHelper.formRowHelp(PlotI18n.tr(
                "plugin.powerline.design.parametric_height_line_limit",
                envelope.limitingSiteIndex() + 1,
                (int) Math.floor(heightRange.worldLimitedMax())));
        } else {
            DialogLayoutHelper.formRowHelp(PlotI18n.tr(
                "plugin.powerline.design.parametric_height_world_limit",
                (int) Math.floor(heightRange.worldLimitedMax())));
        }
    }

    private void renderClampHint(TowerDesignerContext context, String parameterName) {
        if (context.session().lastConstraintResult() == null) {
            return;
        }
        for (ConstraintAdjustment adjustment : context.session().lastConstraintResult().adjustments()) {
            if (adjustment.parameter().equals(parameterName)
                    && adjustment.requestedValue() != adjustment.resolvedValue()) {
                DialogLayoutHelper.formRowHelp(PlotI18n.tr(
                    "plugin.powerline.design.parametric_clamped",
                    friendlyParameterLabel(parameterName),
                    adjustment.resolvedValue()));
            }
        }
    }

    private void renderDensityButtons(TowerDesignerContext context) {
        PoleDesign draft = context.draft();
        TowerParameterSet parameters = draft.getGeneratorConfig().parameters();
        DialogLayoutHelper.formRowLabel(PlotI18n.tr("plugin.powerline.design.parametric_density"));
        for (StructureDensity density : StructureDensity.values()) {
            boolean selected = parameters.density() == density;
            if (selected) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0xFF455A64);
            }
            if (ImGui.button(densityLabel(density) + "##density_" + density.name(), 0, 0)) {
                context.pushDraftSnapshot().run();
                context.session().applyParametricChange(draft, source -> withDensity(source, density));
            }
            if (selected) {
                ImGui.popStyleColor();
            }
            if (density != StructureDensity.HIGH) {
                ImGui.sameLine();
            }
        }
    }

    private static String displayLabel(String labelKey) {
        String translated = PlotI18n.tr(labelKey);
        int colon = translated.indexOf('：');
        if (colon < 0) {
            colon = translated.indexOf(':');
        }
        if (colon >= 0 && colon + 1 < translated.length()) {
            return translated.substring(colon + 1).trim();
        }
        return translated;
    }

    private static String friendlyParameterLabel(String parameterName) {
        return switch (parameterName) {
            case "height" -> PlotI18n.tr("plugin.powerline.design.parametric_height");
            case "baseWidth" -> PlotI18n.tr("plugin.powerline.design.parametric_base_width");
            case "armSpan" -> PlotI18n.tr("plugin.powerline.design.parametric_arm_span");
            default -> parameterName;
        };
    }

    private static String densityLabel(StructureDensity density) {
        return switch (density) {
            case LOW -> PlotI18n.tr("plugin.powerline.design.parametric_density_low");
            case MEDIUM -> PlotI18n.tr("plugin.powerline.design.parametric_density_medium");
            case HIGH -> PlotI18n.tr("plugin.powerline.design.parametric_density_high");
        };
    }

    private static TowerParameterSet withHeight(TowerParameterSet source, float height) {
        return new TowerParameterSet(
            height,
            source.baseWidth(),
            source.armSpan(),
            source.depthScale(),
            source.waistRatio(),
            source.armLevelScales(),
            source.density());
    }

    private static TowerParameterSet withBaseWidth(TowerParameterSet source, float baseWidth) {
        return new TowerParameterSet(
            source.height(),
            baseWidth,
            source.armSpan(),
            source.depthScale(),
            source.waistRatio(),
            source.armLevelScales(),
            source.density());
    }

    private static TowerParameterSet withArmSpan(TowerParameterSet source, float armSpan) {
        return new TowerParameterSet(
            source.height(),
            source.baseWidth(),
            armSpan,
            source.depthScale(),
            source.waistRatio(),
            source.armLevelScales(),
            source.density());
    }

    private static TowerParameterSet withDensity(TowerParameterSet source, StructureDensity density) {
        return new TowerParameterSet(
            source.height(),
            source.baseWidth(),
            source.armSpan(),
            source.depthScale(),
            source.waistRatio(),
            source.armLevelScales(),
            density);
    }
}
