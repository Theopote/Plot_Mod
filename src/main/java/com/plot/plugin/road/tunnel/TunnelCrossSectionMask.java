package com.plot.plugin.road.tunnel;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Discrete tunnel cross-section occupancy for voxel placement.
 * <p>
 * Built from inner + outer contours: AIR inside the inner profile, LINING between
 * outer and inner shells. Road pavement cells at {@code roadY} are OUTSIDE.
 */
public final class TunnelCrossSectionMask {
    public enum CellKind {
        OUTSIDE,
        AIR,
        LINING
    }

    private final Map<Long, CellKind> cells = new HashMap<>();
    private final int roadMin;
    private final int roadMax;
    private final int minY;
    private final int maxY;

    private TunnelCrossSectionMask(int roadMin, int roadMax, int minY, int maxY) {
        this.roadMin = roadMin;
        this.roadMax = roadMax;
        this.minY = minY;
        this.maxY = maxY;
    }

    // Geometry only; no terrain/world references. Bounded to avoid retaining a
    // mask at every elevation along a long graded road.
    private record Key(TunnelShape shape, int width, int side, int height, int lining, int y, boolean portal) { }
    private static final Map<Key, TunnelCrossSectionMask> CACHE =
        new java.util.LinkedHashMap<>(32, 0.75f, true);
    private static final int MAX_CACHED_CELLS = 100_000;
    private static int cachedCells;

    public static synchronized TunnelCrossSectionMask build(
            TunnelShape shape, int roadEnvelopeWidth, int sideClearance,
            int clearHeight, int liningThickness, int roadY, boolean portalFrame) {
        var key = new Key(shape, roadEnvelopeWidth, sideClearance, clearHeight, liningThickness, roadY, portalFrame);
        var mask = CACHE.get(key);
        if (mask != null) return mask;
        mask = buildUncached(shape, roadEnvelopeWidth, sideClearance, clearHeight, liningThickness, roadY, portalFrame);
        if (mask.cells.size() <= MAX_CACHED_CELLS) {
            while (!CACHE.isEmpty() && (CACHE.size() >= 32 || cachedCells + mask.cells.size() > MAX_CACHED_CELLS)) {
                var iterator = CACHE.entrySet().iterator();
                cachedCells -= iterator.next().getValue().cells.size();
                iterator.remove();
            }
            CACHE.put(key, mask);
            cachedCells += mask.cells.size();
        }
        return mask;
    }

    private static TunnelCrossSectionMask buildUncached(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            boolean portalFrame) {
        int lining = portalFrame
            ? Math.min(TunnelStyle.MAX_LINING_THICKNESS, liningThickness + 1)
            : liningThickness;
        lining = Math.max(TunnelStyle.MIN_LINING_THICKNESS, lining);
        int cavityWidth = roadEnvelopeWidth + sideClearance * 2;
        int outerWidth = cavityWidth + lining * 2;
        int roadMin = RoadDimensionUtils.minLateralOffset(roadEnvelopeWidth);
        int roadMax = RoadDimensionUtils.maxLateralOffset(roadEnvelopeWidth);
        int outerMin = RoadDimensionUtils.minLateralOffset(outerWidth);
        int outerMax = RoadDimensionUtils.maxLateralOffset(outerWidth);
        int halfCavityWidth = cavityWidth / 2;
        TunnelShape resolved = shape != null ? shape : TunnelShape.ARCH;
        int invert = TunnelProfile.derivedInvertDepth(resolved, halfCavityWidth, clearHeight);

        int minY = roadY - invert - lining;
        int maxY = roadY + clearHeight + lining;
        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            maxY = Math.max(maxY, TunnelProfile.outerCeilingY(
                resolved, Math.abs(lateral), halfCavityWidth, roadY, clearHeight, lining));
            minY = Math.min(minY, TunnelProfile.outerFloorY(
                resolved, Math.abs(lateral), halfCavityWidth, roadY, clearHeight, lining, invert));
        }

        TunnelCrossSectionMask mask = new TunnelCrossSectionMask(roadMin, roadMax, minY, maxY);
        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            boolean roadColumn = lateral >= roadMin && lateral <= roadMax;
            for (int y = minY; y <= maxY; y++) {
                boolean inner = TunnelProfile.containsInner(
                    resolved, lateral, y, roadY, halfCavityWidth, clearHeight);
                boolean outer = TunnelProfile.containsOuter(
                    resolved, lateral, y, roadY, halfCavityWidth, clearHeight, lining);

                CellKind kind;
                if (roadColumn && y == roadY) {
                    // Pavement owned by road generator — keep mask hole flat.
                    kind = CellKind.OUTSIDE;
                } else if (inner) {
                    kind = CellKind.AIR;
                } else if (outer) {
                    kind = CellKind.LINING;
                } else {
                    kind = CellKind.OUTSIDE;
                }
                mask.put(lateral, y, kind);
            }
        }
        return mask;
    }

    /**
     * Highest LINING (or structural) Y in this lateral column, or {@link Integer#MIN_VALUE}
     * when the column has no shell cells.
     */
    public int outerTopY(int lateral) {
        int top = Integer.MIN_VALUE;
        for (int y = minY; y <= maxY; y++) {
            if (kindAt(lateral, y) == CellKind.LINING) {
                top = y;
            }
        }
        return top;
    }

    public int roadMin() {
        return roadMin;
    }

    public int roadMax() {
        return roadMax;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public CellKind kindAt(int lateral, int y) {
        return cells.getOrDefault(pack(lateral, y), CellKind.OUTSIDE);
    }

    public void forEach(CellConsumer consumer) {
        cells.forEach((key, kind) -> {
            int lateral = unpackLateral(key);
            int y = unpackY(key);
            consumer.accept(lateral, y, kind);
        });
    }

    @FunctionalInterface
    public interface CellConsumer {
        void accept(int lateral, int y, CellKind kind);
    }

    private void put(int lateral, int y, CellKind kind) {
        if (kind != CellKind.OUTSIDE) {
            cells.put(pack(lateral, y), kind);
        }
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
