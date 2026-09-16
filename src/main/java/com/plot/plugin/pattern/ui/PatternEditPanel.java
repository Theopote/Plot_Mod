package com.plot.plugin.pattern.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternSource;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
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

        PatternUiWidgets.renderFootprintGeometrySection(ctx, footprint, invalidate);

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
        if (pattern.getType() == ProceduralPatternConfig.PatternType.CHECKERBOARD) {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.checkerboard_materials_hint"));
        }
        PatternUiWidgets.renderMaterialList(ctx, pattern, beforeEdit, commitPattern);

        ImFloat tileSize = new ImFloat((float) pattern.getTileSize());
        boolean tileChanged = ImGui.sliderFloat(
            PlotI18n.tr("plugin.pattern.tile_size"),
            tileSize.getData(),
            0.5f,
            (float) ProceduralPatternConfig.MAX_TILE_SIZE,
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

        if (pattern.getType() == ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS) {
            renderConcentricRingCenter(footprint, pattern, beforeEdit, commitPattern);
        }

        // 添加偏移控制
        renderOffsetControls(pattern, beforeEdit, commitPattern);
        
        // 添加密度控制（适用于新图案类型）
        if (pattern.getType() == ProceduralPatternConfig.PatternType.HEXAGONAL ||
            pattern.getType() == ProceduralPatternConfig.PatternType.DIAMOND ||
            pattern.getType() == ProceduralPatternConfig.PatternType.HERRINGBONE) {
            renderDensityControl(pattern, beforeEdit, commitPattern);
        }
    }

    private void renderConcentricRingCenter(
            PatternFootprint footprint,
            ProceduralPatternConfig pattern,
            Runnable beforeEdit,
            Runnable commitPattern) {
        ImBoolean useCentroid = new ImBoolean(pattern.getCenterOverride() == null);
        if (ImGui.checkbox(PlotI18n.tr("plugin.pattern.ring_use_centroid"), useCentroid)) {
            beforeEdit.run();
            if (useCentroid.get()) {
                pattern.setCenterOverride(null);
            } else {
                pattern.setCenterOverride(footprint.computeCentroid());
            }
            commitPattern.run();
        }
        if (!useCentroid.get()) {
            Vec2d center = pattern.getCenterOverride();
            if (center == null) {
                center = footprint.computeCentroid();
                pattern.setCenterOverride(center);
            }
            ImFloat centerX = new ImFloat((float) center.x);
            ImFloat centerZ = new ImFloat((float) center.y);
            boolean centerChanged = ImGui.inputFloat(
                PlotI18n.tr("plugin.pattern.ring_center_x"),
                centerX,
                0.5f,
                1.0f,
                "%.1f");
            centerChanged |= ImGui.inputFloat(
                PlotI18n.tr("plugin.pattern.ring_center_z"),
                centerZ,
                0.5f,
                1.0f,
                "%.1f");
            if (ImGui.isItemActivated()) {
                beforeEdit.run();
            }
            if (centerChanged) {
                pattern.setCenterOverride(new Vec2d(centerX.get(), centerZ.get()));
                commitPattern.run();
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.ring_center_reset"), 0, 0)) {
                beforeEdit.run();
                pattern.setCenterOverride(null);
                commitPattern.run();
            }
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.ring_center_hint"));
        }
    }

    private void renderOffsetControls(
            ProceduralPatternConfig pattern,
            Runnable beforeEdit,
            Runnable commitPattern) {
        com.plot.api.geometry.Vec2d offset = pattern.getOffset();
        ImFloat offsetX = new ImFloat((float) offset.x);
        ImFloat offsetZ = new ImFloat((float) offset.y);
        
        boolean offsetChanged = ImGui.inputFloat(
            PlotI18n.tr("plugin.pattern.offset_x"),
            offsetX,
            0.1f,
            0.5f,
            "%.1f");
        offsetChanged |= ImGui.inputFloat(
            PlotI18n.tr("plugin.pattern.offset_z"),
            offsetZ,
            0.1f,
            0.5f,
            "%.1f");
        
        if (ImGui.isItemActivated()) {
            beforeEdit.run();
        }
        if (offsetChanged) {
            pattern.setOffset(new com.plot.api.geometry.Vec2d(offsetX.get(), offsetZ.get()));
            commitPattern.run();
        }
    }

    private void renderDensityControl(
            ProceduralPatternConfig pattern,
            Runnable beforeEdit,
            Runnable commitPattern) {
        ImFloat density = new ImFloat((float) pattern.getDensity());
        boolean densityChanged = ImGui.sliderFloat(
            PlotI18n.tr("plugin.pattern.density"),
            density.getData(),
            0.1f,
            3.0f,
            "%.2f");
        
        if (ImGui.isItemActivated()) {
            beforeEdit.run();
        }
        if (densityChanged) {
            pattern.setDensity(density.get());
            commitPattern.run();
        }
        
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.density_hint"));
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
