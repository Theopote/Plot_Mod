package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 检测场地内设计分区轮廓重叠。
 */
public final class ZoneOverlapAnalyzer {

    public record ZoneOverlap(
            String zoneIdA,
            String zoneNameA,
            int priorityA,
            double areaA,
            String zoneIdB,
            String zoneNameB,
            int priorityB,
            double areaB,
            int overlapCells) {

        public boolean involves(String zoneId) {
            return zoneIdA.equals(zoneId) || zoneIdB.equals(zoneId);
        }

        public String winnerZoneId() {
            return winnerZoneId(CompositionPolicy.DEFAULT);
        }

        public String winnerZoneName() {
            String winnerId = winnerZoneId();
            return winnerId.equals(zoneIdA) ? zoneNameA : zoneNameB;
        }

        /**
         * 按场地合成策略解析重叠区胜出分区。
         */
        public String resolveWinner(CompositionPolicy policy) {
            CompositionPolicy safePolicy = policy != null ? policy : CompositionPolicy.DEFAULT;
            return winnerZoneId(safePolicy);
        }

        private String winnerZoneId(CompositionPolicy policy) {
            String resolution = policy.getOverlapResolution();
            if (CompositionPolicy.OVERLAP_LARGEST_ZONE_WINS.equals(resolution)) {
                if (Math.abs(areaA - areaB) > 1e-6) {
                    return areaA > areaB ? zoneIdA : zoneIdB;
                }
            } else if (!CompositionPolicy.OVERLAP_HIGHEST_PRIORITY_WINS.equals(resolution)) {
                return winnerByPriorityThenArea();
            }
            return winnerByPriorityThenArea();
        }

        private String winnerByPriorityThenArea() {
            if (priorityA != priorityB) {
                return priorityA > priorityB ? zoneIdA : zoneIdB;
            }
            if (Math.abs(areaA - areaB) > 1e-6) {
                return areaA < areaB ? zoneIdA : zoneIdB;
            }
            return zoneIdA.compareToIgnoreCase(zoneIdB) <= 0 ? zoneIdA : zoneIdB;
        }
    }

    private ZoneOverlapAnalyzer() {
    }

    public static List<ZoneOverlap> findOverlaps(EarthworkSite site) {
        if (site == null || site.getZoneCount() < 2) {
            return List.of();
        }
        List<ZoneCandidate> candidates = new ArrayList<>();
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            List<Vec2d> points = zone.getOuterPoints();
            if (points.size() < 3) {
                continue;
            }
            candidates.add(new ZoneCandidate(
                zone.getId(),
                zone.getName(),
                zone.getPriority(),
                Math.abs(zone.computeArea()),
                EarthworkGeometryUtils.toPolygon(points),
                EarthworkGeometryUtils.collectFootprintCellCenters(points)));
        }

        List<ZoneOverlap> overlaps = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < candidates.size(); leftIndex++) {
            ZoneCandidate left = candidates.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < candidates.size(); rightIndex++) {
                ZoneCandidate right = candidates.get(rightIndex);
                int overlapCells = estimateOverlapCells(left, right);
                if (overlapCells <= 0) {
                    continue;
                }
                overlaps.add(new ZoneOverlap(
                    left.id(),
                    left.name(),
                    left.priority(),
                    left.area(),
                    right.id(),
                    right.name(),
                    right.priority(),
                    right.area(),
                    overlapCells));
            }
        }
        overlaps.sort(Comparator
            .comparingInt(ZoneOverlap::overlapCells).reversed()
            .thenComparing(ZoneOverlap::zoneNameA, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ZoneOverlap::zoneNameB, String.CASE_INSENSITIVE_ORDER));
        return overlaps;
    }

    public static List<ZoneOverlap> findOverlapsInvolving(EarthworkSite site, String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return List.of();
        }
        return findOverlaps(site).stream()
            .filter(overlap -> overlap.involves(zoneId))
            .toList();
    }

    private static int estimateOverlapCells(ZoneCandidate left, ZoneCandidate right) {
        if (!boundsOverlap(left.polygon(), right.polygon())) {
            return 0;
        }
        int overlap = countCellsInside(left.cells(), right.polygon());
        if (overlap > 0) {
            return overlap;
        }
        return countCellsInside(right.cells(), left.polygon());
    }

    private static int countCellsInside(List<Vec2d> cells, Polygon polygon) {
        int count = 0;
        for (Vec2d cell : cells) {
            if (cell != null && polygon.contains(cell)) {
                count++;
            }
        }
        return count;
    }

    private static boolean boundsOverlap(Polygon left, Polygon right) {
        return left.getBoundingBox().intersects(right.getBoundingBox());
    }

    private record ZoneCandidate(
            String id,
            String name,
            int priority,
            double area,
            Polygon polygon,
            List<Vec2d> cells) {
    }
}
