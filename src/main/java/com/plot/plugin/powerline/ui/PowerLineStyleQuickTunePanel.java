package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineQuickTunePolicy;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;

/** Base preset 下的 Quick Customize（Tower / Wires 分区）。 */
public final class PowerLineStyleQuickTunePanel {
    private static final float SEGMENT_HEIGHT = 24f;
    private static final float SAG_PREVIEW_HEIGHT = 56f;
    private static final float LABEL_COLUMN_WIDTH = 76f;
    private static final float ACTION_BUTTON_GAP = 8f;
    private static final int TUNE_TABLE_COLUMNS = 2;

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
        renderSelectedHeader(line, base);
        PowerLineStyleCardRenderer.renderLargeSelectedPreview(line, base, ctx.designResolver());
        renderPlacementContext(line, base);
        ImGui.spacing();
        renderTowerSection(line, base);
        ImGui.spacing();
        renderWiresSection(line, base);
        renderFooter(line, base);
    }

    public void renderCustomFallback(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        ImGui.separator();
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.style.custom"));
        renderWiresSection(line, null);
    }

    private void renderSelectedHeader(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.selected"));
        ImGui.sameLine();
        PowerLineUiWidgets.text(PlotI18n.tr(base.getLabelKey()));
        if (PowerLineStyleEditor.isModified(line)) {
            ImGui.sameLine();
            PowerLineUiWidgets.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.style.modified_badge"));
        }
    }

    private void renderPlacementContext(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineUiPresets.SpacingDensity density = PowerLineSpacingPolicy.effectiveDensity(line);
        String densityLabel = PlotI18n.tr("plugin.powerline.route.spacing." + density.name().toLowerCase());
        double spacing = line.isSpacingCustomized()
            ? line.getMaxPoleSpacing()
            : PowerLineSpacingPolicy.spacingForDensity(line, density);
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.powerline.style.placement_context",
                densityLabel,
                spacing));
        if (line.isSpacingCustomized()) {
            ImGui.sameLine();
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                "(" + PlotI18n.tr("plugin.powerline.style.spacing_customized_hint") + ")");
        }
    }

    private void renderTowerSection(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.tower_tune"));
        ImGui.separator();
        if (!beginTuneTable("tower")) {
            return;
        }
        renderTowerStyleRow(line);
        if (PowerLineQuickTunePolicy.supportsPoleHeightTune(line, ctx.designResolver())) {
            renderPoleHeightRow(line, base);
        }
        if (PowerLineQuickTunePolicy.supportsCrossarmTune(line, ctx.designResolver())) {
            renderCrossarmRow(line, base);
        }
        renderPoleMaterialRow(line, base);
        endTuneTable();
    }

    private void renderWiresSection(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.wires_tune"));
        ImGui.separator();
        if (!beginTuneTable("wires")) {
            return;
        }
        renderWireLayoutRow(line, base);
        renderWireMaterialRow(line, base);
        renderSagRow(line);
        endTuneTable();
        renderTopWireInAdvanced(line);
    }

    private boolean beginTuneTable(String sectionId) {
        int flags = ImGuiTableFlags.SizingStretchProp
            | ImGuiTableFlags.RowBg
            | ImGuiTableFlags.PadOuterX;
        if (!ImGui.beginTable("quick_tune_" + sectionId, TUNE_TABLE_COLUMNS, flags)) {
            return false;
        }
        ImGui.tableSetupColumn("##label", ImGuiTableColumnFlags.WidthFixed, LABEL_COLUMN_WIDTH);
        ImGui.tableSetupColumn("##content", ImGuiTableColumnFlags.WidthStretch, 1f);
        return true;
    }

    private void endTuneTable() {
        ImGui.endTable();
    }

    private void renderTowerStyleRow(PowerLineFootprint line) {
        if (line.hasTowerFamily()) {
            renderValueRow(
                "tower_style",
                PlotI18n.tr("plugin.powerline.style.quick_tune.tower_style"),
                resolveTowerLabel(line),
                PlotI18n.tr("plugin.powerline.style.quick_tune.customize_family"),
                () -> poleDesignerPanel.requestCustomizeFamily(line.getTowerFamilyId()));
            return;
        }
        renderValueRow(
            "tower_style",
            PlotI18n.tr("plugin.powerline.style.quick_tune.tower_style"),
            resolveTowerLabel(line),
            PlotI18n.tr("plugin.powerline.style.quick_tune.edit_tower"),
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

    private void renderPoleMaterialRow(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getPoleMaterial();
        String value = formatMaterialLabel(mix, PowerLineFootprint.DEFAULT_POLE_MATERIAL);
        renderValueRow(
            "pole_material",
            PlotI18n.tr("plugin.powerline.style.quick_tune.pole_material"),
            value,
            PlotI18n.tr("plugin.powerline.style.quick_tune.change"),
            () -> openPoleMaterialPicker(line, base));
    }

    private void renderWireLayoutRow(PowerLineFootprint line, PowerLineStylePreset base) {
        int count = PowerLineQuickTunePolicy.conductorCount(line, base);
        renderValueRow(
            "wire_layout",
            PlotI18n.tr("plugin.powerline.style.quick_tune.wire_layout"),
            PlotI18n.tr("plugin.powerline.style.quick_tune.wire_count", count),
            null,
            null);
    }

    private void renderWireMaterialRow(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getWireMaterial();
        String value = formatMaterialLabel(mix, PowerLineFootprint.DEFAULT_WIRE_MATERIAL);
        renderValueRow(
            "wire_material",
            PlotI18n.tr("plugin.powerline.wire_material"),
            value,
            PlotI18n.tr("plugin.powerline.style.quick_tune.change"),
            () -> openWireMaterialPicker(line, base));
    }

    private void renderSagRow(PowerLineFootprint line) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.style.sag"));
        ImGui.tableNextColumn();
        PowerLineUiPresets.WireSag current = PowerLineUiPresets.detectSag(line);
        int selected = current != null ? current.ordinal() : -1;
        String[] labels = new String[PowerLineUiPresets.WireSag.values().length];
        for (int i = 0; i < labels.length; i++) {
            PowerLineUiPresets.WireSag sag = PowerLineUiPresets.WireSag.values()[i];
            labels[i] = PlotI18n.tr("plugin.powerline.style.sag." + sag.name().toLowerCase());
        }
        renderInlineSagSegments(line, labels, selected);
        ImGui.spacing();
        renderSagLivePreview(line);
        ImGui.spacing();
        renderSagRatioSlider(line);
        renderMaxSagDepthControls(line);
    }

    private void renderSagLivePreview(PowerLineFootprint line) {
        float previewWidth = ImGui.getContentRegionAvail().x;
        if (previewWidth < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(
            origin.x,
            origin.y,
            origin.x + previewWidth,
            origin.y + SAG_PREVIEW_HEIGHT,
            PluginUiColors.PANEL_BG_DARK);
        drawList.addRect(
            origin.x,
            origin.y,
            origin.x + previewWidth,
            origin.y + SAG_PREVIEW_HEIGHT,
            PluginUiColors.PANEL_BORDER);
        double maxDepth = line.isMaxSagDepthUnlimited()
            ? PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH
            : line.getMaxSagDepth();
        PowerLineSagCardRenderer.drawSagPreview(
            drawList,
            line.getSagRatio(),
            maxDepth,
            origin.x + 6f,
            origin.y + 4f,
            origin.x + previewWidth - 6f,
            origin.y + SAG_PREVIEW_HEIGHT - 4f);
        ImGui.dummy(previewWidth, SAG_PREVIEW_HEIGHT);
    }

    private void renderSagRatioSlider(PowerLineFootprint line) {
        float[] sagRatio = {(float) (line.getSagRatio() * 100f)};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "quick_sag_ratio",
            "plugin.powerline.sag_ratio",
            sagRatio,
            0f,
            (float) (PowerLineUiPresets.ADVANCED_SAG_MAX_RATIO * 100f),
            "%.0f%%",
            value -> {
                PowerLineUiPresets.applyAdvancedSag(line, value / 100f);
                PowerLineStyleEditor.afterStyleEdit(line);
            });
    }

    private void renderMaxSagDepthControls(PowerLineFootprint line) {
        boolean unlimited = line.isMaxSagDepthUnlimited();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.max_sag_depth_unlimited"), unlimited)) {
            ctx.pushEditSnapshot();
            PowerLineUiPresets.applyMaxSagDepth(
                line,
                PowerLineUiPresets.displayMaxSagDepth(line),
                !unlimited);
            PowerLineStyleEditor.afterStyleEdit(line);
            ctx.invalidatePreview();
        }
        if (!line.isMaxSagDepthUnlimited()) {
            float[] maxDepth = {PowerLineUiPresets.displayMaxSagDepth(line)};
            PowerLineUiWidgets.sliderFloatStableLineEdit(
                ctx,
                "quick_max_sag_depth",
                "plugin.powerline.max_sag_depth",
                maxDepth,
                1f,
                PowerLineUiPresets.ADVANCED_MAX_SAG_DEPTH_MAX,
                "%.0f",
                value -> {
                    PowerLineUiPresets.applyMaxSagDepth(line, value, false);
                    PowerLineStyleEditor.afterStyleEdit(line);
                });
        }
    }

    private void renderTopWireInAdvanced(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.quick_tune.top_wire"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        PowerLineUiWidgets.renderMaterialMixPicker(
            ctx,
            "quick_top_wire_material",
            PlotI18n.tr("plugin.powerline.top_wire_material"),
            line.getTopWireMaterial(),
            MaterialMix.single("minecraft:chain"),
            mix -> {
                line.setTopWireMaterial(mix);
                PowerLineStyleEditor.afterStyleEdit(line);
                ctx.invalidatePreview();
            });
    }

    private void renderFooter(PowerLineFootprint line, PowerLineStylePreset base) {
        if (!PowerLineStyleEditor.isModified(line)) {
            return;
        }
        ImGui.separator();
        PowerLineUiWidgets.textColored(
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

    private static String formatMaterialLabel(MaterialMix mix, String fallbackId) {
        String primary = mix != null ? mix.getPrimaryMaterial() : fallbackId;
        String value = UIUtils.getBlockDisplayName(primary);
        if (mix != null && mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            value += " + " + UIUtils.getBlockDisplayName(mix.getAccentMaterial());
        }
        return value;
    }

    private void renderValueRow(
            String rowId,
            String label,
            String value,
            String actionLabel,
            Runnable action) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, label);
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        if (actionLabel != null && action != null) {
            float contentStartX = ImGui.getCursorPosX();
            float contentStartY = ImGui.getCursorPosY();
            float contentWidth = ImGui.getContentRegionAvail().x;
            float buttonWidth = actionButtonWidth(actionLabel);
            float textWidth = Math.max(0f, contentWidth - buttonWidth - ACTION_BUTTON_GAP);
            ImGui.pushTextWrapPos(contentStartX + textWidth);
            PowerLineUiWidgets.text(value != null ? value : "-");
            ImGui.popTextWrapPos();
            ImGui.setCursorPos(contentStartX + contentWidth - buttonWidth, contentStartY);
            if (ImGui.smallButton(actionLabel + "##" + rowId)) {
                action.run();
            }
        } else {
            PowerLineUiWidgets.text(value != null ? value : "-");
        }
    }

    private static float actionButtonWidth(String actionLabel) {
        return ImGui.calcTextSize(actionLabel).x + ImGui.getStyle().getFramePaddingX() * 2f;
    }

    private void renderBandRow(
            String rowId,
            String label,
            String[] options,
            int selected,
            java.util.function.IntConsumer onSelect) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, label);
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        renderSegmentButtons(rowId, options, selected, onSelect);
    }

    private void renderSegmentButtons(
            String id,
            String[] options,
            int selected,
            java.util.function.IntConsumer onSelect) {
        if (options == null || options.length == 0) {
            return;
        }
        float spacing = 4f;
        float avail = ImGui.getContentRegionAvail().x;
        float totalSpacing = spacing * (options.length - 1);
        float buttonWidth = Math.max(32f, (avail - totalSpacing) / options.length);
        for (int i = 0; i < options.length; i++) {
            if (i > 0) {
                ImGui.sameLine(0f, spacing);
            }
            boolean active = i == selected;
            if (active) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF37474F);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF455A64);
            }
            ImGui.pushID(id + "_" + i);
            if (ImGui.button(options[i], buttonWidth, SEGMENT_HEIGHT)) {
                if (!active) {
                    ctx.pushEditSnapshot();
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
        if (labels == null || labels.length == 0) {
            return;
        }
        float spacing = 4f;
        float avail = ImGui.getContentRegionAvail().x;
        float totalSpacing = spacing * (labels.length - 1);
        float buttonWidth = Math.max(32f, (avail - totalSpacing) / labels.length);
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
            if (ImGui.button(labels[i], buttonWidth, SEGMENT_HEIGHT)) {
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
                ImGui.setTooltip(labels[i]);
            }
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
        MaterialMix defaults = base != null
            ? base.getPoleMaterial()
            : MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL);
        openMaterialPicker(mix, defaults, selected -> {
            line.setPoleMaterial(selected);
            PowerLineStyleEditor.afterStyleEdit(line);
            ctx.invalidatePreview();
        });
    }

    private void openWireMaterialPicker(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getWireMaterial();
        MaterialMix defaults = base != null
            ? base.getWireMaterial()
            : MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL);
        openMaterialPicker(mix, defaults, selected -> {
            line.setWireMaterial(selected);
            PowerLineStyleEditor.afterStyleEdit(line);
            ctx.invalidatePreview();
        });
    }

    private void openMaterialPicker(MaterialMix mix, MaterialMix defaults, java.util.function.Consumer<MaterialMix> onSelected) {
        java.util.List<String> initial = new java.util.ArrayList<>();
        if (mix.getPrimaryMaterial() != null && !mix.getPrimaryMaterial().isBlank()) {
            initial.add(mix.getPrimaryMaterial());
        }
        if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            initial.add(mix.getAccentMaterial());
        }
        UIUtils.openPalettePicker(initial, blockIds -> {
            ctx.pushEditSnapshot();
            onSelected.accept(UIUtils.fromPaletteSelection(blockIds, mix.getAccentRatio(), defaults));
        });
    }

    private void applyPoleHeightBand(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PowerLineQuickTunePolicy.PoleHeightBand band) {
        if (PowerLineQuickTunePolicy.supportsParametricTune(line)) {
            ctx.pushEditSnapshot();
            PowerLineQuickTunePolicy.applyParametricPoleHeightBand(line, base, band);
            PowerLineStyleEditor.afterStyleEdit(line);
            ctx.invalidatePreview();
            return;
        }
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
        if (PowerLineQuickTunePolicy.supportsParametricTune(line)) {
            ctx.pushEditSnapshot();
            PowerLineQuickTunePolicy.applyParametricCrossarmBand(line, base, band);
            PowerLineStyleEditor.afterStyleEdit(line);
            ctx.invalidatePreview();
            return;
        }
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
        return ctx.designResolver().prepareEditableCopy(line.getPoleDesignId());
    }
}
