package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.List;

/** 电力线路编辑 Tab。 */
public final class PowerLineEditPanel {
    private final PowerLineUiContext ctx;
    private final PoleDesignerPanel poleDesignerPanel;

    public PowerLineEditPanel(PowerLineUiContext ctx, PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.poleDesignerPanel = poleDesignerPanel;
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
            ctx.pushEditSnapshot();
        }

        ImGui.separator();
        renderSpacingControls(line);
        renderPoleControls(line);
        renderMaterialControls(line);
        renderPoleDesignControls(line);

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
    }

    private void renderPoleControls(PowerLineFootprint line) {
        if (line.hasPoleDesign()) {
            PoleDesign design = ctx.designResolver().find(line.getPoleDesignId());
            int designHeight = design != null ? design.totalHeight() : (int) line.getPoleHeight();
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.pole_design_height_hint", designHeight));
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.pole_height_from_design"));
        } else {
            float[] poleHeight = {(float) line.getPoleHeight()};
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.powerline.pole_height", poleHeight[0]),
                    poleHeight,
                    1f,
                    64f,
                    "%.1f")) {
                line.setPoleHeight(poleHeight[0]);
                ctx.invalidatePreview();
            }
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
        }

        float[] sagRatio = {(float) (line.getSagRatio() * 100f)};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.sag_ratio", sagRatio[0]),
                sagRatio,
                0f,
                50f,
                "%.0f%%")) {
            line.setSagRatio(sagRatio[0] / 100f);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
    }

    private void renderMaterialControls(PowerLineFootprint line) {
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "wire_material",
            PlotI18n.tr("plugin.powerline.wire_material"),
            line.getWireMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL),
            mix -> {
                line.setWireMaterial(mix);
                ctx.invalidatePreview();
            });
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "pole_material",
            PlotI18n.tr("plugin.powerline.pole_material"),
            line.getPoleMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL),
            mix -> {
                line.setPoleMaterial(mix);
                ctx.invalidatePreview();
            });
    }

    private void renderPoleDesignControls(PowerLineFootprint line) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.pole_design_section"));

        PoleDesignResolver resolver = ctx.designResolver();
        List<PoleDesign> designs = resolver.listAll();
        String noneLabel = PlotI18n.tr("plugin.powerline.pole_design_default");
        String[] labels = new String[designs.size() + 1];
        String[] ids = new String[designs.size() + 1];
        labels[0] = noneLabel;
        ids[0] = "";
        for (int i = 0; i < designs.size(); i++) {
            PoleDesign design = designs.get(i);
            String prefix = PoleDesignCatalog.isBuiltinId(design.getId())
                ? PlotI18n.tr("plugin.powerline.pole_design.builtin_prefix")
                : "";
            labels[i + 1] = prefix + design.getName();
            ids[i + 1] = design.getId();
        }

        int current = 0;
        String selectedId = line.getPoleDesignId() != null ? line.getPoleDesignId() : "";
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(selectedId)) {
                current = i;
                break;
            }
        }

        ImInt designIndex = new ImInt(current);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 110);
        if (ImGui.beginCombo(PlotI18n.tr("plugin.powerline.pole_design"), labels[current])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(labels[i], designIndex.get() == i)) {
                    ctx.pushEditSnapshot();
                    line.setPoleDesignId(ids[i].isBlank() ? null : ids[i]);
                    ctx.invalidatePreview();
                }
            }
            ImGui.endCombo();
        }

        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.open_designer"), 0, 0)) {
            poleDesignerPanel.open(line.getPoleDesignId());
        }
    }
}
