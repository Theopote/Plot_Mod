package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
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
        for (PowerLineUiPresets.SpacingDensity density : PowerLineUiPresets.SpacingDensity.values()) {
            boolean selected = density == current;
            if (ImGui.radioButton(
                    PlotI18n.tr("plugin.powerline.route.spacing." + density.name().toLowerCase()),
                    selected)) {
                ctx.pushEditSnapshot();
                PowerLineUiPresets.applySpacing(line, density);
                ctx.invalidatePreview();
            }
            ImGui.sameLine();
        }
        ImGui.newLine();
    }

    private void renderTerrainAvoidance(PowerLineFootprint line) {
        boolean enabled = line.isEngineeringAnalysisEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.route.avoid_terrain"), enabled)) {
            ctx.pushEditSnapshot();
            line.setEngineeringAnalysisEnabled(!enabled);
        }
    }

    private void renderAdvancedSpacing(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.route.advanced"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        float[] minSpacing = {(float) line.getMinPoleSpacing()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.min_pole_spacing", minSpacing[0]),
                minSpacing,
                1f,
                30f,
                "%.1f")) {
            line.setMinPoleSpacing(minSpacing[0]);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }

        float[] maxSpacing = {(float) line.getMaxPoleSpacing()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.max_pole_spacing", maxSpacing[0]),
                maxSpacing,
                1f,
                60f,
                "%.1f")) {
            line.setMaxPoleSpacing(maxSpacing[0]);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }

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

        if (ctx.hasMinSpacingWarning(line)) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.min_spacing_warning"));
        }
    }
}
