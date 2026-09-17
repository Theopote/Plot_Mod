package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternBorderConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;

/** 图案边框 v1：实线边缘（嵌入图案页）。 */
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
        Runnable beforeEdit = () -> ctx.pushProjectHistory();
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

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.border_v1_hint"));
        ImGui.spacing();

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

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.pattern.border_material"));
        String currentMaterial = borderConfig.getBorderMaterial();
        if (ImGui.button(UIUtils.getBlockDisplayName(currentMaterial) + "##border_mat", 0, 0)) {
            UIUtils.openBlockPicker(currentMaterial, blockId -> {
                if (blockId.equals(currentMaterial)) {
                    return;
                }
                beforeEdit.run();
                borderConfig.setBorderMaterial(blockId);
                commit.run();
            });
        }
    }
}
