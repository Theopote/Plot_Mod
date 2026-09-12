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

/** 线路 Tab：摘要、认领路径、杆塔布置。 */
public final class PowerLineRoutePanel {
    private final PowerLineUiContext ctx;
    private final PowerLineAdoptPanel adoptPanel;
    private final PowerLineOverviewPanel overviewPanel;

    public PowerLineRoutePanel(PowerLineUiContext ctx, PowerLineOverviewPanel overviewPanel) {
        this.ctx = ctx;
        this.adoptPanel = new PowerLineAdoptPanel(ctx);
        this.overviewPanel = overviewPanel;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());

        if (line != null) {
            PowerLineRouteSummaryRenderer.render(ctx, line);
        }

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.section.path"));
        adoptPanel.render();

        if (line == null) {
            ImGui.separator();
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.no_line"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            renderProjectSection();
            return;
        }

        ImGui.separator();
        PowerLineUiWidgets.renderLineSelector(ctx);
        renderLineName(line);
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.section.placement"));
        renderPolePlacement(line);
        renderTerrainAvoidance(line);
        renderAdvancedSpacing(line);
        renderProjectSection();
    }

    public void renderDeleteConfirmPopup() {
        overviewPanel.renderDeleteConfirmPopup();
    }

    private void renderProjectSection() {
        ImGui.separator();
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.route.section.all_lines"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        overviewPanel.renderProjectSection();
    }

    private void renderLineName(PowerLineFootprint line) {
        if (!line.getId().equals(ctx.lineNameEditingId())) {
            ctx.lineNameBuffer().set(line.getName());
            ctx.setLineNameEditingId(line.getId());
        }
        if (ImGui.inputText(PlotI18n.tr("plugin.powerline.line_name"), ctx.lineNameBuffer())) {
            line.setName(ctx.lineNameBuffer().get());
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
    }

    private void renderPolePlacement(PowerLineFootprint line) {
        renderPlacementMode(line);
        ImGui.spacing();
        if (line.getPoleSpacingMode() == PoleSpacingMode.AUTO_SPACING) {
            renderSpacingPresets(line);
            renderSpacingSlider(line);
        } else if (line.getPoleSpacingMode() == PoleSpacingMode.TOWER_COUNT) {
            renderTowerCountInput(line);
        } else {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.placement_mode_hint." + line.getPoleSpacingMode().name()));
        }
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.corner_hint"));
    }

    private void renderPlacementMode(PowerLineFootprint line) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.placement_mode"));
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
            boolean selected = !line.isSpacingCustomized() && density == detected;
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
            "%.0f",
            value -> {
                line.setMaxPoleSpacing(value);
                PowerLineStyleEditor.afterSpacingEdit(line);
            });

        ImGui.sameLine();
        ImGui.setNextItemWidth(64f);
        imgui.type.ImFloat input = new imgui.type.ImFloat(spacing[0]);
        if (ImGui.inputFloat("##pole_spacing_input", input, 1f, 4f, "%.0f")) {
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
            float clamped = Math.max(sliderMin, Math.min(sliderMax, input.get()));
            line.setMaxPoleSpacing(clamped);
            PowerLineStyleEditor.afterSpacingEdit(line);
            ctx.invalidatePreview();
        }
        ImGui.sameLine();
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.spacing_blocks"));
    }

    private void renderTowerCountInput(PowerLineFootprint line) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.tower_count"));
        ImInt count = new ImInt(line.getTargetTowerCount());
        if (ImGui.inputInt("##tower_count", count, 1, 1)) {
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
            line.setTargetTowerCount(count.get());
            ctx.invalidatePreview();
        }
        double worldLength = line.computeWorldPathLength(ctx.coordinates());
        int poles = line.getTargetTowerCount();
        if (poles > 1 && worldLength > 0.0) {
            double implied = worldLength / (poles - 1);
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.tower_count_implied_spacing", String.format("%.0f", implied)));
        }
    }

    private void renderTerrainAvoidance(PowerLineFootprint line) {
        boolean enabled = line.isTerrainAvoidanceEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.route.avoid_terrain"), enabled)) {
            ctx.pushEditSnapshot();
            line.setTerrainAvoidanceEnabled(!enabled);
            ctx.invalidatePreview();
        }
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.avoid_terrain_hint"));
    }

    private void renderAdvancedSpacing(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.route.advanced"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        PoleSpacingProfile profile = PowerLineSpacingPolicy.profileFor(line);
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.powerline.route.spacing_style_range",
                profile.recommendedMin(),
                profile.preferred(),
                profile.recommendedMax()));
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.powerline.route.current_spacing",
                line.getMinPoleSpacing(),
                line.getMaxPoleSpacing()));

        float sliderMin = (float) PowerLineFootprint.MIN_CONFIGURABLE_SPACING;
        float sliderMax = (float) PowerLineSpacingPolicy.sliderMax(line);
        float[] minSpacing = {(float) line.getMinPoleSpacing()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "min_pole_spacing",
            "plugin.powerline.min_pole_spacing",
            minSpacing,
            sliderMin,
            sliderMax,
            "%.1f",
            value -> {
                line.setMinPoleSpacing(value);
                PowerLineStyleEditor.afterSpacingEdit(line);
            });

        float[] maxSpacing = {(float) line.getMaxPoleSpacing()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "max_pole_spacing",
            "plugin.powerline.max_pole_spacing",
            maxSpacing,
            sliderMin,
            sliderMax,
            "%.1f",
            value -> {
                line.setMaxPoleSpacing(value);
                PowerLineStyleEditor.afterSpacingEdit(line);
            });

        renderSpacingRecommendation(line);

        float[] cornerAngle = {(float) line.getCornerAngleThreshold()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "corner_angle",
            "plugin.powerline.corner_angle",
            cornerAngle,
            0f,
            90f,
            "%.1f",
            line::setCornerAngleThreshold);

        ctx.actions().closestMandatorySpacingViolation(line).ifPresent(distance -> PowerLineUiWidgets.textColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.powerline.min_spacing_warning", distance)));
        renderAutoAddedPoles(line);
    }

    private void renderSpacingRecommendation(PowerLineFootprint line) {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.activePreset(line);
        if (preset == null || !line.isSpacingCustomized()) {
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
                profile.preferred(),
                line.getMaxPoleSpacing()));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.route.apply_recommended_spacing"), 0, 0)) {
            ctx.pushEditSnapshot();
            PowerLineSpacingPolicy.applyStyleDefaultSpacing(line, profile);
            PowerLineStyleEditor.afterSpacingAdopted(line);
            ctx.invalidatePreview();
        }
    }

    private void renderAutoAddedPoles(PowerLineFootprint line) {
        var constraints = line.getLayoutConstraints();
        if (constraints.isEmpty()) {
            return;
        }
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.auto_poles.section"));
        ImGui.spacing();
        for (int i = 0; i < constraints.size(); i++) {
            var constraint = constraints.get(i);
            ImGui.pushID("powerline_auto_pole_" + i);
            PowerLineStatusIcon.renderBulletLine(PlotI18n.tr(
                "plugin.powerline.route.auto_poles.entry",
                String.format("%.1f", constraint.getRequiredStationing()),
                PowerLineAutoPoleLabels.friendlyReason(constraint)));
            if (ImGui.button(PlotI18n.tr("plugin.powerline.route.auto_poles.remove"), 0, 0)) {
                ctx.pushEditSnapshot();
                line.removeLayoutConstraint(i);
                ctx.invalidatePreview();
            }
            ImGui.popID();
            ImGui.spacing();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.route.auto_poles.clear_all"), 0, 0)) {
            ctx.pushEditSnapshot();
            line.clearLayoutConstraints();
            ctx.invalidatePreview();
        }
    }
}
