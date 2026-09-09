package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;
import com.plot.plugin.powerline.style.PoleSpacingProfile;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** 线路 Tab：认领路径、杆塔间距、地形适应。 */
public final class PowerLineRoutePanel {
    private final PowerLineUiContext ctx;
    private final PowerLineAdoptPanel adoptPanel;

    public PowerLineRoutePanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
        this.adoptPanel = new PowerLineAdoptPanel(ctx);
    }

    public void render() {
        ImGui.text(PlotI18n.tr("plugin.powerline.route.section.path"));
        adoptPanel.render();

        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            ImGui.separator();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.no_line"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            return;
        }

        ImGui.separator();
        PowerLineUiWidgets.renderLineSelector(ctx);
        renderLineName(line);
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.route.section.placement"));
        renderSpacingPresets(line);
        PoleSpacingProfile spacingProfile = PowerLineSpacingPolicy.profileFor(line);
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.powerline.route.spacing_style_range",
                spacingProfile.recommendedMin(),
                spacingProfile.preferred(),
                spacingProfile.recommendedMax()));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.spacing_hint"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.corner_hint"));
        renderTerrainAvoidance(line);
        renderAdvancedSpacing(line);
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

    private void renderSpacingPresets(PowerLineFootprint line) {
        ImGui.text(PlotI18n.tr("plugin.powerline.route.spacing"));
        PowerLineUiPresets.SpacingDensity current = PowerLineUiPresets.detectSpacing(line);

        float spacing = ImGui.getStyle().getItemSpacingX();
        float totalWidth = PowerLineSpacingCardRenderer.CARD_WIDTH
            * PowerLineUiPresets.SpacingDensity.values().length
            + spacing * (PowerLineUiPresets.SpacingDensity.values().length - 1);
        float startX = ImGui.getCursorPosX();
        if (totalWidth < ImGui.getContentRegionAvail().x) {
            ImGui.setCursorPosX(startX + (ImGui.getContentRegionAvail().x - totalWidth) * 0.5f);
        }

        for (PowerLineUiPresets.SpacingDensity density : PowerLineUiPresets.SpacingDensity.values()) {
            if (density != PowerLineUiPresets.SpacingDensity.values()[0]) {
                ImGui.sameLine(0f, spacing);
            }
            boolean selected = density == current;
            String label = PlotI18n.tr("plugin.powerline.route.spacing." + density.name().toLowerCase());
            if (PowerLineSpacingCardRenderer.renderSpacingCard(line, density, label, selected)) {
                ctx.pushEditSnapshot();
                PowerLineUiPresets.applySpacing(line, density);
                ctx.invalidatePreview();
            }
        }
        ImGui.newLine();
    }

    private void renderTerrainAvoidance(PowerLineFootprint line) {
        boolean enabled = line.isTerrainAvoidanceEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.route.avoid_terrain"), enabled)) {
            ctx.pushEditSnapshot();
            line.setTerrainAvoidanceEnabled(!enabled);
            ctx.invalidatePreview();
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.avoid_terrain_hint"));
    }

    private void renderAdvancedSpacing(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.route.advanced"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        float sliderMin = (float) PowerLineFootprint.MIN_CONFIGURABLE_SPACING;
        float sliderMax = (float) PowerLineSpacingPolicy.sliderMax(line);
        float[] minSpacing = {(float) line.getMinPoleSpacing()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.min_pole_spacing", minSpacing[0]),
                minSpacing,
                sliderMin,
                sliderMax,
                "%.1f")) {
            line.setMinPoleSpacing(minSpacing[0]);
            com.plot.plugin.powerline.style.PowerLineStyleEditor.afterSpacingEdit(line);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.powerline.recommended_min_spacing"));
        }

        float[] maxSpacing = {(float) line.getMaxPoleSpacing()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.max_pole_spacing", maxSpacing[0]),
                maxSpacing,
                sliderMin,
                sliderMax,
                "%.1f")) {
            line.setMaxPoleSpacing(maxSpacing[0]);
            com.plot.plugin.powerline.style.PowerLineStyleEditor.afterSpacingEdit(line);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.powerline.recommended_max_spacing"));
        }

        renderSpacingRecommendation(line);

        float[] cornerAngle = {(float) line.getCornerAngleThreshold()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.corner_angle", cornerAngle[0]),
                cornerAngle,
                0f,
                90f,
                "%.1f")) {
            line.setCornerAngleThreshold(cornerAngle[0]);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }

        ctx.actions().closestMandatorySpacingViolation(line).ifPresent(distance -> ImGui.textColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.powerline.min_spacing_warning", distance)));
        renderAutoAddedPoles(line);
    }

    private void renderSpacingRecommendation(PowerLineFootprint line) {
        var preset = com.plot.plugin.powerline.style.PowerLineStylePresetCatalog.activePreset(line);
        if (preset == null || !line.isSpacingCustomized()) {
            return;
        }
        if (!PowerLineSpacingPolicy.differsFromStyleRecommendation(line, preset)) {
            return;
        }
        PoleSpacingProfile profile = preset.getSpacingProfile();
        ImGui.textColored(
            PluginUiColors.WARNING,
            PlotI18n.tr(
                "plugin.powerline.route.spacing_recommendation",
                profile.preferred(),
                line.getMaxPoleSpacing()));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.route.apply_recommended_spacing"), 0, 0)) {
            ctx.pushEditSnapshot();
            PowerLineSpacingPolicy.applyStyleDefaultSpacing(line, profile);
            com.plot.plugin.powerline.style.PowerLineStyleEditor.afterSpacingAdopted(line);
            ctx.invalidatePreview();
        }
    }

    private void renderAutoAddedPoles(PowerLineFootprint line) {
        var constraints = line.getLayoutConstraints();
        if (constraints.isEmpty()) {
            return;
        }
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.route.auto_poles.section"));
        ImGui.spacing();
        for (int i = 0; i < constraints.size(); i++) {
            var constraint = constraints.get(i);
            ImGui.pushID("powerline_auto_pole_" + i);
            ImGui.text(PlotI18n.tr(
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
