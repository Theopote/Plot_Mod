package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * Full tunnel style editor shown in a popup from {@link TunnelStyleControls}.
 */
public final class TunnelStyleEditor {
    private TunnelStyleEditor() {
    }

    public static void render(RoadUiContext ctx, TunnelStyle style, Runnable onChanged) {
        if (style == null) {
            return;
        }
        ImInt shapeIndex = new ImInt(shapeOrdinal(style.getShape()));
        String[] shapeLabels = {
            PlotI18n.tr("plugin.road.tunnel.shape.rectangular"),
            PlotI18n.tr("plugin.road.tunnel.shape.arch"),
            PlotI18n.tr("plugin.road.tunnel.shape.horseshoe")
        };
        if (ImGui.combo(PlotI18n.tr("plugin.road.tunnel.shape"), shapeIndex, shapeLabels)) {
            style.setShape(TunnelShape.values()[shapeIndex.get()]);
            onChanged.run();
        }
        int[] clearHeight = {style.getClearHeight()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.clear_height"), clearHeight,
                TunnelStyle.MIN_CLEAR_HEIGHT, TunnelStyle.MAX_CLEAR_HEIGHT)) {
            style.setClearHeight(clearHeight[0]);
            onChanged.run();
        }
        int[] sideClearance = {style.getSideClearance()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.side_clearance"), sideClearance,
                TunnelStyle.MIN_SIDE_CLEARANCE, TunnelStyle.MAX_SIDE_CLEARANCE)) {
            style.setSideClearance(sideClearance[0]);
            onChanged.run();
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
                onChanged.run();
            },
            false);
        int[] liningThickness = {style.getLiningThickness()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.lining_thickness"), liningThickness,
                TunnelStyle.MIN_LINING_THICKNESS, TunnelStyle.MAX_LINING_THICKNESS)) {
            style.setLiningThickness(liningThickness[0]);
            onChanged.run();
        }

        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.tunnel.lighting_section"));
        ImInt lightingIndex = new ImInt(lightingOrdinal(style.getLightingMode()));
        String[] lightingLabels = {
            PlotI18n.tr("plugin.road.tunnel.lighting.none"),
            PlotI18n.tr("plugin.road.tunnel.lighting.wall_bands"),
            PlotI18n.tr("plugin.road.tunnel.lighting.ceiling_band")
        };
        if (ImGui.combo(PlotI18n.tr("plugin.road.tunnel.lighting_mode"), lightingIndex, lightingLabels)) {
            style.setLightingMode(uiLightingModes()[lightingIndex.get()]);
            onChanged.run();
        }
        if (style.getLightingMode() != TunnelLightingMode.NONE) {
            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##tunnel_light_material",
                PlotI18n.tr("plugin.road.tunnel.light_material"),
                style.getLightMaterial(),
                material -> {
                    style.setLightMaterial(material);
                    onChanged.run();
                },
                false);
            int[] lightSpacing = {style.getLightSpacing()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel.light_spacing"), lightSpacing,
                    TunnelStyle.MIN_LIGHT_SPACING, TunnelStyle.MAX_LIGHT_SPACING)) {
                style.setLightSpacing(lightSpacing[0]);
                onChanged.run();
            }
        }

        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.road.tunnel.decoration_section"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImBoolean accentRings = new ImBoolean(style.isAccentRings());
            if (ImGui.checkbox(PlotI18n.tr("plugin.road.tunnel.accent_rings"), accentRings)) {
                style.setAccentRings(accentRings.get());
                onChanged.run();
            }
            if (style.isAccentRings()) {
                RoadUiWidgets.renderBlockMaterialPicker(
                    ctx,
                    "##tunnel_accent_material",
                    PlotI18n.tr("plugin.road.tunnel.accent_material"),
                    style.getAccentMaterial(),
                    material -> {
                        style.setAccentMaterial(material);
                        onChanged.run();
                    },
                    false);
                int[] accentSpacing = {style.getAccentSpacing()};
                if (ImGui.sliderInt(PlotI18n.tr("plugin.road.tunnel_accent_spacing"), accentSpacing,
                        TunnelStyle.MIN_ACCENT_SPACING, TunnelStyle.MAX_ACCENT_SPACING)) {
                    style.setAccentSpacing(accentSpacing[0]);
                    onChanged.run();
                }
            }
        }
    }

    private static TunnelLightingMode[] uiLightingModes() {
        return new TunnelLightingMode[] {
            TunnelLightingMode.NONE,
            TunnelLightingMode.WALL_BANDS,
            TunnelLightingMode.CEILING_BAND
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

    private static int shapeOrdinal(TunnelShape shape) {
        return shape != null ? shape.ordinal() : TunnelShape.ARCH.ordinal();
    }
}
