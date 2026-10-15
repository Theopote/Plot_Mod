package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.pipeline.construction.RoadEarthworkPolicy;

import java.util.*;

/** 单次 edge 构建的不可变施工计划（mask + envelope）。 */
public final class RoadCorridorPlan {
    private final RoadSurfaceMask surfaceMask = new RoadSurfaceMask();
    private final Map<ColumnKey, ClearanceCell> clearanceCells = new LinkedHashMap<>();
    private final Map<ColumnKey, SlopeColumnCell> slopeCells = new LinkedHashMap<>();
    private final Set<ColumnKey> incompleteSlopeColumns = new HashSet<>();
    /** Shoulder batter horizontal run hit {@link com.plot.plugin.road.geometry.RoadCorridorWidth#MAX_SLOPE_BATTER_RUN_BLOCKS}. */
    private int slopeBatterWidthCapCount;
    /** Outward slope columns that fail to connect smoothly to neighbors along the batter. */
    private int brokenSlopeSurfaceCount;
    /** Batter ended before connecting to natural ground (often after width cap). */
    private int unresolvedSlopeConstructionCount;
    /** Batter stopped at width cap before terrain; partial slope may still be emitted. */
    private int needsRetainingWallCount;
    /** ROAD columns missing adjacent shoulder coverage at the same elevation. */
    private int roadShoulderSeamGapCount;
    private final List<PortalBackfillSite> portalBackfillSites = new ArrayList<>();
    private final Set<ColumnKey> portalBackfillSiteKeys = new HashSet<>();

    public RoadSurfaceMask surfaceMask() {
        return surfaceMask;
    }

    public ClearanceCell clearanceCell(ColumnKey key) {
        return clearanceCells.get(key);
    }

    public Set<Map.Entry<ColumnKey, ClearanceCell>> clearanceEntries() {
        return clearanceCells.entrySet();
    }

    public SlopeColumnCell slopeCell(ColumnKey key) {
        return slopeCells.get(key);
    }

    public Set<Map.Entry<ColumnKey, SlopeColumnCell>> slopeEntries() {
        return slopeCells.entrySet();
    }

    void putClearance(ColumnKey key, ClearanceCell cell) {
        if (key == null || cell == null) {
            return;
        }
        ClearanceCell existing = clearanceCells.get(key);
        ClearanceCell merged = existing == null ? cell : mergeClearance(existing, cell);
        if (merged.constructionType() != null
                && RoadEarthworkPolicy.of(merged.constructionType()) == RoadEarthworkPolicy.STRUCTURE_ONLY) {
            slopeCells.remove(key);
        }
        clearanceCells.put(key, merged);
    }

    public List<PortalBackfillSite> portalBackfillSites() {
        return List.copyOf(portalBackfillSites);
    }

    void registerPortalBackfillSite(PortalBackfillSite site) {
        if (site == null || site.center() == null) {
            return;
        }
        ColumnKey key = new ColumnKey(
            (int) Math.round(site.center().x * 1000.0),
            (int) Math.round(site.center().y * 1000.0));
        if (!portalBackfillSiteKeys.add(key)) {
            return;
        }
        portalBackfillSites.add(site);
    }

    /**
     * Structure interiors outrank open road so overlapping columns keep bridge/tunnel
     * construction type. Structure columns keep their own vertical envelope.
     */
    static ClearanceCell mergeClearance(ClearanceCell existing, ClearanceCell incoming) {
        int existingRank = clearanceMergeRank(existing.constructionType());
        int incomingRank = clearanceMergeRank(incoming.constructionType());
        if (incomingRank > existingRank) {
            return preferWithExpandedEnvelope(incoming, existing);
        }
        if (incomingRank < existingRank) {
            return preferWithExpandedEnvelope(existing, incoming);
        }
        ClearanceCell portalPreferred = portalPreferred(existing, incoming);
        if (portalPreferred != null) {
            ClearanceCell other = portalPreferred == existing ? incoming : existing;
            return preferWithExpandedEnvelope(portalPreferred, other);
        }
        if (incoming.clearTopY() > existing.clearTopY()) {
            return withMergedLateral(incoming, existing);
        }
        if (incoming.clearTopY() == existing.clearTopY()
                && incoming.designSurfaceY() > existing.designSurfaceY()) {
            return withMergedLateral(incoming, existing);
        }
        return withMergedLateral(existing, incoming);
    }

    private static ClearanceCell portalPreferred(ClearanceCell a, ClearanceCell b) {
        if (a.mode() == RoadCorridorConstructionMode.PORTAL) {
            return a;
        }
        if (b.mode() == RoadCorridorConstructionMode.PORTAL) {
            return b;
        }
        return null;
    }

    private static ClearanceCell preferWithExpandedEnvelope(ClearanceCell preferred, ClearanceCell other) {
        if (isStructureEnvelope(preferred)) {
            return withMergedLateral(preferred, other);
        }
        int clearTopY = Math.max(preferred.clearTopY(), other.clearTopY());
        if (clearTopY == preferred.clearTopY()) {
            return withMergedLateral(preferred, other);
        }
        return new ClearanceCell(
            preferred.planPoint(),
            preferred.designSurfaceY(),
            clearTopY,
            preferred.mode(),
            preferred.constructionType(),
            mergedLateral(preferred, other));
    }

    private static boolean isStructureEnvelope(ClearanceCell cell) {
        return clearanceMergeRank(cell.constructionType()) >= 1;
    }

    private static ClearanceCell withMergedLateral(ClearanceCell primary, ClearanceCell secondary) {
        Integer lateral = mergedLateral(primary, secondary);
        if (Objects.equals(lateral, primary.corridorLateral())) {
            return primary;
        }
        return new ClearanceCell(
            primary.planPoint(),
            primary.designSurfaceY(),
            primary.clearTopY(),
            primary.mode(),
            primary.constructionType(),
            lateral);
    }

    private static Integer mergedLateral(ClearanceCell a, ClearanceCell b) {
        return a.corridorLateral() != null ? a.corridorLateral() : b.corridorLateral();
    }

    /** Higher rank wins when open corridor and structure volumes share a world column. */
    static int clearanceMergeRank(com.plot.plugin.road.RoadConstructionType type) {
        return switch (RoadEarthworkPolicy.of(type)) {
            case STRUCTURE_ONLY -> 2;
            case TRANSITION_BATTER -> 1;
            case NORMAL_BATTER -> 0;
        };
    }

    SlopeColumnCell slopeCellOrCreate(ColumnKey key, com.plot.api.geometry.Vec2d planPoint) {
        return slopeCells.computeIfAbsent(key, ignored -> new SlopeColumnCell(planPoint));
    }

    void clearSlopeCells() {
        slopeCells.clear();
        incompleteSlopeColumns.clear();
    }

    void markSlopeColumnIncomplete(ColumnKey key) {
        if (key != null) {
            incompleteSlopeColumns.add(key);
            slopeCells.remove(key);
        }
    }

    public boolean isSlopeColumnIncomplete(ColumnKey key) {
        return key != null && incompleteSlopeColumns.contains(key);
    }

    public int slopeBatterWidthCapCount() {
        return slopeBatterWidthCapCount;
    }

    public int brokenSlopeSurfaceCount() {
        return brokenSlopeSurfaceCount;
    }

    void noteSlopeBatterWidthCap() {
        slopeBatterWidthCapCount++;
    }

    public int unresolvedSlopeConstructionCount() {
        return unresolvedSlopeConstructionCount;
    }

    void noteUnresolvedSlopeConstruction() {
        unresolvedSlopeConstructionCount++;
    }

    public int needsRetainingWallCount() {
        return needsRetainingWallCount;
    }

    void noteNeedsRetainingWall() {
        needsRetainingWallCount++;
    }

    public int roadShoulderSeamGapCount() {
        return roadShoulderSeamGapCount;
    }

    void setRoadShoulderSeamGapCount(int count) {
        roadShoulderSeamGapCount = Math.max(0, count);
    }

    void setBrokenSlopeSurfaceCount(int count) {
        brokenSlopeSurfaceCount = Math.max(0, count);
    }

    void removeColumn(ColumnKey key) {
        if (key == null) {
            return;
        }
        surfaceMask.remove(key);
        clearanceCells.remove(key);
        slopeCells.remove(key);
        incompleteSlopeColumns.remove(key);
    }
}
