package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineQuickTunePolicy;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PoleSpacingProfile;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** Base preset 下的 Quick Tune 行式微调 UI。 */
public final class PowerLineStyleQuickTunePanel {
    private static final float ROW_LABEL_WIDTH = 76f;
    private static final float SEGMENT_HEIGHT = 24f;

    private final PowerLineUiContext ctx;
    private final PoleDesignerPanel poleDesignerPanel;

    public PowerLineStyleQuickTunePanel(PowerLineUiContext ctx, PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.poleDesignerPanel = poleDesignerPanel;
    }

    public void render(PowerLineFootprint line, PowerLineStylePreset base) {
        if (line == null || base == null) {
            return;
        }
        ImGui.separator();
        renderHeader(line, base);
        ImGui.spacing();
        renderTowerRow(line);
        if (PowerLineQuickTunePolicy.supportsPoleHeightTune(line)) {
            renderPoleHeightRow(line, base);
        }
        if (PowerLineQuickTunePolicy.supportsCrossarmTune(line, ctx.designResolver())) {
            renderCrossarmRow(line, base);
        }
        renderMaterialRow(line, base);
        renderWiresRow(line, base);
        renderSagRow(line);
        renderSpacingRow(line, base);
        renderMoreTuneSection(line);
        renderFooter(line, base);
    }

    /** 无 base preset 时的精简微调（材质 + 垂度）。 */
    public void renderCustomFallback(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        ImGui.separator();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.style.custom"));
        renderMaterialRow(line, null);
        renderSagRow(line);
        renderSpacingRow(line, null);
    }

    private void renderHeader(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineStyleCardRenderer.renderCompactStylePreview(base);
        ImGui.sameLine();
        ImGui.beginGroup();
        ImGui.text(PlotI18n.tr(base.getLabelKey()));
        if (PowerLineStyleEditor.isModified(line)) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.style.modified_badge"));
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.style.quick_tune_hint"));
        }
        ImGui.endGroup();
    }

    private void renderTowerRow(PowerLineFootprint line) {
        String towerName = resolveTowerLabel(line);
        renderValueRow(
            PlotI18n.tr("plugin.powerline.style.quick_tune.tower"),
            towerName,
            PlotI18n.tr("plugin.powerline.style.quick_tune.customize"),
            () -> poleDesignerPanel.open(line.getPoleDesignId()));
    }

    private void renderPoleHeightRow(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineQuickTunePolicy.PoleHeightBand current =
            PowerLineQuickTunePolicy.detectPoleHeightBand(line, base, ctx.designResolver());
        int selected = current != null ? current.ordinal() : 1;
        renderBandRow(
            "pole_height",
            PlotI18n.tr("plugin.powerline.style.quick_tune.pole_height"),
            new String[] {
                PlotI18n.tr("plugin.powerline.style.quick_tune.small"),
                PlotI18n.tr("plugin.powerline.style.quick_tune.medium"),
                PlotI18n.tr("plugin.powerline.style.quick_tune.tall")
            },
            selected,
            index -> applyPoleHeightBand(line, base, PowerLineQuickTunePolicy.PoleHeightBand.values()[index]));
    }

    private void renderCrossarmRow(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineQuickTunePolicy.CrossarmWidthBand current =
            PowerLineQuickTunePolicy.detectCrossarmWidthBand(line, base, ctx.designResolver());
        int selected = current != null ? current.ordinal() : 1;
        renderBandRow(
            "crossarm",
            PlotI18n.tr("plugin.powerline.style.quick_tune.crossarm"),
            new String[] {
                PlotI18n.tr("plugin.powerline.style.quick_tune.narrow"),
                PlotI18n.tr("plugin.powerline.style.quick_tune.normal"),
                PlotI18n.tr("plugin.powerline.style.quick_tune.wide")
            },
            selected,
            index -> applyCrossarmBand(line, base, PowerLineQuickTunePolicy.CrossarmWidthBand.values()[index]));
    }

    private void renderMaterialRow(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getPoleMaterial();
        String value = UIUtils.getBlockDisplayName(
            mix != null ? mix.getPrimaryMaterial() : PowerLineFootprint.DEFAULT_POLE_MATERIAL);
        if (mix != null && mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            value += " + " + UIUtils.getBlockDisplayName(mix.getAccentMaterial());
        }
        renderValueRow(
            PlotI18n.tr("plugin.powerline.style.quick_tune.material"),
            value,
            PlotI18n.tr("plugin.powerline.style.quick_tune.change"),
            () -> openPoleMaterialPicker(line, base));
    }

    private void renderWiresRow(PowerLineFootprint line, PowerLineStylePreset base) {
        int count = PowerLineQuickTunePolicy.conductorCount(line, base);
        renderValueRow(
            PlotI18n.tr("plugin.powerline.style.quick_tune.wires"),
            PlotI18n.tr("plugin.powerline.style.quick_tune.wire_count", count),
            null,
            null);
    }

    private void renderSagRow(PowerLineFootprint line) {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.style.sag"));
        ImGui.sameLine(ROW_LABEL_WIDTH);
        PowerLineUiPresets.WireSag current = PowerLineUiPresets.detectSag(line);
        int selected = current != null ? current.ordinal() : -1;
        String[] labels = new String[PowerLineUiPresets.WireSag.values().length];
        for (int i = 0; i < labels.length; i++) {
            PowerLineUiPresets.WireSag sag = PowerLineUiPresets.WireSag.values()[i];
            labels[i] = PlotI18n.tr("plugin.powerline.style.sag." + sag.name().toLowerCase());
        }
        renderInlineSagSegments(line, labels, selected);
    }

    private void renderSpacingRow(PowerLineFootprint line, PowerLineStylePreset base) {
        PoleSpacingProfile profile = base != null
            ? base.getSpacingProfile()
            : PowerLineSpacingPolicy.profileFor(line);
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.style.quick_tune.spacing"));
        ImGui.sameLine(ROW_LABEL_WIDTH);
        ImGui.beginGroup();
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.style.quick_tune.spacing_recommended", profile.preferred()));
        float[] spacing = {(float) line.getMaxPoleSpacing()};
        float sliderMax = (float) PowerLineSpacingPolicy.sliderMax(line);
        ImGui.setNextItemWidth(Math.min(160f, ImGui.getContentRegionAvailX() - 8f));
        if (ImGui.sliderFloat("##quick_tune_spacing", spacing, (float) PowerLineFootprint.MIN_CONFIGURABLE_SPACING, sliderMax, "%.0f")) {
            line.setMaxPoleSpacing(spacing[0]);
            PowerLineStyleEditor.afterSpacingEdit(line);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
        if (line.isSpacingCustomized()
                && PowerLineSpacingPolicy.differsFromStyleRecommendation(line, base != null ? base : PowerLineStyleEditor.basePreset(line))) {
            if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.style.apply_recommended_spacing"))) {
                ctx.pushEditSnapshot();
                PowerLineSpacingPolicy.applyStyleDefaultSpacing(line, profile);
                PowerLineStyleEditor.afterSpacingAdopted(line);
                ctx.invalidatePreview();
            }
        }
        ImGui.endGroup();
    }

    private void renderMoreTuneSection(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.quick_tune.more"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "quick_wire_material",
            PlotI18n.tr("plugin.powerline.wire_material"),
            line.getWireMaterial(),
            MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL),
            mix -> {
                line.setWireMaterial(mix);
                PowerLineStyleEditor.afterStyleEdit(line);
                ctx.invalidatePreview();
            });
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "quick_ground_wire_material",
            PlotI18n.tr("plugin.powerline.ground_wire_material"),
            line.getGroundWireMaterial(),
            MaterialMix.single("minecraft:chain"),
            mix -> {
                line.setGroundWireMaterial(mix);
                PowerLineStyleEditor.afterStyleEdit(line);
                ctx.invalidatePreview();
            });
    }

    private void renderFooter(PowerLineFootprint line, PowerLineStylePreset base) {
        if (!PowerLineStyleEditor.isModified(line)) {
            return;
        }
        ImGui.separator();
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.powerline.style.modified_count",
                PowerLineStyleEditor.modifiedSettingCount(line)));
        if (ImGui.button(PlotI18n.tr(
                "plugin.powerline.style.reset_to_preset",
                PlotI18n.tr(base.getLabelKey())),
                0,
                0)) {
            ctx.pushEditSnapshot();
            PowerLineStyleEditor.resetToBasePreset(line);
            ctx.invalidatePreview();
        }
    }

    private void renderValueRow(String label, String value, String actionLabel, Runnable action) {
        ImGui.textColored(PluginUiColors.HINT_GRAY, label);
        ImGui.sameLine(ROW_LABEL_WIDTH);
        ImGui.text(value != null ? value : "-");
        if (actionLabel != null && action != null) {
            ImGui.sameLine();
            float actionWidth = ImGui.calcTextSize(actionLabel).x + ImGui.getStyle().getFramePaddingX() * 2f;
            ImGui.setCursorPosX(ImGui.getCursorStartPos().x + ImGui.getContentRegionAvail().x - actionWidth);
            if (ImGui.smallButton(actionLabel + "##" + label)) {
                action.run();
            }
        }
    }

    private void renderBandRow(
            String id,
            String label,
            String[] options,
            int selected,
            java.util.function.IntConsumer onSelect) {
        ImGui.textColored(PluginUiColors.HINT_GRAY, label);
        ImGui.sameLine(ROW_LABEL_WIDTH);
        renderSegmentButtons(id, options, selected, onSelect);
    }

    private void renderSegmentButtons(
            String id,
            String[] options,
            int selected,
            java.util.function.IntConsumer onSelect) {
        for (int i = 0; i < options.length; i++) {
            if (i > 0) {
                ImGui.sameLine(0f, 4f);
            }
            boolean active = i == selected;
            if (active) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF37474F);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF455A64);
            }
            ImGui.pushID(id + i);
            if (ImGui.button(options[i], 0, SEGMENT_HEIGHT)) {
                if (!active) {
                    onSelect.accept(i);
                }
            }
            ImGui.popID();
            if (active) {
                ImGui.popStyleColor(2);
            }
        }
    }

    private void renderInlineSagSegments(PowerLineFootprint line, String[] labels, int selected) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float spacing = 4f;
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                ImGui.sameLine(0f, spacing);
            }
            PowerLineUiPresets.WireSag sag = PowerLineUiPresets.WireSag.values()[i];
            boolean active = i == selected;
            if (active) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF37474F);
            }
            ImGui.pushID("quick_sag_" + sag.name());
            if (ImGui.button(labels[i], 0, SEGMENT_HEIGHT)) {
                ctx.pushEditSnapshot();
                PowerLineUiPresets.applySag(line, sag);
                PowerLineStyleEditor.afterStyleEdit(line);
                ctx.invalidatePreview();
            }
            ImGui.popID();
            if (active) {
                ImGui.popStyleColor();
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                float previewW = ImGui.getFontSize() * 6f;
                float previewH = ImGui.getFontSize() * 3f;
                ImGui.text(labels[i]);
                var origin = ImGui.getCursorScreenPos();
                drawList.addRectFilled(
                    origin.x,
                    origin.y,
                    origin.x + previewW,
                    origin.y + previewH,
                    0xFF141414);
                PowerLineSagCardRenderer.drawSagPreview(
                    drawList,
                    sag.ratio(),
                    com.plot.plugin.powerline.PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH,
                    origin.x + 2f,
                    origin.y + 2f,
                    origin.x + previewW - 2f,
                    origin.y + previewH - 2f);
                ImGui.dummy(previewW, previewH);
                ImGui.endTooltip();
            }
        }
        if (selected < 0) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.powerline.build.sag_custom",
                    (int) Math.round(line.getSagRatio() * 100.0)));
        }
    }

    private String resolveTowerLabel(PowerLineFootprint line) {
        if (line.hasTowerFamily()) {
            TowerFamily family = TowerFamilyCatalog.findBuiltin(line.getTowerFamilyId());
            if (family != null) {
                String prefix = TowerFamilyCatalog.isBuiltinId(family.getId())
                    ? PlotI18n.tr("plugin.powerline.tower_family_builtin_prefix")
                    : "";
                return prefix + family.getName();
            }
            return line.getTowerFamilyId();
        }
        if (line.hasPoleDesign()) {
            PoleDesign design = ctx.designResolver().find(line.getPoleDesignId());
            if (design != null) {
                String prefix = PoleDesignCatalog.isBuiltinId(design.getId())
                    ? PlotI18n.tr("plugin.powerline.pole_design_builtin_prefix")
                    : "";
                return prefix + design.getName();
            }
        }
        return PlotI18n.tr("plugin.powerline.pole_design_default");
    }

    private void openPoleMaterialPicker(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getPoleMaterial();
        MaterialMix defaults = base != null ? base.getPoleMaterial() : MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL);
        java.util.List<String> initial = new java.util.ArrayList<>();
        if (mix.getPrimaryMaterial() != null && !mix.getPrimaryMaterial().isBlank()) {
            initial.add(mix.getPrimaryMaterial());
        }
        if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            initial.add(mix.getAccentMaterial());
        }
        UIUtils.openPalettePicker(initial, blockIds -> {
            ctx.pushEditSnapshot();
            line.setPoleMaterial(UIUtils.fromPaletteSelection(blockIds, mix.getAccentRatio(), defaults));
            PowerLineStyleEditor.afterStyleEdit(line);
            ctx.invalidatePreview();
        });
    }

    private void applyPoleHeightBand(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PowerLineQuickTunePolicy.PoleHeightBand band) {
        ctx.pushEditSnapshot();
        if (!line.hasPoleDesign()) {
            PowerLineQuickTunePolicy.applyLegacyPoleHeight(line, band);
        } else {
            boolean wasBuiltin = PoleDesignCatalog.isBuiltinId(line.getPoleDesignId());
            PoleDesign editable = ensureEditableDesign(line);
            PowerLineQuickTunePolicy.applyPoleHeightBand(editable, base, band);
            ctx.actions().savePoleDesign(editable);
            if (wasBuiltin) {
                line.setPoleDesignId(editable.getId());
            }
        }
        PowerLineStyleEditor.afterStyleEdit(line);
        ctx.invalidatePreview();
    }

    private void applyCrossarmBand(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PowerLineQuickTunePolicy.CrossarmWidthBand band) {
        ctx.pushEditSnapshot();
        boolean wasBuiltin = PoleDesignCatalog.isBuiltinId(line.getPoleDesignId());
        PoleDesign editable = ensureEditableDesign(line);
        PowerLineQuickTunePolicy.applyCrossarmWidthBand(editable, base, band);
        ctx.actions().savePoleDesign(editable);
        if (wasBuiltin) {
            line.setPoleDesignId(editable.getId());
        }
        PowerLineStyleEditor.afterStyleEdit(line);
        ctx.invalidatePreview();
    }

    private PoleDesign ensureEditableDesign(PowerLineFootprint line) {
        String id = line.getPoleDesignId();
        PoleDesignResolver resolver = ctx.designResolver();
        PoleDesign current = resolver.find(id);
        if (current == null) {
            return new PoleDesign(PlotI18n.tr("plugin.powerline.pole_design_default"));
        }
        if (!PoleDesignCatalog.isBuiltinId(id) && resolver.userDesigns().getDesign(id) != null) {
            return current.copy();
        }
        PoleDesign fork = new PoleDesign(current.getName());
        fork.setLayers(current.getLayers());
        fork.setAttachments(current.getAttachments());
        fork.setTowerStructure(current.getTowerStructure());
        fork.setEngineeringMetadata(current.getEngineeringMetadata());
        return fork;
    }
}
