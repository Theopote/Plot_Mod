package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelPreset;
import com.plot.plugin.road.tunnel.TunnelPresetCatalog;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * Full tunnel style editor body used by {@link TunnelStyleFloatingEditor}.
 */
public final class TunnelStyleEditor {
    private TunnelStyleEditor() {
    }

    /** Session-scoped preset selection for the open floating editor. */
    public static final class PresetSelection {
        private TunnelPreset applied;

        public TunnelPreset applied() {
            return applied;
        }

        public void apply(TunnelPreset preset) {
            this.applied = preset;
        }

        public void clear() {
            this.applied = null;
        }

        /** Keep the badge only while the draft still fully matches the applied preset. */
        public void syncWithStyle(TunnelStyle style) {
            if (applied != null && !TunnelPresetCatalog.matches(applied, style)) {
                applied = null;
            }
        }
    }

    public static void render(RoadUiContext ctx, TunnelStyle style, Runnable onChanged) {
        render(ctx, style, onChanged, null);
    }

    public static void render(
            RoadUiContext ctx,
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection) {
        if (style == null) {
            return;
        }
        ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.editor_hint"));
        ImGui.spacing();
        renderStylePresetCombo(style, onChanged, presetSelection);
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.tunnel.cross_section_section"));
        ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.shape_presets_hint"));
        ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.shape_switch_keeps_style"));
        if (style.getShape() == TunnelShape.CIRCULAR) {
            ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.circular_constraint_hint"));
        }
        if (TunnelCrossSectionPreviewRenderer.renderShapePresetRow(style.getShape(), shape -> {
            style.setShape(shape);
            notifyChanged(style, onChanged, presetSelection);
        })) {
            // selection handled in callback
        }
        ImGui.spacing();
        TunnelCrossSectionPreviewRenderer.renderLivePreview(
            style.getShape(),
            style.getClearHeight(),
            style.getSideClearance(),
            style.getLiningThickness());
        ImGui.spacing();

        int[] clearHeight = {style.getClearHeight()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.clear_height"), clearHeight,
                TunnelStyle.MIN_CLEAR_HEIGHT, TunnelStyle.MAX_CLEAR_HEIGHT)) {
            style.setClearHeight(clearHeight[0]);
            notifyChanged(style, onChanged, presetSelection);
        }
        int[] sideClearance = {style.getSideClearance()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.side_clearance"), sideClearance,
                TunnelStyle.MIN_SIDE_CLEARANCE, TunnelStyle.MAX_SIDE_CLEARANCE)) {
            style.setSideClearance(sideClearance[0]);
            notifyChanged(style, onChanged, presetSelection);
        }

        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.tunnel.lining_section"));
        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##tunnel_lining_material",
            PlotI18n.tr("plugin.road.tunnel.lining_material"),
            style.getLiningMaterial(),
            material -> {
                style.setLiningMaterial(material);
                notifyChanged(style, onChanged, presetSelection);
            },
            false);
        int[] liningThickness = {style.getLiningThickness()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.lining_thickness"), liningThickness,
                TunnelStyle.MIN_LINING_THICKNESS, TunnelStyle.MAX_LINING_THICKNESS)) {
            style.setLiningThickness(liningThickness[0]);
            notifyChanged(style, onChanged, presetSelection);
        }

        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.tunnel.lighting_section"));
        ImInt lightingIndex = new ImInt(lightingOrdinal(style.getLightingMode()));
        String[] lightingLabels = {
            PlotI18n.tr("plugin.road.tunnel.lighting.none"),
            PlotI18n.tr("plugin.road.tunnel.lighting.wall_bands"),
            PlotI18n.tr("plugin.road.tunnel.lighting.ceiling_band"),
            PlotI18n.tr("plugin.road.tunnel.lighting.wall_and_ceiling")
        };
        if (ImGui.combo(PlotI18n.tr("plugin.road.tunnel.lighting_mode"), lightingIndex, lightingLabels)) {
            style.setLightingMode(uiLightingModes()[lightingIndex.get()]);
            notifyChanged(style, onChanged, presetSelection);
        }
        if (style.getLightingMode() != TunnelLightingMode.NONE) {
            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##tunnel_light_material",
                PlotI18n.tr("plugin.road.tunnel.light_material"),
                style.getLightMaterial(),
                material -> {
                    style.setLightMaterial(material);
                    notifyChanged(style, onChanged, presetSelection);
                },
                false);
            int[] lightSpacing = {style.getLightSpacing()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.light_spacing"), lightSpacing,
                    TunnelStyle.MIN_LIGHT_SPACING, TunnelStyle.MAX_LIGHT_SPACING)) {
                style.setLightSpacing(lightSpacing[0]);
                notifyChanged(style, onChanged, presetSelection);
            }
        }

        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.road.tunnel.decoration_section"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImBoolean accentRings = new ImBoolean(style.isAccentRings());
            if (ImGui.checkbox(PlotI18n.tr("plugin.road.tunnel.accent_rings"), accentRings)) {
                style.setAccentRings(accentRings.get());
                notifyChanged(style, onChanged, presetSelection);
            }
            if (style.isAccentRings()) {
                RoadUiWidgets.renderBlockMaterialPicker(
                    ctx,
                    "##tunnel_accent_material",
                    PlotI18n.tr("plugin.road.tunnel.accent_material"),
                    style.getAccentMaterial(),
                    material -> {
                        style.setAccentMaterial(material);
                        notifyChanged(style, onChanged, presetSelection);
                    },
                    false);
                int[] accentSpacing = {style.getAccentSpacing()};
                if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.accent_spacing"), accentSpacing,
                        TunnelStyle.MIN_ACCENT_SPACING, TunnelStyle.MAX_ACCENT_SPACING)) {
                    style.setAccentSpacing(accentSpacing[0]);
                    notifyChanged(style, onChanged, presetSelection);
                }
            }
        }
    }

    private static void notifyChanged(
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection) {
        if (presetSelection != null) {
            presetSelection.syncWithStyle(style);
        }
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private static void renderStylePresetCombo(
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection) {
        TunnelPreset[] presets = TunnelPreset.values();
        String[] labels = new String[presets.length + 1];
        labels[0] = PlotI18n.tr("plugin.road.tunnel.style_preset.none");
        for (int i = 0; i < presets.length; i++) {
            labels[i + 1] = PlotI18n.tr(presets[i].translationKey());
        }
        int current = 0;
        if (presetSelection != null && presetSelection.applied() != null) {
            current = presetSelection.applied().ordinal() + 1;
        }
        ImInt index = new ImInt(current);
        if (ImGui.combo(PlotI18n.tr("plugin.road.tunnel.style_preset"), index, labels)) {
            int selected = index.get();
            if (selected <= 0) {
                if (presetSelection != null) {
                    presetSelection.clear();
                }
            } else if (selected <= presets.length) {
                TunnelPreset preset = presets[selected - 1];
                TunnelPresetCatalog.apply(preset, style);
                if (presetSelection != null) {
                    presetSelection.apply(preset);
                }
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        }
    }

    private static TunnelLightingMode[] uiLightingModes() {
        return new TunnelLightingMode[] {
            TunnelLightingMode.NONE,
            TunnelLightingMode.WALL_BANDS,
            TunnelLightingMode.CEILING_BAND,
            TunnelLightingMode.WALL_AND_CEILING
        };
    }

    private static int lightingOrdinal(TunnelLightingMode mode) {
        TunnelLightingMode[] modes = uiLightingModes();
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == mode) {
                return i;
            }
        }
        return 0;
    }
}
