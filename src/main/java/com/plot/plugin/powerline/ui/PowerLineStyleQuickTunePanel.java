package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.EffectiveStylePreview;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.PowerLineQuickTunePolicy;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;
import com.plot.plugin.powerline.style.LinePoleDesignOverrides;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.TowerMaterialApplyMode;
import com.plot.plugin.powerline.style.TowerMaterialOverrideSupport;
import com.plot.plugin.powerline.style.UserPoleDesignTemplateCatalog;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
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

    public void renderCurrentStyleSection(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            boolean includePlacementContext) {
        if (line == null || base == null) {
            return;
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.current"));
        renderSelectedPresetLabel(line, base);
        PowerLineStyleCardRenderer.renderLargeSelectedPreview(line, base, ctx.designResolver());
        if (includePlacementContext) {
            renderPlacementContext(line, base);
        }
    }

    public void renderLineQuickTune(PowerLineFootprint line, PowerLineStylePreset base) {
        if (line == null || base == null) {
            return;
        }
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

    private void renderSelectedPresetLabel(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineUiWidgets.text(UserPoleDesignTemplateCatalog.displayLabel(base));
        if (PowerLineStyleEditor.isModified(line)) {
            ImGui.sameLine();
            PowerLineUiWidgets.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.style.modified_badge"));
        }
    }

    private void renderPlacementContext(PowerLineFootprint line, PowerLineStylePreset base) {
        PowerLineUiPresets.SpacingDensity density = PowerLineSpacingPolicy.effectiveDensity(line);
        String densityLabel = PlotI18n.tr("plugin.powerline.route.spacing." + density.name().toLowerCase());
        double spacing = PowerLineStyleEditor.isSpacingCustomized(line)
            ? line.getMaxPoleSpacing()
            : PowerLineSpacingPolicy.spacingForDensity(line, density);
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.powerline.style.placement_context",
                densityLabel,
                spacing));
        if (PowerLineStyleEditor.isSpacingCustomized(line)) {
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
        PoleDesign effectiveTower = resolveEffectiveTowerDesign(line, base);
        if (TowerMaterialOverrideSupport.supportsTowerMaterialTune(line)) {
            renderTowerMaterialApplyModeRow(line);
        }
        renderPoleMaterialRow(line, base);
        if (TowerMaterialOverrideSupport.supportsTowerMaterialTune(line)
                && line.getTowerMaterialApplyMode() == TowerMaterialApplyMode.LEGS_ONLY) {
            renderBraceMaterialRow(line, effectiveTower);
            renderArmChordMaterialRow(line, effectiveTower);
            renderArmBraceMaterialRow(line, effectiveTower);
        }
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
        renderTopWireMaterialRow(line, base);
        renderSagRow(line);
        endTuneTable();
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
            () -> poleDesignerPanel.openLineInstance(line.getPoleDesignId()));
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

    private PoleDesign resolveEffectiveTowerDesign(PowerLineFootprint line, PowerLineStylePreset base) {
        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(line, base, ctx.designResolver());
        return preview != null ? preview.previewDesign() : null;
    }

    private void renderTowerMaterialApplyModeRow(PowerLineFootprint line) {
        TowerMaterialApplyMode mode = line.getTowerMaterialApplyMode();
        String[] labels = new String[] {
            PlotI18n.tr("plugin.powerline.style.quick_tune.tower_material.legs_only"),
            PlotI18n.tr("plugin.powerline.style.quick_tune.tower_material.sync_all")
        };
        int selected = mode == TowerMaterialApplyMode.SYNC_ALL ? 1 : 0;
        renderBandRow(
            "tower_material_mode",
            PlotI18n.tr("plugin.powerline.style.quick_tune.tower_material.mode"),
            labels,
            selected,
            index -> applyTowerMaterialApplyMode(line, index == 1
                ? TowerMaterialApplyMode.SYNC_ALL
                : TowerMaterialApplyMode.LEGS_ONLY));
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

    private void renderBraceMaterialRow(PowerLineFootprint line, PoleDesign effectiveTower) {
        MaterialMix mix = TowerMaterialOverrideSupport.displayBraceMaterial(effectiveTower, line);
        String value = formatMaterialLabel(mix, TowerStructureDesign.DEFAULT_BRACE_MATERIAL);
        renderValueRow(
            "brace_material",
            PlotI18n.tr("plugin.powerline.style.quick_tune.brace_material"),
            value,
            PlotI18n.tr("plugin.powerline.style.quick_tune.change"),
            () -> openBraceMaterialPicker(line, effectiveTower));
    }

    private void renderArmChordMaterialRow(PowerLineFootprint line, PoleDesign effectiveTower) {
        MaterialMix mix = TowerMaterialOverrideSupport.displayArmChordMaterial(effectiveTower, line);
        String value = formatMaterialLabel(mix, TowerStructureDesign.DEFAULT_ARM_MATERIAL);
        renderValueRow(
            "arm_chord_material",
            PlotI18n.tr("plugin.powerline.style.quick_tune.arm_chord_material"),
            value,
            PlotI18n.tr("plugin.powerline.style.quick_tune.change"),
            () -> openArmChordMaterialPicker(line, effectiveTower));
    }

    private void renderArmBraceMaterialRow(PowerLineFootprint line, PoleDesign effectiveTower) {
        MaterialMix mix = TowerMaterialOverrideSupport.displayArmBraceMaterial(effectiveTower, line);
        String value = formatMaterialLabel(mix, TowerStructureDesign.DEFAULT_BRACE_MATERIAL);
        renderValueRow(
            "arm_brace_material",
            PlotI18n.tr("plugin.powerline.style.quick_tune.arm_brace_material"),
            value,
            PlotI18n.tr("plugin.powerline.style.quick_tune.change"),
            () -> openArmBraceMaterialPicker(line, effectiveTower));
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

    private void renderTopWireMaterialRow(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getTopWireMaterial();
        String value = formatMaterialLabel(mix, "minecraft:chain");
        renderValueRow(
            "top_wire_material",
            PlotI18n.tr("plugin.powerline.style.quick_tune.top_wire"),
            value,
            PlotI18n.tr("plugin.powerline.style.quick_tune.change"),
            () -> openTopWireMaterialPicker(line, base));
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
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.sag.precise_section"),
                ImGuiTreeNodeFlags.None)) {
            renderSagLivePreview(line);
            ImGui.spacing();
            renderSagRatioSlider(line);
            renderMaxSagDepthControls(line);
        }
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
        PowerLineUiWidgets.sliderFloatStable(
            ctx,
            "quick_sag_ratio",
            "plugin.powerline.sag_ratio",
            sagRatio,
            0f,
            (float) (PowerLineUiPresets.ADVANCED_SAG_MAX_RATIO * 100f),
            PowerLineUiFormat.SLIDER_PERCENT,
            value -> {
                PowerLineUiPresets.applyAdvancedSag(line, value / 100f);
                PowerLineStyleEditor.afterStyleEdit(line);
            },
            () -> completeStyleEdit(line));
    }

    private void renderMaxSagDepthControls(PowerLineFootprint line) {
        boolean unlimited = line.isMaxSagDepthUnlimited();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.max_sag_depth_unlimited"), unlimited)) {
            beginProjectBackedStyleEdit(line);
            PowerLineUiPresets.applyMaxSagDepth(
                line,
                PowerLineUiPresets.displayMaxSagDepth(line),
                !unlimited);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
        }
        if (!line.isMaxSagDepthUnlimited()) {
            float[] maxDepth = {PowerLineUiPresets.displayMaxSagDepth(line)};
            PowerLineUiWidgets.sliderFloatStable(
                ctx,
                "quick_max_sag_depth",
                "plugin.powerline.max_sag_depth",
                maxDepth,
                1f,
                PowerLineUiPresets.ADVANCED_MAX_SAG_DEPTH_MAX,
                PowerLineUiFormat.SLIDER,
                value -> {
                    PowerLineUiPresets.applyMaxSagDepth(line, value, false);
                    PowerLineStyleEditor.afterStyleEdit(line);
                },
                () -> completeStyleEdit(line));
        }
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
            beginProjectBackedStyleEdit(line);
            PowerLineStyleEditor.resetToBasePreset(line, ctx.state().getDesignProject());
            completeStyleEdit(line);
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
            pushSegmentButtonStyle(active);
            ImGui.pushID(id + "_" + i);
            if (ImGui.button(options[i], buttonWidth, SEGMENT_HEIGHT)) {
                if (!active) {
                    onSelect.accept(i);
                }
            }
            ImGui.popID();
            popSegmentButtonStyle(active);
        }
    }

    private void pushSegmentButtonStyle(boolean active) {
        if (!active) {
            return;
        }
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonSelectedActive);
    }

    private void popSegmentButtonStyle(boolean active) {
        if (active) {
            ImGui.popStyleColor(3);
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
            pushSegmentButtonStyle(active);
            ImGui.pushID("quick_sag_" + sag.name());
            if (ImGui.button(labels[i], buttonWidth, SEGMENT_HEIGHT)) {
                beginProjectBackedStyleEdit(line);
                PowerLineUiPresets.applySag(line, sag);
                PowerLineStyleEditor.afterStyleEdit(line);
                completeStyleEdit(line);
            }
            ImGui.popID();
            popSegmentButtonStyle(active);
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

    private void applyTowerMaterialApplyMode(PowerLineFootprint line, TowerMaterialApplyMode mode) {
        beginProjectBackedStyleEdit(line);
        line.setTowerMaterialApplyMode(mode);
        PowerLineStyleEditor.afterStyleEdit(line);
        completeStyleEdit(line);
    }

    private void openPoleMaterialPicker(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getPoleMaterial();
        MaterialMix defaults = base != null
            ? base.getPoleMaterial()
            : MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL);
        openMaterialPicker(line, mix, defaults, selected -> {
            line.setPoleMaterial(selected);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
        });
    }

    private void openBraceMaterialPicker(PowerLineFootprint line, PoleDesign effectiveTower) {
        MaterialMix mix = TowerMaterialOverrideSupport.displayBraceMaterial(effectiveTower, line);
        openMaterialPicker(line, mix, mix, selected -> {
            line.setBraceMaterialOverride(selected);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
        });
    }

    private void openArmChordMaterialPicker(PowerLineFootprint line, PoleDesign effectiveTower) {
        MaterialMix mix = TowerMaterialOverrideSupport.displayArmChordMaterial(effectiveTower, line);
        openMaterialPicker(line, mix, mix, selected -> {
            line.setArmChordMaterialOverride(selected);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
        });
    }

    private void openArmBraceMaterialPicker(PowerLineFootprint line, PoleDesign effectiveTower) {
        MaterialMix mix = TowerMaterialOverrideSupport.displayArmBraceMaterial(effectiveTower, line);
        openMaterialPicker(line, mix, mix, selected -> {
            line.setArmBraceMaterialOverride(selected);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
        });
    }

    private void openWireMaterialPicker(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getWireMaterial();
        MaterialMix defaults = base != null
            ? base.getWireMaterial()
            : MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL);
        openMaterialPicker(line, mix, defaults, selected -> {
            line.setWireMaterial(selected);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
        });
    }

    private void openTopWireMaterialPicker(PowerLineFootprint line, PowerLineStylePreset base) {
        MaterialMix mix = line.getTopWireMaterial();
        MaterialMix defaults = MaterialMix.single("minecraft:chain");
        openMaterialPicker(line, mix, defaults, selected -> {
            line.setTopWireMaterial(selected);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
        });
    }

    private void openMaterialPicker(
            PowerLineFootprint line,
            MaterialMix mix,
            MaterialMix defaults,
            java.util.function.Consumer<MaterialMix> onSelected) {
        java.util.List<String> initial = new java.util.ArrayList<>();
        if (mix.getPrimaryMaterial() != null && !mix.getPrimaryMaterial().isBlank()) {
            initial.add(mix.getPrimaryMaterial());
        }
        if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            initial.add(mix.getAccentMaterial());
        }
        UIUtils.openPalettePicker(initial, blockIds -> {
            beginProjectBackedStyleEdit(line);
            onSelected.accept(UIUtils.fromPaletteSelection(blockIds, mix.getAccentRatio(), defaults));
        });
    }

    private void applyPoleHeightBand(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PowerLineQuickTunePolicy.PoleHeightBand band) {
        beginProjectBackedStyleEdit(line);
        if (PowerLineQuickTunePolicy.supportsParametricTune(line)) {
            PowerLineQuickTunePolicy.applyParametricPoleHeightBand(line, base, band);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
            return;
        }
        if (!line.hasPoleDesign()) {
            PowerLineQuickTunePolicy.applyLegacyPoleHeight(line, band);
        } else {
            PoleDesign editable = ensureEditableDesign(line);
            PowerLineQuickTunePolicy.applyPoleHeightBand(editable, base, band);
            ctx.actions().saveLineInstancePoleDesign(line, editable);
        }
        PowerLineStyleEditor.afterStyleEdit(line);
        completeStyleEdit(line);
    }

    private void applyCrossarmBand(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PowerLineQuickTunePolicy.CrossarmWidthBand band) {
        beginProjectBackedStyleEdit(line);
        if (PowerLineQuickTunePolicy.supportsParametricTune(line)) {
            PowerLineQuickTunePolicy.applyParametricCrossarmBand(line, base, band);
            PowerLineStyleEditor.afterStyleEdit(line);
            completeStyleEdit(line);
            return;
        }
        PoleDesign editable = ensureEditableDesign(line);
        PowerLineQuickTunePolicy.applyCrossarmWidthBand(editable, base, band);
        ctx.actions().saveLineInstancePoleDesign(line, editable);
        PowerLineStyleEditor.afterStyleEdit(line);
        completeStyleEdit(line);
    }

    private void beginProjectBackedStyleEdit(PowerLineFootprint line) {
        ctx.pushEditSnapshot();
    }

    private void completeStyleEdit(PowerLineFootprint line) {
        ctx.invalidatePreview();
    }

    private PoleDesign ensureEditableDesign(PowerLineFootprint line) {
        String openId = LinePoleDesignOverrides.resolveOpenDesignId(
            line,
            line.getPoleDesignId(),
            ctx.state().getDesignProject());
        PoleDesign current = ctx.designResolver().find(openId);
        if (current == null) {
            return ctx.designResolver().prepareEditableCopy(line.getPoleDesignId());
        }
        return current.copy();
    }
}
