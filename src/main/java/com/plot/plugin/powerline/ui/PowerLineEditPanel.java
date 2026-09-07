package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 电力线路编辑 Tab。 */
public final class PowerLineEditPanel {
    private final PowerLineUiContext ctx;

    public PowerLineEditPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.select_line_hint"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            return;
        }

        PowerLineUiWidgets.renderLineSelector(ctx);

        if (!line.getId().equals(ctx.lineNameEditingId())) {
            ctx.lineNameBuffer().set(line.getName());
            ctx.setLineNameEditingId(line.getId());
        }
        if (ImGui.inputText(PlotI18n.tr("plugin.powerline.line_name"), ctx.lineNameBuffer())) {
            line.setName(ctx.lineNameBuffer().get());
        }
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }

        ImGui.separator();
        renderSpacingControls(line);
        renderPoleControls(line);
        renderMaterialControls(line);

        if (ctx.hasMinSpacingWarning(line)) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.min_spacing_warning"));
        }
    }

    private void renderSpacingControls(PowerLineFootprint line) {
        float[] minSpacing = {(float) line.getMinPoleSpacing()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.min_pole_spacing", minSpacing[0]),
                minSpacing,
                1f,
                30f,
                "%.1f")) {
            line.setMinPoleSpacing(minSpacing[0]);
        }
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }

        float[] maxSpacing = {(float) line.getMaxPoleSpacing()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.max_pole_spacing", maxSpacing[0]),
                maxSpacing,
                1f,
                60f,
                "%.1f")) {
            line.setMaxPoleSpacing(maxSpacing[0]);
        }
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }

        float[] cornerAngle = {(float) line.getCornerAngleThreshold()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.corner_angle", cornerAngle[0]),
                cornerAngle,
                0f,
                90f,
                "%.1f")) {
            line.setCornerAngleThreshold(cornerAngle[0]);
        }
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
    }

    private void renderPoleControls(PowerLineFootprint line) {
        float[] poleHeight = {(float) line.getPoleHeight()};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.pole_height", poleHeight[0]),
                poleHeight,
                1f,
                64f,
                "%.1f")) {
            line.setPoleHeight(poleHeight[0]);
        }
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }

        float[] sagRatio = {(float) (line.getSagRatio() * 100f)};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.sag_ratio", sagRatio[0]),
                sagRatio,
                0f,
                50f,
                "%.0f%%")) {
            line.setSagRatio(sagRatio[0] / 100f);
        }
        if (ImGui.isItemActivated()) {
            ctx.projectHistory().push(ctx.project());
        }
    }

    private void renderMaterialControls(PowerLineFootprint line) {
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "wire_material",
            PlotI18n.tr("plugin.powerline.wire_material"),
            line.getWireMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL),
            line::setWireMaterial);
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "pole_material",
            PlotI18n.tr("plugin.powerline.pole_material"),
            line.getPoleMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL),
            line::setPoleMaterial);
    }
}
