package com.plot.plugin.pattern.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternCapabilities;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternSource;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;

/** 图案设计 Tab：区域 → 来源 → 预设 → 参数 → 边框。 */
public final class PatternDesignPanel {
    private final PatternUiContext ctx;
    private final PatternPresetPanel presetPanel;
    private final PatternBorderPanel borderPanel;

    public PatternDesignPanel(PatternUiContext ctx) {
        this.ctx = ctx;
        this.presetPanel = new PatternPresetPanel(ctx);
        this.borderPanel = new PatternBorderPanel(ctx);
    }

    public void render() {
        PatternUiWidgets.ensurePrimaryFootprintSelected(ctx);
        ctx.selection().retainExisting(ctx.project());
        PatternFootprint footprint = ctx.selection().primary(ctx.project());
        if (footprint == null) {
            renderRegionSection(null);
            return;
        }

        renderRegionSection(footprint);
        ImGui.separator();
        renderSourceSection(footprint);
        ImGui.separator();

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.pattern.preset_section"), ImGuiTreeNodeFlags.DefaultOpen)) {
            presetPanel.renderSection(footprint.getSource());
            ImGui.spacing();
        }

        ImGui.separator();
        Runnable beforeEdit = () -> ctx.projectHistory().push(ctx.project());
        Runnable invalidate = () -> ctx.actions().invalidatePreview();

        if (footprint.getSource() == PatternSource.IMAGE) {
            if (ImGui.collapsingHeader(PlotI18n.tr("plugin.pattern.image_params_section"), ImGuiTreeNodeFlags.DefaultOpen)) {
                renderImageEditor(footprint, beforeEdit, invalidate);
            }
        } else {
            if (ImGui.collapsingHeader(PlotI18n.tr("plugin.pattern.procedural_params_section"), ImGuiTreeNodeFlags.DefaultOpen)) {
                renderProceduralEditor(footprint, beforeEdit, invalidate);
            }
        }

        ImGui.separator();
        borderPanel.renderSection(footprint);
    }

    private void renderRegionSection(PatternFootprint footprint) {
        ImGui.text(PlotI18n.tr("plugin.pattern.section.region"));
        if (footprint == null) {
            PatternUiWidgets.textColoredWrapped(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.select_footprint_hint"));
        }
        PatternUiWidgets.renderFootprintSelector(ctx);
    }

    private void renderSourceSection(PatternFootprint footprint) {
        ImGui.text(PlotI18n.tr("plugin.pattern.section.source"));
        Runnable beforeEdit = () -> ctx.projectHistory().push(ctx.project());
        Runnable invalidate = () -> ctx.actions().invalidatePreview();
        PatternSource previousSource = footprint.getSource();
        PatternUiWidgets.renderSourceCombo(footprint, beforeEdit, invalidate);
        if (footprint.getSource() != previousSource) {
            presetPanel.resetSelection();
        }
        PatternUiWidgets.textColoredWrapped(
            PluginUiColors.HINT_GRAY,
            footprint.getSource() == PatternSource.IMAGE
                ? PlotI18n.tr("plugin.pattern.source.image_hint")
                : PlotI18n.tr("plugin.pattern.source.procedural_hint"));
    }

    private void renderProceduralEditor(
            PatternFootprint footprint,
            Runnable beforeEdit,
            Runnable invalidate) {
        ProceduralPatternConfig pattern = footprint.getPattern();
        PatternCapabilities capabilities = PatternCapabilities.forType(pattern.getType());
        Runnable commitPattern = () -> {
            footprint.setPattern(pattern);
            invalidate.run();
        };

        PatternUiWidgets.renderPatternTypeCombo(pattern, beforeEdit, commitPattern);
        if (pattern.getType() == ProceduralPatternConfig.PatternType.CHECKERBOARD) {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.checkerboard_materials_hint"));
        }
        if (pattern.getType() == ProceduralPatternConfig.PatternType.HERRINGBONE) {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.herringbone_geometry_hint"));
        }
        if (pattern.getType() == ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS) {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.ring_offset_hint"));
        }
        PatternUiWidgets.renderMaterialList(ctx, pattern, beforeEdit, commitPattern);

        if (capabilities.tileSize()) {
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
        }

        if (capabilities.rotation()) {
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

        if (capabilities.offset()) {
            renderOffsetControls(pattern, beforeEdit, commitPattern);
        }

        if (capabilities.density()) {
            renderDensityControl(pattern, beforeEdit, commitPattern);
        }

        if (capabilities.mosaicRatio()) {
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

        if (capabilities.centerOverride()) {
            renderConcentricRingCenter(footprint, pattern, beforeEdit, commitPattern);
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
        Vec2d offset = pattern.getOffset();
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
            pattern.setOffset(new Vec2d(offsetX.get(), offsetZ.get()));
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
            PatternUiWidgets.textColoredWrapped(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.image_missing"));
        }

        String[] matchModeLabels = {
            PlotI18n.tr("plugin.pattern.image_match_auto"),
            PlotI18n.tr("plugin.pattern.image_match_custom")
        };
        ImInt matchMode = new ImInt(imagePattern.getMaterialMatchMode().ordinal());
        if (ImGui.combo(PlotI18n.tr("plugin.pattern.image_match_mode"), matchMode, matchModeLabels)) {
            beforeEdit.run();
            imagePattern.setMaterialMatchMode(
                ImagePatternConfig.MaterialMatchMode.values()[matchMode.get()]);
            commitImage.run();
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

        if (imagePattern.getMaterialMatchMode() == ImagePatternConfig.MaterialMatchMode.CUSTOM) {
            PatternUiWidgets.renderImagePaletteList(imagePattern, beforeEdit, commitImage);
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.image_palette_auto"));
        }
    }
}
