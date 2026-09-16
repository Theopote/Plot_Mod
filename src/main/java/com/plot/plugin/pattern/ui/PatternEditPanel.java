package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternSource;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImInt;

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

        Runnable commitChange = () -> {
            ctx.projectHistory().push(ctx.project());
            ctx.actions().invalidatePreview();
        };

        PatternUiWidgets.renderSourceCombo(footprint, commitChange);
        ImGui.spacing();

        if (footprint.getSource() == PatternSource.IMAGE) {
            renderImageEditor(footprint, commitChange);
        } else {
            renderProceduralEditor(footprint, commitChange);
        }
    }

    private void renderProceduralEditor(PatternFootprint footprint, Runnable commitChange) {
        ProceduralPatternConfig pattern = footprint.getPattern();
        Runnable commitPattern = () -> {
            footprint.setPattern(pattern);
            commitChange.run();
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

    private void renderImageEditor(PatternFootprint footprint, Runnable commitChange) {
        ImagePatternConfig imagePattern = footprint.getImagePattern();
        Runnable commitImage = () -> {
            footprint.setImagePattern(imagePattern);
            commitChange.run();
        };

        if (ImGui.button(PlotI18n.tr("plugin.pattern.import_image"), 0, 0)) {
            ctx.importImageForFootprint(footprint);
        }
        if (imagePattern.hasImage()) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.pattern.image_info",
                imagePattern.getImageWidth(),
                imagePattern.getImageHeight(),
                imagePattern.getImagePath()));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.image_missing"));
        }

        PatternUiWidgets.renderImageFitModeCombo(imagePattern, commitImage);
        if (imagePattern.getFitMode() == ImagePatternConfig.FitMode.TILE) {
            ImFloat tileScale = new ImFloat((float) imagePattern.getTileScale());
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.pattern.image_tile_scale"),
                    tileScale.getData(),
                    0.25f,
                    8.0f,
                    "%.2f")) {
                imagePattern.setTileScale(tileScale.get());
                commitImage.run();
            }
        }

        ImInt alphaThreshold = new ImInt(imagePattern.getAlphaThreshold());
        if (ImGui.sliderInt(
                PlotI18n.tr("plugin.pattern.image_alpha_threshold"),
                alphaThreshold.getData(),
                0,
                255)) {
            imagePattern.setAlphaThreshold(alphaThreshold.get());
            commitImage.run();
        }

        PatternUiWidgets.renderImagePaletteList(imagePattern, commitImage);
    }
}
