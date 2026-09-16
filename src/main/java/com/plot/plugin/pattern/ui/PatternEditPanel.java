package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImFloat;

/** 图案编辑 Tab。 */
public final class PatternEditPanel {
    private final PatternUiContext ctx;

    public PatternEditPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PatternFootprint footprint = ctx.selection().primary(ctx.project());
        if (footprint == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.select_footprint_hint"));
            PatternUiWidgets.renderFootprintSelector(ctx);
            return;
        }

        PatternUiWidgets.renderSelectionSummary(ctx);
        PatternUiWidgets.renderFootprintSelector(ctx);
        ImGui.spacing();

        if (ctx.footprintNameEditingId().equals(footprint.getId())) {
            if (ImGui.inputText(PlotI18n.tr("plugin.pattern.footprint_name"), ctx.footprintNameBuffer())) {
                footprint.setName(ctx.footprintNameBuffer().get());
            }
        } else {
            ctx.footprintNameBuffer().set(footprint.getName());
            if (ImGui.button(PlotI18n.tr("plugin.pattern.edit_name"), 0, 0)) {
                ctx.setFootprintNameEditingId(footprint.getId());
            }
        }

        ProceduralPatternConfig pattern = footprint.getPattern();
        Runnable commitPattern = () -> {
            footprint.setPattern(pattern);
            ctx.projectHistory().push(ctx.project());
            ctx.actions().invalidatePreview();
        };

        PatternUiWidgets.renderPatternTypeCombo(pattern, type -> commitPattern.run());
        PatternUiWidgets.renderMaterialList(ctx, pattern, commitPattern);

        ImFloat tileSize = new ImFloat((float) pattern.getTileSize());
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.pattern.tile_size"),
                tileSize.getData(),
                0.5f,
                16.0f,
                "%.1f")) {
            pattern.setTileSize(tileSize.get());
            commitPattern.run();
        }

        if (pattern.getType() == ProceduralPatternConfig.PatternType.STRIPES) {
            ImFloat angle = new ImFloat((float) pattern.getAngleDegrees());
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.pattern.angle_degrees"),
                    angle.getData(),
                    0.0f,
                    180.0f,
                    "%.0f°")) {
                pattern.setAngleDegrees(angle.get());
                commitPattern.run();
            }
        }

        if (pattern.getType() == ProceduralPatternConfig.PatternType.MOSAIC) {
            ImFloat ratio = new ImFloat((float) pattern.getMosaicPrimaryRatio());
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.pattern.mosaic_primary_ratio"),
                    ratio.getData(),
                    0.2f,
                    0.9f,
                    "%.2f")) {
                pattern.setMosaicPrimaryRatio(ratio.get());
                commitPattern.run();
            }
        }
    }
}
