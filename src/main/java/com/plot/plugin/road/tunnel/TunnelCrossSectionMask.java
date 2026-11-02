package com.plot.plugin.road.tunnel;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Discrete tunnel cross-section occupancy for voxel placement.
 * <p>
 * AIR is the inner cavity profile. LINING is a Chebyshev dilation of AIR by the
 * lining thickness (4-neighbor-connected shell), excluding pavement cells at
 * {@code roadY} which stay OUTSIDE for the road generator.
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

        // Expand bounds one extra lining step so Chebyshev shells near the edge stay in-range.
        minY -= lining;
        maxY += lining;
        outerMin -= lining;
        outerMax += lining;

        Set<Long> air = new HashSet<>();
        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            boolean roadColumn = lateral >= roadMin && lateral <= roadMax;
            for (int y = minY; y <= maxY; y++) {
                if (roadColumn && y == roadY) {
                    continue;
                }
                if (TunnelProfile.containsInner(
                        resolved, lateral, y, roadY, halfCavityWidth, clearHeight)) {
                    air.add(pack(lateral, y));
                }
            }
        }

        Set<Long> shell = dilateChebyshev(air, lining, outerMin, outerMax, minY, maxY);
        int tightMinY = Integer.MAX_VALUE;
        int tightMaxY = Integer.MIN_VALUE;
        for (long key : air) {
            int y = unpackY(key);
            tightMinY = Math.min(tightMinY, y);
            tightMaxY = Math.max(tightMaxY, y);
        }
        for (long key : shell) {
            if (air.contains(key)) {
                continue;
            }
            int lateral = unpackLateral(key);
            int y = unpackY(key);
            if (lateral >= roadMin && lateral <= roadMax && y == roadY) {
                continue;
            }
            tightMinY = Math.min(tightMinY, y);
            tightMaxY = Math.max(tightMaxY, y);
        }
        if (tightMinY == Integer.MAX_VALUE) {
            tightMinY = roadY;
            tightMaxY = roadY;
        }
        TunnelCrossSectionMask mask = new TunnelCrossSectionMask(roadMin, roadMax, tightMinY, tightMaxY);
        for (long key : air) {
            mask.put(unpackLateral(key), unpackY(key), CellKind.AIR);
        }
        for (long key : shell) {
            if (air.contains(key)) {
                continue;
            }
            int lateral = unpackLateral(key);
            int y = unpackY(key);
            if (lateral >= roadMin && lateral <= roadMax && y == roadY) {
                continue;
            }
            mask.put(lateral, y, CellKind.LINING);
        }
        return mask;
    }

    /**
     * Chebyshev (chessboard) dilation of {@code seed} by {@code radius} steps within bounds.
     */
    private static Set<Long> dilateChebyshev(
            Set<Long> seed,
            int radius,
            int minLateral,
            int maxLateral,
            int minY,
            int maxY) {
        Set<Long> expanded = new HashSet<>(seed);
        if (radius <= 0 || seed.isEmpty()) {
            return expanded;
        }
        ArrayDeque<Long> frontier = new ArrayDeque<>(seed);
        // Distance layers: each iteration expands by Chebyshev distance 1.
        for (int step = 0; step < radius; step++) {
            int layer = frontier.size();
            for (int i = 0; i < layer; i++) {
                long key = frontier.removeFirst();
                int lateral = unpackLateral(key);
                int y = unpackY(key);
                for (int dl = -1; dl <= 1; dl++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dl == 0 && dy == 0) {
                            continue;
                        }
                        int nl = lateral + dl;
                        int ny = y + dy;
                        if (nl < minLateral || nl > maxLateral || ny < minY || ny > maxY) {
                            continue;
                        }
                        long next = pack(nl, ny);
                        if (expanded.add(next)) {
                            frontier.addLast(next);
                        }
                    }
                }
            }
        }
        return expanded;
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
