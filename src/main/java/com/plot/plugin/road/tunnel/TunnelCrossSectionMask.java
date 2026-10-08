package com.plot.plugin.road.tunnel;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Discrete tunnel cross-section occupancy for voxel placement.
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

    public static TunnelCrossSectionMask build(
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
        int cavityWidth = roadEnvelopeWidth + sideClearance * 2;
        int outerWidth = cavityWidth + lining * 2;
        int roadMin = RoadDimensionUtils.minLateralOffset(roadEnvelopeWidth);
        int roadMax = RoadDimensionUtils.maxLateralOffset(roadEnvelopeWidth);
        int cavityMin = RoadDimensionUtils.minLateralOffset(cavityWidth);
        int cavityMax = RoadDimensionUtils.maxLateralOffset(cavityWidth);
        int outerMin = RoadDimensionUtils.minLateralOffset(outerWidth);
        int outerMax = RoadDimensionUtils.maxLateralOffset(outerWidth);
        int halfCavityWidth = cavityWidth / 2;
        int roofTop = roadY + clearHeight + lining;
        TunnelCrossSectionMask mask = new TunnelCrossSectionMask(roadMin, roadMax, roadY - 1, roofTop);
        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            boolean cavityColumn = lateral >= cavityMin && lateral <= cavityMax;
            boolean roadColumn = lateral >= roadMin && lateral <= roadMax;
            for (int y = roadY - 1; y <= roofTop; y++) {
                CellKind kind;
                if (cavityColumn
                        && TunnelProfile.isCavityAir(shape, lateral, y, roadY, halfCavityWidth, clearHeight)) {
                    kind = CellKind.AIR;
                } else if (roadColumn && y == roadY) {
                    kind = CellKind.OUTSIDE;
                } else if (cavityColumn && !roadColumn && y == roadY) {
                    kind = CellKind.LINING;
                } else if (cavityColumn && y == roadY - 1) {
                    kind = CellKind.LINING;
                } else if (!cavityColumn || y > roadY) {
                    kind = CellKind.LINING;
                } else {
                    kind = CellKind.OUTSIDE;
                }
                mask.put(lateral, y, kind);
            }
        }
        return mask;
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
