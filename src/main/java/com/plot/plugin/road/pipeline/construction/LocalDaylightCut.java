package com.plot.plugin.road.pipeline.construction;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.core.terrain.TerrainSampler;

import java.util.List;

/**
 * Converts short, isolated rock-shelter islands into open {@link RoadConstructionType#CUT}
 * when both flanks are already daylight road and the estimated excavation stays small.
 * <p>
 * Does <strong>not</strong> weaken continuous cliffs, true tunnels, or tunnel-to-tunnel
 * continuity (those remain after {@link RoadConstructionClassifier#mergeShortTunnelGaps}).
 */
public final class LocalDaylightCut {
    /** Max continuous chainage length (Minecraft blocks) eligible for daylight. */
    public static final double MAX_LENGTH_BLOCKS = 4.0;
    /** Soft cap on estimated daylight prism volume (length×width×cover). */
    public static final int MAX_EXCAVATION_BLOCKS = 150;

    private LocalDaylightCut() {
    }

    /**
     * Mutates {@code types} in place: short tunnel-family islands flanked by open road
     * become {@link RoadConstructionType#CUT}.
     */
    public static void apply(
            List<RoadConstructionType> types,
            List<Double> segmentDistances,
            List<SegmentHeightInfo> heightInfos,
            TerrainSampler terrain,
            int gradingEnvelopeWidth,
            ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock) {
        if (types == null || types.isEmpty() || segmentDistances == null || heightInfos == null) {
            return;
        }
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double maxLengthCanvas = MAX_LENGTH_BLOCKS * scale;
        int index = 0;
        while (index < types.size()) {
            if (!isDaylightCandidate(types.get(index))) {
                index++;
                continue;
            }
            int start = index;
            double length = 0.0;
            while (index < types.size() && isDaylightCandidate(types.get(index))) {
                if (index < segmentDistances.size()) {
                    length += segmentDistances.get(index);
                }
                index++;
            }
            int endExclusive = index;
            if (length <= 1e-9 || length >= maxLengthCanvas - 1e-9) {
                continue;
            }
            if (!flankedByOpenDaylight(types, start, endExclusive)) {
                continue;
            }
            if (!excavationWithinBudget(
                    start, endExclusive, heightInfos, terrain, gradingEnvelopeWidth,
                    tunnelStyle, columnResolver, scale)) {
                continue;
            }
            for (int i = start; i < endExclusive; i++) {
                types.set(i, RoadConstructionType.CUT);
            }
        }
    }

    /**
     * Only rock-shelter islands — never true TUNNEL/PORTAL runs. Underpasses and
     * mountain tunnels must stay continuous; the screenshot case is isolated cliff shelter.
     */
    static boolean isDaylightCandidate(RoadConstructionType type) {
        return type != null && type.isRockShelter();
    }

    static boolean isOpenDaylight(RoadConstructionType type) {
        return type == RoadConstructionType.ROAD
            || type == RoadConstructionType.CUT
            || type == RoadConstructionType.FILL;
    }

    static boolean flankedByOpenDaylight(List<RoadConstructionType> types, int start, int endExclusive) {
        boolean openBefore = start == 0 || isOpenDaylight(types.get(start - 1));
        boolean openAfter = endExclusive >= types.size() || isOpenDaylight(types.get(endExclusive));
        // Require at least one open flank; both preferred. Isolated start/end of path with
        // one open neighbor still qualifies when the other side is path boundary.
        if (start == 0 && endExclusive >= types.size()) {
            return false; // whole path is candidate — not a local outcrop
        }
        if (start > 0 && endExclusive < types.size()) {
            return openBefore && openAfter;
        }
        return openBefore || openAfter;
    }

    /**
     * Count solid cells above design Y inside the grading envelope along the run.
     * Caps early once over budget.
     */
    static boolean excavationWithinBudget(
            int start,
            int endExclusive,
            List<SegmentHeightInfo> heightInfos,
            TerrainSampler terrain,
            int gradingEnvelopeWidth,
            ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double scale) {
        if (terrain == null || columnResolver == null || tunnelStyle == null || gradingEnvelopeWidth <= 0) {
            // Without terrain we cannot prove the cut is small — refuse the exception.
            return false;
        }
        // Coarse prism estimate: length × envelope × mean cover (capped at structure height).
        // Full voxel scans of hillsides always blow past the budget for any real mountain.
        int width = Math.max(1, gradingEnvelopeWidth);
        int structureHeight = Math.max(1, tunnelStyle.clearHeight());
        double lengthBlocks = 0.0;
        int coverSum = 0;
        int coverSamples = 0;
        for (int i = start; i < endExclusive && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            if (info == null || info.segment == null) {
                continue;
            }
            lengthBlocks += info.segment.distance / scale;
            for (double t : new double[] {0.25, 0.5, 0.75}) {
                Vec2d center = info.segment.start.lerp(info.segment.end, t);
                int designY = (int) Math.round(
                    info.targetStart + (info.targetEnd - info.targetStart) * t);
                if (!terrain.isChunkLoaded(columnResolver.worldX(center), columnResolver.worldZ(center))) {
                    return false;
                }
                int cover = Math.max(0, terrain.sampleSurfaceY(center) - designY);
                coverSum += Math.min(cover, structureHeight);
                coverSamples++;
            }
        }
        if (coverSamples <= 0 || lengthBlocks <= 1e-9) {
            return false;
        }
        double meanCover = (double) coverSum / coverSamples;
        int estimate = (int) Math.ceil(lengthBlocks * width * meanCover);
        return estimate > 0 && estimate <= MAX_EXCAVATION_BLOCKS;
    }
}
