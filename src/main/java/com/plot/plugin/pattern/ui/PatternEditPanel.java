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
        PatternUiWidgets.ensurePrimaryFootprintSelected(ctx);
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

        Runnable beforeEdit = () -> ctx.projectHistory().push(ctx.project());
        Runnable invalidate = () -> ctx.actions().invalidatePreview();

        PatternUiWidgets.renderSourceCombo(footprint, beforeEdit, invalidate);
        ImGui.spacing();

        if (footprint.getSource() == PatternSource.IMAGE) {
            renderImageEditor(footprint, beforeEdit, invalidate);
        } else {
            renderProceduralEditor(footprint, beforeEdit, invalidate);
        }
    }

    private void renderProceduralEditor(
            PatternFootprint footprint,
            Runnable beforeEdit,
            Runnable invalidate) {
        ProceduralPatternConfig pattern = footprint.getPattern();
        Runnable commitPattern = () -> {
            footprint.setPattern(pattern);
            invalidate.run();
        };

        PatternUiWidgets.renderPatternTypeCombo(pattern, beforeEdit, commitPattern);
        PatternUiWidgets.renderMaterialList(ctx, pattern, beforeEdit, commitPattern);

        ImFloat tileSize = new ImFloat((float) pattern.getTileSize());
        boolean tileChanged = ImGui.sliderFloat(
            PlotI18n.tr("plugin.pattern.tile_size"),
            tileSize.getData(),
            0.5f,
            16.0f,
            "%.1f");
        if (ImGui.isItemActivated()) {
            beforeEdit.run();
        }
        if (tileChanged) {
            pattern.setTileSize(tileSize.get());
            commitPattern.run();
        }

        if (pattern.getType() == ProceduralPatternConfig.PatternType.STRIPES) {
            ImFloat angle = new ImFloat((float) pattern.getAngleDegrees());
            boolean angleChanged = ImGui.sliderFloat(
                PlotI18n.tr("plugin.pattern.angle_degrees"),
                angle.getData(),
                0.0f,
                180.0f,
                "%.0f°");
            if (ImGui.isItemActivated()) {
                beforeEdit.run();
            }
            if (angleChanged) {
                pattern.setAngleDegrees(angle.get());
                commitPattern.run();
            }
        }

        if (pattern.getType() == ProceduralPatternConfig.PatternType.MOSAIC) {
            ImFloat ratio = new ImFloat((float) pattern.getMosaicPrimaryRatio());
            boolean ratioChanged = ImGui.sliderFloat(
                PlotI18n.tr("plugin.pattern.mosaic_primary_ratio"),
                ratio.getData(),
                0.2f,
                0.9f,
                "%.2f");
            if (ImGui.isItemActivated()) {
                beforeEdit.run();
            }
            if (ratioChanged) {
                pattern.setMosaicPrimaryRatio(ratio.get());
                commitPattern.run();
            }
        }
    }

    private void renderImageEditor(
            PatternFootprint footprint,
            Runnable beforeEdit,
            Runnable invalidate) {
        ImagePatternConfig imagePattern = footprint.getImagePattern();
        Runnable commitImage = () -> {
            footprint.setImagePattern(imagePattern);
            invalidate.run();
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

        PatternUiWidgets.renderImageFitModeCombo(imagePattern, beforeEdit, commitImage);
        if (imagePattern.getFitMode() == ImagePatternConfig.FitMode.TILE) {
            ImFloat tileScale = new ImFloat((float) imagePattern.getTileScale());
            boolean tileScaleChanged = ImGui.sliderFloat(
                PlotI18n.tr("plugin.pattern.image_tile_scale"),
                tileScale.getData(),
                0.25f,
                8.0f,
                "%.2f");
            if (ImGui.isItemActivated()) {
                beforeEdit.run();
            }
            if (tileScaleChanged) {
                imagePattern.setTileScale(tileScale.get());
                commitImage.run();
            }
        }

        ImInt alphaThreshold = new ImInt(imagePattern.getAlphaThreshold());
        boolean alphaChanged = ImGui.sliderInt(
            PlotI18n.tr("plugin.pattern.image_alpha_threshold"),
            alphaThreshold.getData(),
            0,
            255);
        if (ImGui.isItemActivated()) {
            beforeEdit.run();
        }
        if (alphaChanged) {
            imagePattern.setAlphaThreshold(alphaThreshold.get());
            commitImage.run();
        }

        PatternUiWidgets.renderImagePaletteList(imagePattern, beforeEdit, commitImage);
    }
}
