package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.lamp.RoadLampCompiler;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelLightMount;
import com.plot.plugin.road.tunnel.TunnelLightPlacementResolver;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelPreset;
import com.plot.plugin.road.tunnel.TunnelPresetCatalog;
import com.plot.plugin.road.tunnel.TunnelProfile;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.plugin.road.ui.editor.RoadLampPreview;
import com.plot.plugin.ui.PluginSectionCard;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;
import java.util.Objects;

/**
 * Full tunnel style editor body used by {@link TunnelStyleFloatingEditor}.
 */
public final class TunnelStyleEditor {
    /** Left column share for the live cross-section preview. */
    private static final float PREVIEW_COLUMN_RATIO = 0.38f;
    private static final float PREVIEW_COLUMN_MIN = 168f;
    private static final float PARAMS_COLUMN_MIN = 220f;
    private static final float LIVE_PREVIEW_HEIGHT_MIN = 160f;

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

        /** Detect a matching catalog preset for the current draft (e.g. on open). */
        public void detectFromStyle(TunnelStyle style) {
            applied = TunnelPresetCatalog.findMatching(style);
        }
    }

    /**
     * Session-scoped 3D segment preview state for the open floating editor
     * (checkbox, orbit, and cached voxels).
     */
    public static final class PreviewSession {
        private boolean segmentContext;
        private final float[] yaw = {30f};
        private final float[] zoom = {1f};
        private final RoadLampPreview preview = new RoadLampPreview();
        private List<RoadLampCompiler.Voxel> cachedScene = List.of();
        private Object cacheKey;

        public boolean segmentContext() {
            return segmentContext;
        }

        public void setSegmentContext(boolean segmentContext) {
            this.segmentContext = segmentContext;
        }

        List<RoadLampCompiler.Voxel> sceneFor(TunnelStyle style, int envelopeWidth) {
            Object key = TunnelSegmentPreviewScene.cacheKey(style, envelopeWidth);
            if (!Objects.equals(key, cacheKey)) {
                cachedScene = TunnelSegmentPreviewScene.build(style, envelopeWidth);
                cacheKey = key;
            }
            return cachedScene != null ? cachedScene : List.of();
        }

        void renderSegment(List<RoadLampCompiler.Voxel> scene, float height) {
            preview.render(scene, height, yaw, zoom);
        }
    }

    public static void render(RoadUiContext ctx, TunnelStyle style, Runnable onChanged) {
        render(ctx, style, onChanged, null, null, true, null);
    }

    public static void render(
            RoadUiContext ctx,
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection) {
        render(ctx, style, onChanged, presetSelection, null, true, null);
    }

    /**
     * @param roadId when non-blank, live preview uses that road's pavement envelope width;
     *               otherwise uses the global config road width.
     */
    public static void render(
            RoadUiContext ctx,
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection,
            String roadId) {
        render(ctx, style, onChanged, presetSelection, roadId, true, null);
    }

    /**
     * @param includeStylePreset 为 false 时不画风格预设下拉（由外层与「使用全局」放同一行）。
     */
    public static void render(
            RoadUiContext ctx,
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection,
            String roadId,
            boolean includeStylePreset) {
        render(ctx, style, onChanged, presetSelection, roadId, includeStylePreset, null);
    }

    /**
     * @param includeStylePreset 为 false 时不画风格预设下拉（由外层与「使用全局」放同一行）。
     * @param previewSession when non-null, enables the lamp-style「横断面情境」3D segment toggle.
     */
    public static void render(
            RoadUiContext ctx,
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection,
            String roadId,
            boolean includeStylePreset,
            PreviewSession previewSession) {
        if (style == null) {
            return;
        }
        int envelopeWidth = resolvePreviewEnvelopeWidth(ctx, roadId);
        if (includeStylePreset) {
            renderStylePresetCombo(style, onChanged, presetSelection);
            ImGui.spacing();
        }
        if (TunnelCrossSectionPreviewRenderer.renderShapePresetRow(style.getShape(), shape -> {
            style.setShape(shape);
            notifyChanged(style, onChanged, presetSelection);
        })) {
            // selection handled in callback
        }
        ImGui.spacing();
        renderPreviewAndParams(ctx, style, onChanged, presetSelection, envelopeWidth, previewSession);
    }

    /**
     * Pavement envelope used by tunnel generation for the given road, or global config when
     * {@code roadId} is blank / missing.
     */
    public static int resolvePreviewEnvelopeWidth(RoadUiContext ctx, String roadId) {
        RoadSystemConfig config = ctx != null && ctx.networkManager() != null
            ? ctx.networkManager().getConfig()
            : null;
        if (ctx != null && ctx.networkManager() != null
                && roadId != null && !roadId.isBlank()) {
            Road road = ctx.networkManager().getNetwork().getRoad(roadId);
            if (road != null) {
                return Math.max(1, RoadCorridorWidth.gradingEnvelopeWidthBlocks(
                    ResolvedCrossSection.resolve(road.getCrossSection(), config)));
            }
        }
        if (config != null) {
            return Math.max(1, RoadCorridorWidth.gradingEnvelopeWidthBlocks(
                ResolvedCrossSection.fromConfig(config)));
        }
        return TunnelCrossSectionPreviewRenderer.PREVIEW_ENVELOPE_WIDTH;
    }

    private static void renderPreviewAndParams(
            RoadUiContext ctx,
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection,
            int envelopeWidth,
            PreviewSession previewSession) {
        float avail = ImGui.getContentRegionAvailX();
        float gap = ImGui.getStyle().getItemSpacingX();
        float leftWidth = Math.max(PREVIEW_COLUMN_MIN, avail * PREVIEW_COLUMN_RATIO);
        float rightWidth = avail - leftWidth - gap;
        if (rightWidth < PARAMS_COLUMN_MIN && avail > PREVIEW_COLUMN_MIN + PARAMS_COLUMN_MIN + gap) {
            rightWidth = PARAMS_COLUMN_MIN;
            leftWidth = avail - rightWidth - gap;
        }

        // Side-by-side when both columns fit; otherwise stack preview above params.
        boolean sideBySide = rightWidth >= PARAMS_COLUMN_MIN && leftWidth >= PREVIEW_COLUMN_MIN;
        float rowHeight = Math.max(LIVE_PREVIEW_HEIGHT_MIN, ImGui.getContentRegionAvailY());
        if (sideBySide) {
            ImGui.beginChild("##tunnel_preview_col", leftWidth, rowHeight, false);
            try {
                renderLivePreviewColumn(style, onChanged, presetSelection, envelopeWidth, previewSession);
            } finally {
                ImGui.endChild();
            }
            ImGui.sameLine(0f, gap);
            ImGui.beginChild(
                "##tunnel_params_col",
                rightWidth,
                rowHeight,
                false,
                ImGuiWindowFlags.AlwaysVerticalScrollbar);
            try {
                renderParameterColumn(ctx, style, onChanged, presetSelection, envelopeWidth);
            } finally {
                ImGui.endChild();
            }
        } else {
            float previewHeight = Math.max(LIVE_PREVIEW_HEIGHT_MIN, rowHeight * 0.42f);
            ImGui.beginChild("##tunnel_preview_col", avail, previewHeight, false);
            try {
                renderLivePreviewColumn(style, onChanged, presetSelection, envelopeWidth, previewSession);
            } finally {
                ImGui.endChild();
            }
            ImGui.spacing();
            renderParameterColumn(ctx, style, onChanged, presetSelection, envelopeWidth);
        }
    }

    private static void renderLivePreviewColumn(
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection,
            int envelopeWidth,
            PreviewSession previewSession) {
        if (previewSession != null) {
            boolean next = ImGui.checkbox(
                PlotI18n.tr("plugin.road.tunnel.road_context"),
                previewSession.segmentContext()) != previewSession.segmentContext();
            previewSession.setSegmentContext(next);
        }
        if (previewSession != null && previewSession.segmentContext()) {
            List<RoadLampCompiler.Voxel> scene = previewSession.sceneFor(style, envelopeWidth);
            float height = Math.max(LIVE_PREVIEW_HEIGHT_MIN, ImGui.getContentRegionAvailY() - 8f);
            previewSession.renderSegment(scene, height);
        } else if (TunnelCrossSectionPreviewRenderer.renderLivePreview(style, envelopeWidth, true)) {
            notifyChanged(style, onChanged, presetSelection);
        }
        TunnelLightPlacementResolver.ResolveResult lightResult =
            TunnelLightPlacementResolver.resolveDetailed(
                ResolvedTunnelStyle.from(style), envelopeWidth,
                TunnelCrossSectionPreviewRenderer.PREVIEW_ROAD_Y);
        if (lightResult.unresolvedAnchors() > 0) {
            ImGui.textDisabled(PlotI18n.tr(
                "plugin.road.tunnel.light_unresolved",
                lightResult.unresolvedAnchors()));
        }
    }

    private static void renderParameterColumn(
            RoadUiContext ctx,
            TunnelStyle style,
            Runnable onChanged,
            PresetSelection presetSelection,
            int envelopeWidth) {
        PluginSectionCard.render(
            "##tunnel_sec_clearance",
            PlotI18n.tr("plugin.road.tunnel.clearance_section"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
                int[] clearHeight = {style.getClearHeight()};
                if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.clear_height"), clearHeight,
                        TunnelStyle.MIN_CLEAR_HEIGHT, TunnelStyle.MAX_CLEAR_HEIGHT)) {
                    style.setClearHeight(clearHeight[0]);
                    notifyChanged(style, onChanged, presetSelection);
                }
                if (style.getShape() == TunnelShape.CIRCULAR) {
                    int cavityHalf = (Math.max(1, envelopeWidth) + style.getSideClearance() * 2) / 2;
                    int effective = TunnelProfile.effectiveCircularClearHeight(
                        cavityHalf, style.getClearHeight());
                    ImGui.textDisabled(PlotI18n.tr(
                        "plugin.road.tunnel.circular_effective_height",
                        effective));
                }
                int[] sideClearance = {style.getSideClearance()};
                if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.side_clearance"), sideClearance,
                        TunnelStyle.MIN_SIDE_CLEARANCE, TunnelStyle.MAX_SIDE_CLEARANCE)) {
                    style.setSideClearance(sideClearance[0]);
                    notifyChanged(style, onChanged, presetSelection);
                }
            });

        PluginSectionCard.render(
            "##tunnel_sec_lining",
            PlotI18n.tr("plugin.road.tunnel.lining_section"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
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
            });

        PluginSectionCard.render(
            "##tunnel_sec_lighting",
            PlotI18n.tr("plugin.road.tunnel.lighting_section"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
                ImInt lightingIndex = new ImInt(lightingOrdinal(style.getLightingMode()));
                String[] lightingLabels = {
                    PlotI18n.tr("plugin.road.tunnel.lighting.none"),
                    PlotI18n.tr("plugin.road.tunnel.lighting.wall_bands"),
                    PlotI18n.tr("plugin.road.tunnel.lighting.ceiling_band"),
                    PlotI18n.tr("plugin.road.tunnel.lighting.wall_and_ceiling")
                };
                if (ImGui.combo(PlotI18n.tr("plugin.road.tunnel.lighting_mode"), lightingIndex, lightingLabels)) {
                    style.setLightingMode(uiLightingModes()[lightingIndex.get()]);
                    style.clearCustomLightLayout();
                    notifyChanged(style, onChanged, presetSelection);
                }
                boolean lightingActive = style.getLightingMode() != TunnelLightingMode.NONE
                    || style.hasCustomLightLayout();
                if (!lightingActive) {
                    return;
                }
                ImInt mountIndex = new ImInt(style.getLightMount() == TunnelLightMount.EMBEDDED ? 1 : 0);
                String[] mountLabels = {
                    PlotI18n.tr("plugin.road.tunnel.light_mount.protruding"),
                    PlotI18n.tr("plugin.road.tunnel.light_mount.embedded")
                };
                if (ImGui.combo(PlotI18n.tr("plugin.road.tunnel.light_mount"), mountIndex, mountLabels)) {
                    style.setLightMount(mountIndex.get() == 1
                        ? TunnelLightMount.EMBEDDED
                        : TunnelLightMount.PROTRUDING);
                    TunnelLightPlacementResolver.syncCustomAnchorsToMount(
                        style, envelopeWidth, TunnelCrossSectionPreviewRenderer.PREVIEW_ROAD_Y);
                    notifyChanged(style, onChanged, presetSelection);
                }
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
                ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.light_custom_hint"));
                if (style.hasCustomLightLayout()
                        && ImGui.button(PlotI18n.tr("plugin.road.tunnel.light_reset_defaults"))) {
                    style.clearCustomLightLayout();
                    notifyChanged(style, onChanged, presetSelection);
                }
            });

        PluginSectionCard.render(
            "##tunnel_sec_decoration",
            PlotI18n.tr("plugin.road.tunnel.decoration_section"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
                ImBoolean accentRings = new ImBoolean(style.isAccentRings());
                if (ImGui.checkbox(PlotI18n.tr("plugin.road.tunnel.accent_rings"), accentRings)) {
                    style.setAccentRings(accentRings.get());
                    notifyChanged(style, onChanged, presetSelection);
                }
                if (!style.isAccentRings()) {
                    return;
                }
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
            });
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

    static void renderStylePresetCombo(
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
