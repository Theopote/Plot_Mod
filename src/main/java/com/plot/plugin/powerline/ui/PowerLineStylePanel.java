package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** 样式 Tab：塔型主题、下垂、材质。 */
public final class PowerLineStylePanel {
    private final PowerLineUiContext ctx;
    private final PowerLineEditPanel editPanel;
    private final PoleDesignerPanel poleDesignerPanel;

    public PowerLineStylePanel(
            PowerLineUiContext ctx,
            PowerLineEditPanel editPanel,
            PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.editPanel = editPanel;
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
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.style.section.tower"));
        renderStylePack(line);
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.style.section.wire"));
        renderSagPresets(line);
        editPanel.renderMaterialControlsPublic(line);
        editPanel.renderPoleDesignControlsPublic(line, poleDesignerPanel);
        renderAdvancedStyle(line);
    }

    private void renderStylePack(PowerLineFootprint line) {
        String selected = line.hasTowerFamily() ? line.getTowerFamilyId() : "";
        String[][] packs = {
            {"", PlotI18n.tr("plugin.powerline.style.pack.wood")},
            {TowerFamily.STANDARD_LATTICE_3_PHASE_ID, PlotI18n.tr("plugin.powerline.style.pack.lattice")},
            {TowerFamily.GRADED_LATTICE_3_PHASE_ID, PlotI18n.tr("plugin.powerline.style.pack.adaptive")},
        };

        float spacing = ImGui.getStyle().getItemSpacingX();
        float totalWidth = PowerLineStyleCardRenderer.cardWidth() * packs.length
            + spacing * (packs.length - 1);
        float startX = ImGui.getCursorPosX();
        if (totalWidth < ImGui.getContentRegionAvail().x) {
            ImGui.setCursorPosX(startX + (ImGui.getContentRegionAvail().x - totalWidth) * 0.5f);
        }

        for (int i = 0; i < packs.length; i++) {
            if (i > 0) {
                ImGui.sameLine(0f, spacing);
            }
            boolean active = packs[i][0].equals(selected);
            if (PowerLineStyleCardRenderer.renderStyleCard(packs[i][0], packs[i][1], active)) {
                ctx.pushEditSnapshot();
                line.setTowerFamilyId(packs[i][0].isBlank() ? null : packs[i][0]);
                ctx.invalidatePreview();
            }
        }
        ImGui.newLine();

        if (line.hasTowerFamily()) {
            var family = new com.plot.plugin.powerline.design.family.TowerFamilyResolver().find(line.getTowerFamilyId());
            String name = family != null ? family.getName() : line.getTowerFamilyId();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.style.selected", name));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.style.selected_wood"));
        }
    }

    private void renderSagPresets(PowerLineFootprint line) {
        ImGui.text(PlotI18n.tr("plugin.powerline.style.sag"));
        PowerLineUiPresets.WireSag current = PowerLineUiPresets.detectSag(line);
        for (PowerLineUiPresets.WireSag sag : PowerLineUiPresets.WireSag.values()) {
            boolean selected = sag == current;
            if (ImGui.radioButton(PlotI18n.tr("plugin.powerline.style.sag." + sag.name().toLowerCase()), selected)) {
                ctx.pushEditSnapshot();
                PowerLineUiPresets.applySag(line, sag);
                ctx.invalidatePreview();
            }
            ImGui.sameLine();
        }
        ImGui.newLine();

        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.powerline.style.sag_advanced"), ImGuiTreeNodeFlags.None)) {
            float[] sagRatio = {(float) (line.getSagRatio() * 100f)};
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.powerline.sag_ratio", sagRatio[0]),
                    sagRatio,
                    0f,
                    35f,
                    "%.0f%%")) {
                line.setSagRatio(sagRatio[0] / 100f);
                ctx.invalidatePreview();
            }
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
        }
    }

    private void renderAdvancedStyle(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.powerline.style.advanced"), ImGuiTreeNodeFlags.None)) {
            return;
        }
        editPanel.renderPoleHeightControlsPublic(line);
        editPanel.renderPoleRoleInspectorPublic(line);
    }
}
