package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.ConstraintAdjustment;
import com.plot.plugin.powerline.design.parametric.ConstraintIssue;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerConstraintResult;
import com.plot.plugin.powerline.design.parametric.TowerConstraintSolver;
import com.plot.plugin.powerline.design.parametric.TowerLineBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParametricHeightLimits;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfile;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.ui.PowerLineUiWidgets;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.Optional;

/** Compact status summary for the parametric tower editor. */
public final class TowerParameterStatusPanel {

    public void render(TowerDesignerContext context) {
        PoleDesign draft = context.draft();
        if (!draft.isParametricMode() && !draft.isManualLegacyMode()) {
            return;
        }
        if (!draft.hasTowerStructure() && !draft.isParametricMode()) {
            return;
        }

        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.tower_status_section"));

        String profileId = draft.getGeneratorConfig() != null
            ? draft.getGeneratorConfig().profileId()
            : TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID;
        DialogLayoutHelperStatus.row(
            PlotI18n.tr("plugin.powerline.design.tower_status_profile"),
            TowerProfileUiCatalog.labelFor(profileId));

        if (draft.isManualLegacyMode()) {
            DialogLayoutHelperStatus.row(
                PlotI18n.tr("plugin.powerline.design.tower_status_mode"),
                PlotI18n.tr("plugin.powerline.design.tower_status_mode_manual"));
        } else if (draft.isParametricMode()) {
            DialogLayoutHelperStatus.row(
                PlotI18n.tr("plugin.powerline.design.tower_status_mode"),
                PlotI18n.tr("plugin.powerline.design.tower_status_mode_parametric"));
            renderHeightStatus(context, draft);
            renderConstraintStatus(context);
        }
    }

    private void renderHeightStatus(TowerDesignerContext context, PoleDesign draft) {
        TowerParameterProfile profile = TowerParametricEditor.findProfile(draft.getGeneratorConfig().profileId())
            .orElse(TowerParameterProfiles.classicDoubleArm());
        TowerParameterSet parameters = draft.getGeneratorConfig().parameters();
        TowerBuildEnvelope envelope = context.session().resolveConstraintEnvelope();
        TowerParametricHeightLimits.EffectiveHeightRange heightRange = TowerParametricHeightLimits.heightRange(
            profile,
            parameters,
            envelope);
        int resolvedHeight = (int) Math.round(parameters.height());
        int maxHeight = (int) Math.floor(heightRange.max());
        DialogLayoutHelperStatus.row(
            PlotI18n.tr("plugin.powerline.design.tower_status_height"),
            PlotI18n.tr("plugin.powerline.design.tower_status_height_value", resolvedHeight, maxHeight));
    }

    private void renderConstraintStatus(TowerDesignerContext context) {
        TowerConstraintResult result = context.session().lastConstraintResult();
        if (result == null) {
            result = TowerParametricEditor.preview(
                context.draft(),
                context.draft().getGeneratorConfig().parameters(),
                context.session().resolveConstraintEnvelope());
        }
        if (result == null) {
            DialogLayoutHelperStatus.row(
                PlotI18n.tr("plugin.powerline.design.tower_status_state"),
                PlotI18n.tr("plugin.powerline.design.tower_status_ready"));
            return;
        }

        long errors = result.issues().stream()
            .filter(issue -> issue.severity() == com.plot.plugin.powerline.design.parametric.ConstraintSeverity.ERROR)
            .count();
        long warnings = result.issues().stream()
            .filter(issue -> issue.severity() == com.plot.plugin.powerline.design.parametric.ConstraintSeverity.WARNING)
            .count();

        if (errors > 0) {
            PowerLineUiWidgets.textColored(0xFFE57373, PlotI18n.tr(
                "plugin.powerline.design.tower_status_errors_format", errors));
            PowerLineUiWidgets.textColored(0xFFFFB74D, PlotI18n.tr(
                "plugin.powerline.design.tower_preview_last_valid"));
            for (ConstraintIssue issue : result.issues()) {
                if (issue.severity() == com.plot.plugin.powerline.design.parametric.ConstraintSeverity.ERROR) {
                    PowerLineUiWidgets.textColored(0xFFE57373, friendlyIssueMessage(issue, context));
                }
            }
        } else if (warnings > 0) {
            PowerLineUiWidgets.textColored(0xFFFFB74D, PlotI18n.tr(
                "plugin.powerline.design.tower_status_warnings_format", warnings));
            for (ConstraintIssue issue : result.issues()) {
                if (issue.severity() == com.plot.plugin.powerline.design.parametric.ConstraintSeverity.WARNING) {
                    PowerLineUiWidgets.textColored(0xFFFFB74D, friendlyIssueMessage(issue, context));
                }
            }
        } else {
            DialogLayoutHelperStatus.row(
                PlotI18n.tr("plugin.powerline.design.tower_status_state"),
                PlotI18n.tr("plugin.powerline.design.tower_status_ready"));
        }

        for (ConstraintAdjustment adjustment : result.adjustments()) {
            if (adjustment.requestedValue() != adjustment.resolvedValue()) {
                PowerLineUiWidgets.textColored(0xFF9E9E9E, PlotI18n.tr(
                    "plugin.powerline.design.tower_status_adjustment",
                    friendlyParameterLabel(adjustment.parameter()),
                    adjustment.resolvedValue()));
            }
        }
    }

    private static String friendlyIssueMessage(ConstraintIssue issue, TowerDesignerContext context) {
        if (TowerConstraintSolver.CODE_WORLD_HEIGHT_EXCEEDED.equals(issue.code())) {
            Optional<TowerLineBuildEnvelope> lineEnvelope = context.session().tryResolveLineEnvelope();
            if (lineEnvelope.isPresent()) {
                return PlotI18n.tr("plugin.powerline.design.tower_status_world_height_line");
            }
            return PlotI18n.tr("plugin.powerline.design.tower_status_world_height");
        }
        if (TowerConstraintSolver.CODE_TAPER_TOO_STEEP.equals(issue.code())) {
            return PlotI18n.tr("plugin.powerline.design.tower_status_taper_steep");
        }
        if (TowerConstraintSolver.CODE_ARM_VERTICAL_SPACING.equals(issue.code())) {
            return PlotI18n.tr("plugin.powerline.design.tower_status_arm_spacing");
        }
        if (TowerConstraintSolver.CODE_BAY_HEIGHT_TOO_SMALL.equals(issue.code())) {
            return PlotI18n.tr("plugin.powerline.design.tower_status_bay_height");
        }
        return issue.message() != null && !issue.message().isBlank()
            ? issue.message()
            : PlotI18n.tr("plugin.powerline.design.tower_status_generic_issue");
    }

    private static String friendlyParameterLabel(String parameterName) {
        return switch (parameterName) {
            case "height" -> PlotI18n.tr("plugin.powerline.design.parametric_height");
            case "baseWidth" -> PlotI18n.tr("plugin.powerline.design.parametric_base_width");
            case "armSpan" -> PlotI18n.tr("plugin.powerline.design.parametric_arm_span");
            default -> parameterName;
        };
    }

    /** Minimal two-column status rows without pulling in full form layout. */
    private static final class DialogLayoutHelperStatus {
        private DialogLayoutHelperStatus() {
        }

        static void row(String label, String value) {
            ImGui.text(label);
            ImGui.sameLine(160f);
            ImGui.textDisabled(value);
        }
    }
}
