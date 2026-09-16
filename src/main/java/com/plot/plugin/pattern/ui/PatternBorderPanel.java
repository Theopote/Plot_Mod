package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternBorderConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;

/** 图案边框配置（嵌入图案页）。 */
public final class PatternBorderPanel {
    private final PatternUiContext ctx;

    public PatternBorderPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderSection(PatternFootprint footprint) {
        if (footprint == null) {
            return;
        }
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.pattern.border_section"))) {
            return;
        }

        PatternBorderConfig borderConfig = footprint.getBorderConfig();
        Runnable beforeEdit = () -> ctx.projectHistory().push(ctx.project());
        Runnable commit = () -> {
            footprint.setBorderConfig(borderConfig);
            ctx.actions().invalidatePreview();
        };

        ImBoolean enabled = new ImBoolean(borderConfig.isEnabled());
        if (ImGui.checkbox(PlotI18n.tr("plugin.pattern.border_enabled"), enabled)) {
            beforeEdit.run();
            borderConfig.setEnabled(enabled.get());
            commit.run();
        }

        if (!borderConfig.isEnabled()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.border_disabled_hint"));
            return;
        }

        ImGui.spacing();
        renderBorderStyleCombo(borderConfig, beforeEdit, commit);

        ImFloat borderWidth = new ImFloat((float) borderConfig.getBorderWidth());
        boolean widthChanged = ImGui.sliderFloat(
            PlotI18n.tr("plugin.pattern.border_width"),
            borderWidth.getData(),
            0.5f,
            5.0f,
            "%.1f");
        if (ImGui.isItemActivated()) {
            beforeEdit.run();
        }
        if (widthChanged) {
            borderConfig.setBorderWidth(borderWidth.get());
            commit.run();
        }

        ImBoolean innerBorder = new ImBoolean(borderConfig.isInnerBorder());
        if (ImGui.checkbox(PlotI18n.tr("plugin.pattern.border_inner"), innerBorder)) {
            beforeEdit.run();
            borderConfig.setInnerBorder(innerBorder.get());
            commit.run();
        }

        ImBoolean outerBorder = new ImBoolean(borderConfig.isOuterBorder());
        if (ImGui.checkbox(PlotI18n.tr("plugin.pattern.border_outer"), outerBorder)) {
            beforeEdit.run();
            borderConfig.setOuterBorder(outerBorder.get());
            commit.run();
        }

        ImFloat cornerRadius = new ImFloat((float) borderConfig.getCornerRadius());
        boolean radiusChanged = ImGui.sliderFloat(
            PlotI18n.tr("plugin.pattern.border_corner_radius"),
            cornerRadius.getData(),
            0.0f,
            3.0f,
            "%.1f");
        if (ImGui.isItemActivated()) {
            beforeEdit.run();
        }
        if (radiusChanged) {
            borderConfig.setCornerRadius(cornerRadius.get());
            commit.run();
        }

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.pattern.border_material"));
        String currentMaterial = borderConfig.getPrimaryBorderMaterial();
        if (ImGui.button(UIUtils.getBlockDisplayName(currentMaterial) + "##border_mat", 0, 0)) {
            beforeEdit.run();
            UIUtils.openBlockPicker(currentMaterial, blockId -> {
                borderConfig.setBorderMaterials(java.util.List.of(blockId));
                commit.run();
            });
        }
    }

    private void renderBorderStyleCombo(
            PatternBorderConfig borderConfig,
            Runnable beforeEdit,
            Runnable commit) {
        PatternBorderConfig.BorderStyle[] styles = PatternBorderConfig.BorderStyle.values();
        String[] labels = new String[styles.length];
        for (int i = 0; i < styles.length; i++) {
            labels[i] = PlotI18n.tr("plugin.pattern.border_style." + styles[i].name().toLowerCase());
        }

        imgui.type.ImInt current = new imgui.type.ImInt(borderConfig.getStyle().ordinal());
        if (ImGui.combo(PlotI18n.tr("plugin.pattern.border_style"), current, labels)) {
            beforeEdit.run();
            borderConfig.setStyle(styles[current.get()]);
            commit.run();
        }
    }
}
