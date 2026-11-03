package com.plot.plugin.road.tunnel;

import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     * Which longitudinal station the 2D preview represents.
     * Accent rings only replace the full lining on {@link #ACCENT_RING_SECTION};
     * the default {@link #NORMAL_SECTION} keeps ordinary lining and may add sparse
     * schematic accent markers when rings are enabled.
     */
    public enum PreviewSectionKind {
        NORMAL_SECTION,
        ACCENT_RING_SECTION
    }

    /** Target count of schematic accent markers on a normal-section preview. */
    private static final int SCHEMATIC_ACCENT_MARKERS = 8;

    public record Cell(int lateral, int y, DisplayKind kind) {
    }

    private final List<Cell> cells;
    private final int minLateral;
    private final int maxLateral;
    private final int displayMinY;
    private final int displayMaxY;
    private final int effectiveClearHeight;
    private final int roadY;
    private final PreviewSectionKind sectionKind;

    private TunnelSectionDisplayModel(
            List<Cell> cells,
            int minLateral,
            int maxLateral,
            int displayMinY,
            int displayMaxY,
            int effectiveClearHeight,
            int roadY,
            PreviewSectionKind sectionKind) {
        this.cells = List.copyOf(cells);
        this.minLateral = minLateral;
        this.maxLateral = maxLateral;
        this.displayMinY = displayMinY;
        this.displayMaxY = displayMaxY;
        this.effectiveClearHeight = effectiveClearHeight;
        this.roadY = roadY;
        this.sectionKind = sectionKind;
    }

    /**
     * Builds a {@link PreviewSectionKind#NORMAL_SECTION} display model.
     * When {@code accentRings} is true, sparse schematic accent markers are overlaid;
     * the full lining is never recolored.
     */
    public static TunnelSectionDisplayModel build(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode lightingMode,
            boolean accentRings) {
        return build(
            shape,
            roadEnvelopeWidth,
            sideClearance,
            clearHeight,
            liningThickness,
            roadY,
            lightingMode,
            TunnelLightMount.PROTRUDING,
            null,
            accentRings,
            PreviewSectionKind.NORMAL_SECTION);
    }

    public static TunnelSectionDisplayModel build(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode lightingMode,
            boolean accentRings,
            PreviewSectionKind sectionKind) {
        return build(
            shape,
            roadEnvelopeWidth,
            sideClearance,
            clearHeight,
            liningThickness,
            roadY,
            lightingMode,
            TunnelLightMount.PROTRUDING,
            null,
            accentRings,
            sectionKind);
    }

    public static TunnelSectionDisplayModel build(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode lightingMode,
            TunnelLightMount lightMount,
            List<TunnelLightAnchor> lightAnchors,
            boolean accentRings) {
        return build(
            shape,
            roadEnvelopeWidth,
            sideClearance,
            clearHeight,
            liningThickness,
            roadY,
            lightingMode,
            lightMount,
            lightAnchors,
            accentRings,
            PreviewSectionKind.NORMAL_SECTION);
    }

    public static TunnelSectionDisplayModel buildFromStyle(
            TunnelStyle style,
            int roadEnvelopeWidth,
            int roadY) {
        if (style == null) {
            return build(
                TunnelShape.ARCH, roadEnvelopeWidth, 1, 5, 1, roadY,
                TunnelLightingMode.NONE, false);
        }
        return build(
            style.getShape(),
            roadEnvelopeWidth,
            style.getSideClearance(),
            style.getClearHeight(),
            style.getLiningThickness(),
            roadY,
            style.getLightingMode(),
            style.getLightMount(),
            style.getStoredLightAnchors(),
            style.isAccentRings(),
            PreviewSectionKind.NORMAL_SECTION);
    }

    public static TunnelSectionDisplayModel build(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode lightingMode,
            TunnelLightMount lightMount,
            List<TunnelLightAnchor> lightAnchors,
            boolean accentRings,
            PreviewSectionKind sectionKind) {
        TunnelShape resolved = shape != null ? shape : TunnelShape.ARCH;
        PreviewSectionKind section = sectionKind != null ? sectionKind : PreviewSectionKind.NORMAL_SECTION;
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

        boolean fullAccent = accentRings && section == PreviewSectionKind.ACCENT_RING_SECTION;
        boolean schematicAccent = accentRings && section == PreviewSectionKind.NORMAL_SECTION;

        Map<Long, DisplayKind> layers = new HashMap<>();
        List<long[]> liningCells = schematicAccent ? new ArrayList<>() : null;
        int[] bounds = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
        // bounds: minLat, maxLat, minY, maxY among visible cells

        mask.forEach((lateral, y, kind) -> {
            if (y < roadY) {
                return;
            }
            if (kind == TunnelCrossSectionMask.CellKind.OUTSIDE) {
                return;
            }
            DisplayKind display;
            if (kind == TunnelCrossSectionMask.CellKind.AIR) {
                display = DisplayKind.AIR;
            } else if (fullAccent) {
                display = DisplayKind.ACCENT;
            } else {
                display = DisplayKind.LINING;
                if (schematicAccent) {
                    liningCells.add(new long[]{lateral, y});
                }
            }
            layers.put(pack(lateral, y), display);
            expandBounds(bounds, lateral, y);
        });

        if (schematicAccent && liningCells != null && !liningCells.isEmpty()) {
            applySchematicAccentMarkers(layers, liningCells, mask, roadY);
        }

        for (int lateral = mask.roadMin(); lateral <= mask.roadMax(); lateral++) {
            layers.put(pack(lateral, roadY), DisplayKind.ROAD);
            expandBounds(bounds, lateral, roadY);
        }

        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            resolved, envelope, side, clear, lining, roadY,
            lightingMode != null ? lightingMode : TunnelLightingMode.NONE,
            lightMount != null ? lightMount : TunnelLightMount.PROTRUDING,
            lightAnchors);
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
            return new TunnelSectionDisplayModel(
                List.of(), 0, 0, roadY, roadY, effectiveClear, roadY, section);
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
            roadY,
            section);
    }

    /**
     * Marks a sparse ring of inner-face lining cells as ACCENT so the preview
     * hints at periodic accent rings without recoloring the whole shell.
     */
    private static void applySchematicAccentMarkers(
            Map<Long, DisplayKind> layers,
            List<long[]> liningCells,
            TunnelCrossSectionMask mask,
            int roadY) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<long[]> innerFace = new ArrayList<>();
        for (long[] cell : liningCells) {
            int lateral = (int) cell[0];
            int y = (int) cell[1];
            boolean touchesAir = false;
            for (int[] d : dirs) {
                if (mask.kindAt(lateral + d[0], y + d[1]) == TunnelCrossSectionMask.CellKind.AIR) {
                    touchesAir = true;
                    break;
                }
            }
            if (touchesAir) {
                innerFace.add(cell);
            }
        }
        if (innerFace.isEmpty()) {
            return;
        }

        double centerLat = 0.0;
        double centerY = 0.0;
        for (long[] cell : innerFace) {
            centerLat += cell[0];
            centerY += cell[1];
        }
        centerLat /= innerFace.size();
        centerY /= innerFace.size();

        final double cx = centerLat;
        final double cy = centerY;
        innerFace.sort(Comparator.comparingDouble(cell -> Math.atan2(cell[1] - cy, cell[0] - cx)));

        int markers = Math.min(SCHEMATIC_ACCENT_MARKERS, innerFace.size());
        // Prefer at least a few samples even on tiny shells.
        markers = Math.max(1, markers);
        for (int i = 0; i < markers; i++) {
            int index = (int) Math.round(i * (innerFace.size() - 1) / (double) Math.max(1, markers - 1));
            if (markers == 1) {
                index = 0;
            }
            long[] cell = innerFace.get(Math.min(index, innerFace.size() - 1));
            int lateral = (int) cell[0];
            int y = (int) cell[1];
            // Road overlay owns pavement cells; skip if somehow present.
            if (y == roadY) {
                continue;
            }
            layers.put(pack(lateral, y), DisplayKind.ACCENT);
        }
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

    public PreviewSectionKind sectionKind() {
        return sectionKind;
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
