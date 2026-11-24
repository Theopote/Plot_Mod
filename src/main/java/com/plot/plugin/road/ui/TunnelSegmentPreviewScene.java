package com.plot.plugin.road.ui;

import com.plot.plugin.road.lamp.RoadLampCompiler;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelSectionDisplayModel;
import com.plot.plugin.road.tunnel.TunnelStyle;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Builds a short 3D tunnel segment for the style editor's "cross-section context" preview.
 * Accent rings replace the full lining only at stations that hit {@link TunnelStyle#getAccentSpacing()},
 * matching world generation (no 2D schematic markers).
 */
public final class TunnelSegmentPreviewScene {
    private static final String ROAD_MATERIAL = "minecraft:gray_concrete";

    private TunnelSegmentPreviewScene() {
    }

    /** Cache key covering fields that affect the extruded segment. */
    public static Object cacheKey(TunnelStyle style, int roadEnvelopeWidth) {
        if (style == null) {
            return roadEnvelopeWidth;
        }
        // Arrays.asList allows null anchors; List.of would NPE and skip ImGui endChild.
        return Arrays.asList(
            roadEnvelopeWidth,
            style.getShape(),
            style.getClearHeight(),
            style.getSideClearance(),
            style.getLiningThickness(),
            style.getLiningMaterial(),
            style.getLightingMode(),
            style.getLightMaterial(),
            style.getLightSpacing(),
            style.getLightMount(),
            style.getStoredLightAnchors(),
            style.isAccentRings(),
            style.getAccentMaterial(),
            style.getAccentSpacing());
    }

    public static List<RoadLampCompiler.Voxel> build(TunnelStyle style, int roadEnvelopeWidth) {
        if (style == null) {
            return List.of();
        }
        int envelope = Math.max(1, roadEnvelopeWidth);
        int roadY = TunnelCrossSectionPreviewRenderer.PREVIEW_ROAD_Y;
        int accentSpacing = Math.max(TunnelStyle.MIN_ACCENT_SPACING, style.getAccentSpacing());
        int lightSpacing = Math.max(1, style.getLightSpacing());
        int halfLen = Math.max(accentSpacing, lightSpacing) + 2;
        boolean rings = style.hasEffectiveAccentRings();
        String lining = style.getLiningMaterial();
        String accent = blankTo(style.getAccentMaterial(), lining);
        String light = blankTo(style.getLightMaterial(), "minecraft:sea_lantern");

        List<RoadLampCompiler.Voxel> scene = new ArrayList<>();
        for (int z = -halfLen; z <= halfLen; z++) {
            boolean accentStation = rings && hitsSpacing(z, accentSpacing);
            boolean lightStation = style.getLightingMode() != TunnelLightingMode.NONE
                && hitsSpacing(z, lightSpacing);
            TunnelSectionDisplayModel model = TunnelSectionDisplayModel.build(
                style.getShape(),
                envelope,
                style.getSideClearance(),
                style.getClearHeight(),
                style.getLiningThickness(),
                roadY,
                lightStation ? style.getLightingMode() : TunnelLightingMode.NONE,
                style.getLightMount(),
                lightStation ? style.getStoredLightAnchors() : null,
                accentStation,
                accentStation
                    ? TunnelSectionDisplayModel.PreviewSectionKind.ACCENT_RING_SECTION
                    : TunnelSectionDisplayModel.PreviewSectionKind.NORMAL_SECTION);
            appendSlice(scene, model, z, lining, accent, light);
        }
        return List.copyOf(scene);
    }

    private static void appendSlice(
            List<RoadLampCompiler.Voxel> scene,
            TunnelSectionDisplayModel model,
            int z,
            String lining,
            String accent,
            String light) {
        int roadY = model.roadY();
        for (TunnelSectionDisplayModel.Cell cell : model.cells()) {
            String block = switch (cell.kind()) {
                case AIR -> null;
                case ROAD -> ROAD_MATERIAL;
                case LINING -> lining;
                case ACCENT -> accent;
                case WALL_LIGHT, CEILING_LIGHT -> light;
            };
            if (block == null || block.isBlank()) {
                continue;
            }
            int y = cell.kind() == TunnelSectionDisplayModel.DisplayKind.ROAD
                ? -1
                : cell.y() - roadY;
            scene.add(new RoadLampCompiler.Voxel(new BlockPos(cell.lateral(), y, z), block));
        }
    }

    static boolean hitsSpacing(int station, int spacing) {
        if (spacing <= 0) {
            return false;
        }
        return Math.floorMod(station, spacing) == 0;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "") : value;
    }
}
