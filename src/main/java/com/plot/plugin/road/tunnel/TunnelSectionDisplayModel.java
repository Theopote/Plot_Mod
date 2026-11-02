package com.plot.plugin.road.tunnel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Preview-only visible cross-section: roadY and above, with road / light / accent overlays.
 * Does not alter construction geometry.
 */
public final class TunnelSectionDisplayModel {
    public enum DisplayKind {
        AIR,
        LINING,
        ROAD,
        WALL_LIGHT,
        CEILING_LIGHT,
        ACCENT
    }

    public record Cell(int lateral, int y, DisplayKind kind) {
    }

    private final List<Cell> cells;
    private final int minLateral;
    private final int maxLateral;
    private final int displayMinY;
    private final int displayMaxY;
    private final int effectiveClearHeight;
    private final int roadY;

    private TunnelSectionDisplayModel(
            List<Cell> cells,
            int minLateral,
            int maxLateral,
            int displayMinY,
            int displayMaxY,
            int effectiveClearHeight,
            int roadY) {
        this.cells = List.copyOf(cells);
        this.minLateral = minLateral;
        this.maxLateral = maxLateral;
        this.displayMinY = displayMinY;
        this.displayMaxY = displayMaxY;
        this.effectiveClearHeight = effectiveClearHeight;
        this.roadY = roadY;
    }

    public static TunnelSectionDisplayModel build(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode lightingMode,
            boolean accentRings) {
        TunnelShape resolved = shape != null ? shape : TunnelShape.ARCH;
        int side = Math.max(TunnelStyle.MIN_SIDE_CLEARANCE, sideClearance);
        int clear = Math.max(TunnelStyle.MIN_CLEAR_HEIGHT, clearHeight);
        int lining = Math.max(TunnelStyle.MIN_LINING_THICKNESS, liningThickness);
        int envelope = Math.max(1, roadEnvelopeWidth);

        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            resolved, envelope, side, clear, lining, roadY, false);

        int cavityHalf = (envelope + side * 2) / 2;
        int effectiveClear = resolved == TunnelShape.CIRCULAR
            ? TunnelProfile.effectiveCircularClearHeight(cavityHalf, clear)
            : clear;

        Map<Long, DisplayKind> layers = new HashMap<>();
        int[] bounds = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
        // bounds: minLat, maxLat, minY, maxY among visible cells

        mask.forEach((lateral, y, kind) -> {
            if (y < roadY) {
                return;
            }
            if (kind == TunnelCrossSectionMask.CellKind.OUTSIDE) {
                return;
            }
            DisplayKind display = kind == TunnelCrossSectionMask.CellKind.AIR
                ? DisplayKind.AIR
                : (accentRings ? DisplayKind.ACCENT : DisplayKind.LINING);
            layers.put(pack(lateral, y), display);
            expandBounds(bounds, lateral, y);
        });

        for (int lateral = mask.roadMin(); lateral <= mask.roadMax(); lateral++) {
            layers.put(pack(lateral, roadY), DisplayKind.ROAD);
            expandBounds(bounds, lateral, roadY);
        }

        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            resolved, envelope, side, clear, lining, roadY,
            lightingMode != null ? lightingMode : TunnelLightingMode.NONE);
        for (TunnelLightPlacementResolver.LightPosition light : lights) {
            if (light.y() < roadY) {
                continue;
            }
            DisplayKind kind = light.type() == TunnelLightPlacementResolver.LightType.CEILING
                ? DisplayKind.CEILING_LIGHT
                : DisplayKind.WALL_LIGHT;
            layers.put(pack(light.lateral(), light.y()), kind);
            expandBounds(bounds, light.lateral(), light.y());
        }

        if (bounds[0] == Integer.MAX_VALUE) {
            return new TunnelSectionDisplayModel(List.of(), 0, 0, roadY, roadY, effectiveClear, roadY);
        }

        List<Cell> cells = new ArrayList<>(layers.size());
        layers.forEach((key, kind) -> cells.add(new Cell(unpackLateral(key), unpackY(key), kind)));
        return new TunnelSectionDisplayModel(
            cells,
            bounds[0],
            bounds[1],
            Math.max(roadY, bounds[2]),
            bounds[3],
            effectiveClear,
            roadY);
    }

    public List<Cell> cells() {
        return cells;
    }

    public int minLateral() {
        return minLateral;
    }

    public int maxLateral() {
        return maxLateral;
    }

    public int displayMinY() {
        return displayMinY;
    }

    public int displayMaxY() {
        return displayMaxY;
    }

    public int effectiveClearHeight() {
        return effectiveClearHeight;
    }

    public int roadY() {
        return roadY;
    }

    public int cols() {
        return maxLateral - minLateral + 1;
    }

    public int rows() {
        return displayMaxY - displayMinY + 1;
    }

    private static void expandBounds(int[] bounds, int lateral, int y) {
        bounds[0] = Math.min(bounds[0], lateral);
        bounds[1] = Math.max(bounds[1], lateral);
        bounds[2] = Math.min(bounds[2], y);
        bounds[3] = Math.max(bounds[3], y);
    }

    private static long pack(int lateral, int y) {
        return (((long) lateral) << 32) ^ (y & 0xffffffffL);
    }

    private static int unpackLateral(long key) {
        return (int) (key >> 32);
    }

    private static int unpackY(long key) {
        return (int) key;
    }
}
