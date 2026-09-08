package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** 电力线路编辑 Tab（遗留完整编辑界面）。 */
public final class PowerLineEditPanel {
    private final PowerLineUiContext ctx;
    private final PoleDesignerPanel poleDesignerPanel;
    private final PowerLineStyleControls styleControls;

    public PowerLineEditPanel(PowerLineUiContext ctx, PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.poleDesignerPanel = poleDesignerPanel;
        this.styleControls = new PowerLineStyleControls(ctx);
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
        styleControls.renderMaterialControls(line);
        renderTowerFamilyControls(line);
        PowerLineUiWidgets.renderAdvancedEngineeringSection(ctx, line);
        styleControls.renderPoleDesignControls(line, poleDesignerPanel);
        styleControls.renderPoleRoleInspector(line);

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
        styleControls.renderPoleHeightControls(line);
        float[] sagRatio = {(float) (line.getSagRatio() * 100f)};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.sag_ratio", sagRatio[0]),
                sagRatio,
                0f,
                (float) (PowerLineUiPresets.ADVANCED_SAG_MAX_RATIO * 100f),
                "%.0f%%")) {
            PowerLineUiPresets.applyAdvancedSag(line, sagRatio[0] / 100f);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
    }

    private void renderTowerFamilyControls(PowerLineFootprint line) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.tower_family_section"));

        TowerFamilyResolver familyResolver = new TowerFamilyResolver();
        List<TowerFamily> families = familyResolver.listAll();
        String noneLabel = PlotI18n.tr("plugin.powerline.tower_family_none");
        String[] labels = new String[families.size() + 1];
        String[] ids = new String[families.size() + 1];
        labels[0] = noneLabel;
        ids[0] = "";
        for (int i = 0; i < families.size(); i++) {
            TowerFamily family = families.get(i);
            String prefix = TowerFamilyCatalog.isBuiltinId(family.getId())
                ? PlotI18n.tr("plugin.powerline.tower_family_builtin_prefix")
                : "";
            labels[i + 1] = prefix + family.getName();
            ids[i + 1] = family.getId();
        }

        int current = 0;
        String selectedId = line.getTowerFamilyId() != null ? line.getTowerFamilyId() : "";
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(selectedId)) {
                current = i;
                break;
            }
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo(PlotI18n.tr("plugin.powerline.tower_family"), labels[current])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(labels[i], current == i)) {
                    ctx.pushEditSnapshot();
                    line.setTowerFamilyId(ids[i].isBlank() ? null : ids[i]);
                    ctx.invalidatePreview();
                }
            }
            ImGui.endCombo();
        }
    }
}
