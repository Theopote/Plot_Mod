package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PoleSpacingMode;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PoleSpacingProfile;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

/** 线路 Tab：全部线路、路径拾取、杆塔布置。 */
public final class PowerLineRoutePanel {
    private final PowerLineUiContext ctx;
    private final PowerLinePathPickPanel pathPickPanel;
    private final PowerLineOverviewPanel overviewPanel;

    public PowerLineRoutePanel(
            PowerLineUiContext ctx,
            PowerLineOverviewPanel overviewPanel) {
        this.ctx = ctx;
        this.pathPickPanel = new PowerLinePathPickPanel(ctx);
        this.overviewPanel = overviewPanel;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());

        renderCurrentLineHeader();

        ImGui.separator();
        overviewPanel.renderProjectSection();

        ImGui.separator();
        if (PowerLineUiWidgets.renderMultiLineEditBlocked(ctx)) {
            return;
        }
        pathPickPanel.render(line);

        if (line != null) {
            ImGui.separator();
            renderPolePlacement(line);
            renderAdvancedSpacing(line);
        }
    }

    public void renderDeleteConfirmPopup() {
        overviewPanel.renderDeleteConfirmPopup();
    }

    private void renderCurrentLineHeader() {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.current_line"));
        ImGui.sameLine();
        float width = ImGui.getContentRegionAvail().x;
        if (width > 0f) {
            ImGui.setNextItemWidth(width);
        }
        if (!PowerLineUiWidgets.renderLineSelector(ctx, false)) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.current_line_empty"));
        }
    }

    private void renderPolePlacement(PowerLineFootprint line) {
        renderPlacementMode(line);
        if (line.getPoleSpacingMode() == PoleSpacingMode.AUTO_SPACING) {
            renderSpacingPresets(line);
            renderSpacingSlider(line);
        } else if (line.getPoleSpacingMode() == PoleSpacingMode.TOWER_COUNT) {
            renderTowerCountInput(line);
        } else if (line.getPoleSpacingMode() == PoleSpacingMode.ENDPOINTS_ONLY) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.placement_mode_hint.ENDPOINTS_ONLY"));
        } else if (line.getPoleSpacingMode() == PoleSpacingMode.ENDPOINTS_WITH_CORNERS) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.placement_mode_hint.ENDPOINTS_WITH_CORNERS"));
        }
        renderCornerBehaviorHint(line);
    }

    private void renderCornerBehaviorHint(PowerLineFootprint line) {
        String hintKey = switch (line.getPoleSpacingMode()) {
            case AUTO_SPACING -> "plugin.powerline.route.corner_hint.auto_spacing";
            case ENDPOINTS_WITH_CORNERS -> "plugin.powerline.route.corner_hint.endpoints_with_corners";
            default -> null;
        };
        if (hintKey == null) {
            return;
        }
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(hintKey));
    }

    private void renderPlacementMode(PowerLineFootprint line) {
        PoleSpacingMode[] modes = PoleSpacingMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr("plugin.powerline.route.placement_mode." + modes[i].name());
        }
        int current = line.getPoleSpacingMode().ordinal();
        ImInt index = new ImInt(current);
        if (ImGui.combo(
                PowerLineUiWidgets.stableLabel("plugin.powerline.route.placement_mode", "placement_mode"),
                index,
                labels)) {
            PoleSpacingMode selected = modes[index.get()];
            if (selected != line.getPoleSpacingMode()) {
                ctx.pushEditSnapshot();
                line.setPoleSpacingMode(selected);
                ctx.invalidatePreview();
            }
        }
    }

    private void renderSpacingPresets(PowerLineFootprint line) {
        PowerLineUiPresets.SpacingDensity detected = PowerLineSpacingPolicy.detectDensity(line);
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2f) / 3f;
        for (PowerLineUiPresets.SpacingDensity density : PowerLineUiPresets.SpacingDensity.values()) {
            if (density != PowerLineUiPresets.SpacingDensity.values()[0]) {
                ImGui.sameLine();
            }
            boolean selected = !PowerLineStyleEditor.isSpacingCustomized(line) && density == detected;
            String label = PlotI18n.tr("plugin.powerline.route.spacing." + density.name().toLowerCase());
            if (selected) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
            }
            if (ImGui.button(label + "##preset_" + density.name(), buttonWidth, 0)) {
                ctx.pushEditSnapshot();
                PowerLineUiPresets.applySpacing(line, density);
                ctx.invalidatePreview();
            }
            if (selected) {
                ImGui.popStyleColor();
            }
        }
        ImGui.spacing();
    }

    private void renderSpacingSlider(PowerLineFootprint line) {
        float sliderMin = (float) PowerLineFootprint.MIN_CONFIGURABLE_SPACING;
        float sliderMax = (float) PowerLineSpacingPolicy.sliderMax(line);
        float[] spacing = {(float) line.getMaxPoleSpacing()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "pole_spacing",
            "plugin.powerline.route.pole_spacing",
            spacing,
            sliderMin,
            sliderMax,
            PowerLineUiFormat.SLIDER,
            value -> {
                line.setMaxPoleSpacing(value);
                PowerLineStyleEditor.afterSpacingEdit(line);
            });
        if (ImGui.isItemHovered()) {
            PoleSpacingProfile profile = PowerLineSpacingPolicy.profileFor(line);
            ImGui.setTooltip(PlotI18n.tr(
                "plugin.powerline.route.pole_spacing_style_tooltip",
                PowerLineUiFormat.format(profile.denseFloor()),
                PowerLineUiFormat.format(profile.recommendedMax()),
                PowerLineUiFormat.format(profile.preferred())));
        }
    }

    private void renderTowerCountInput(PowerLineFootprint line) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.tower_count"));
        int[] count = {line.getTargetTowerCount()};
        PowerLineUiWidgets.inputIntStableLineEdit(
            ctx,
            "tower_count",
            count,
            1,
            1,
            line::setTargetTowerCount);
        double worldLength = line.computeWorldPathLength(ctx.coordinates());
        int poles = count[0];
        if (poles > 1 && worldLength > 0.0) {
            double implied = PowerLineBuildMetrics.typicalSpanBlocks(
                line,
                worldLength,
                poles,
                line.getMaxPoleSpacing());
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.tower_count_implied_spacing", PowerLineUiFormat.format(implied)));
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.route.tower_count_hint"));
    }

    private void renderAdvancedSpacing(PowerLineFootprint line) {
        PoleSpacingMode mode = line.getPoleSpacingMode();
        if (mode == PoleSpacingMode.ENDPOINTS_ONLY) {
            renderPausedAutoCorrectionPoles(line);
            return;
        }
        if (!shouldShowAdvancedSection(line)) {
            return;
        }

        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.route.advanced"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }

        if (mode == PoleSpacingMode.AUTO_SPACING) {
            renderCornerAngleThreshold(line);
            renderSpacingRecommendation(line);
        } else if (mode == PoleSpacingMode.ENDPOINTS_WITH_CORNERS) {
            renderCornerAngleThreshold(line);
        }

        renderAutoCorrectionPoles(line);
    }

    private static boolean shouldShowAdvancedSection(PowerLineFootprint line) {
        PoleSpacingMode mode = line.getPoleSpacingMode();
        if (mode == PoleSpacingMode.AUTO_SPACING || mode == PoleSpacingMode.ENDPOINTS_WITH_CORNERS) {
            return true;
        }
        if (mode == PoleSpacingMode.TOWER_COUNT) {
            return !line.effectiveLayoutConstraints().isEmpty();
        }
        return false;
    }

    private void renderCornerAngleThreshold(PowerLineFootprint line) {
        float[] cornerAngle = {(float) line.getCornerAngleThreshold()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "corner_angle",
            "plugin.powerline.corner_angle",
            cornerAngle,
            (float) com.plot.plugin.powerline.PowerPoleLayoutUtils.MIN_CORNER_ANGLE_THRESHOLD_DEGREES,
            90f,
            PowerLineUiFormat.SLIDER,
            line::setCornerAngleThreshold);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.powerline.route.corner_hint.detail"));
        }
    }

    private void renderSpacingRecommendation(PowerLineFootprint line) {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.activePreset(line);
        if (preset == null || !PowerLineStyleEditor.isSpacingCustomized(line)) {
            return;
        }
        if (!PowerLineSpacingPolicy.differsFromStyleRecommendation(line, preset)) {
            return;
        }
        PoleSpacingProfile profile = preset.getSpacingProfile();
        PowerLineUiWidgets.textColored(
            PluginUiColors.WARNING,
            PlotI18n.tr(
                "plugin.powerline.route.spacing_recommendation",
                PowerLineUiFormat.format(profile.preferred()),
                PowerLineUiFormat.format(line.getMaxPoleSpacing())));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.route.apply_recommended_spacing"), 0, 0)) {
            ctx.pushEditSnapshot();
            PowerLineSpacingPolicy.applyStyleDefaultSpacing(line, profile);
            PowerLineStyleEditor.afterSpacingAdopted(line);
            ctx.invalidatePreview();
        }
    }

    private void renderPausedAutoCorrectionPoles(PowerLineFootprint line) {
        int count = line.getDerivedLayout().autoLayoutConstraints().size();
        if (count <= 0) {
            return;
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.route.auto_poles.paused_endpoints", count));
    }

    private void renderAutoCorrectionPoles(PowerLineFootprint line) {
        var constraints = line.getDerivedLayout().autoLayoutConstraints();
        if (constraints.isEmpty()) {
            return;
        }
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.auto_poles.section"));
        ImGui.spacing();
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.route.auto_poles.summary", constraints.size()));
        ImGui.spacing();
        for (int i = 0; i < constraints.size(); i++) {
            var constraint = constraints.get(i);
            ImGui.pushID("powerline_auto_pole_" + i);
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.route.auto_poles.entry",
                PowerLineUiFormat.format(constraint.getRequiredStationing()),
                PowerLineAutoPoleLabels.friendlyReason(constraint)));
            ImGui.popID();
            ImGui.spacing();
        }
    }
}
