package com.plot.plugin.earthwork.solver;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 沿场地较长方向取一列格点，对比现状地面与设计面（剖面 / Before-After）。
 * <p>
 * 采样严格落在一条世界方块截线上（固定 X 或固定 Z），不混合相邻列。
 */
public final class EarthworkSectionProfile {
    public static final EarthworkSectionProfile EMPTY = new EarthworkSectionProfile(List.of(), true, 0);

    private final List<Station> stations;
    private final boolean alongX;
    /** 截线在世界 XZ 上的固定坐标：沿 X 剖面时为 worldZ，沿 Z 剖面时为 worldX。 */
    private final int crossAxisCoordinate;

    public record Station(int worldX, int worldZ, int existingY, int designY) {
        public int cut() {
            return Math.max(0, existingY - designY);
        }

        public int fill() {
            return Math.max(0, designY - existingY);
        }
    }

    private EarthworkSectionProfile(List<Station> stations, boolean alongX, int crossAxisCoordinate) {
        this.stations = List.copyOf(stations);
        this.alongX = alongX;
        this.crossAxisCoordinate = crossAxisCoordinate;
    }

    public static EarthworkSectionProfile fromGrid(DesignTerrainGrid grid) {
        if (grid == null || grid.cellCount() == 0) {
            return EMPTY;
        }
        List<DesignTerrainCell> cells = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            cells.add(cell);
            minX = Math.min(minX, cell.worldX());
            maxX = Math.max(maxX, cell.worldX());
            minZ = Math.min(minZ, cell.worldZ());
            maxZ = Math.max(maxZ, cell.worldZ());
        }
        if (cells.isEmpty()) {
            return EMPTY;
        }
        boolean alongX = (maxX - minX) >= (maxZ - minZ);
        int midCross = alongX ? (minZ + maxZ) / 2 : (minX + maxX) / 2;
        int crossCoord = chooseCutLineCoordinate(cells, alongX, midCross);
        List<Station> stations = sliceExact(cells, alongX, crossCoord);
        stations.sort(alongX
            ? Comparator.comparingInt(Station::worldX).thenComparingInt(Station::worldZ)
            : Comparator.comparingInt(Station::worldZ).thenComparingInt(Station::worldX));
        return new EarthworkSectionProfile(stations, alongX, crossCoord);
    }

    public boolean isEmpty() {
        return stations.size() < 2;
    }

    public List<Station> stations() {
        return stations;
    }

    public boolean alongX() {
        return alongX;
    }

    public int crossAxisCoordinate() {
        return crossAxisCoordinate;
    }

    /** 沿剖面方向的方块距离（首站为 0）。 */
    public int axisDistance(Station station) {
        if (stations.isEmpty() || station == null) {
            return 0;
        }
        Station origin = stations.get(0);
        return alongX
            ? station.worldX() - origin.worldX()
            : station.worldZ() - origin.worldZ();
    }

    public int axisSpan() {
        if (stations.size() < 2) {
            return 1;
        }
        return Math.max(1, axisDistance(stations.get(stations.size() - 1)));
    }

    /** 相邻采样点在剖面轴向上的间距（格）；大于 1 表示应断线。 */
    public int axisGap(Station a, Station b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }
        return alongX
            ? Math.abs(b.worldX() - a.worldX())
            : Math.abs(b.worldZ() - a.worldZ());
    }

    /** 世界 XZ 上的剖面截线端点（方块中心），供画布叠加。 */
    public SectionCutLine sectionCutLine() {
        if (stations.isEmpty()) {
            return new SectionCutLine(0, 0, crossAxisCoordinate, alongX);
        }
        int primaryMin = Integer.MAX_VALUE;
        int primaryMax = Integer.MIN_VALUE;
        for (Station station : stations) {
            int primary = alongX ? station.worldX() : station.worldZ();
            primaryMin = Math.min(primaryMin, primary);
            primaryMax = Math.max(primaryMax, primary);
        }
        return new SectionCutLine(primaryMin, primaryMax, crossAxisCoordinate, alongX);
    }

    public record SectionCutLine(int primaryMin, int primaryMax, int crossMid, boolean alongX) {
    }

    private static int chooseCutLineCoordinate(List<DesignTerrainCell> cells, boolean alongX, int preferredCross) {
        Set<Integer> crossValues = new HashSet<>();
        for (DesignTerrainCell cell : cells) {
            crossValues.add(alongX ? cell.worldZ() : cell.worldX());
        }
        int bestCross = preferredCross;
        int bestCount = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int cross : crossValues) {
            int count = sliceExact(cells, alongX, cross).size();
            if (count < 2) {
                continue;
            }
            int distance = Math.abs(cross - preferredCross);
            if (count > bestCount || (count == bestCount && distance < bestDistance)) {
                bestCount = count;
                bestDistance = distance;
                bestCross = cross;
            }
        }
        if (bestCount >= 2) {
            return bestCross;
        }
        for (int cross : crossValues) {
            int count = sliceExact(cells, alongX, cross).size();
            int distance = Math.abs(cross - preferredCross);
            if (count > bestCount || (count == bestCount && distance < bestDistance)) {
                bestCount = count;
                bestDistance = distance;
                bestCross = cross;
            }
        }
        return bestCross;
    }

    private static List<Station> sliceExact(List<DesignTerrainCell> cells, boolean alongX, int crossCoord) {
        List<Station> stations = new ArrayList<>();
        for (DesignTerrainCell cell : cells) {
            int cross = alongX ? cell.worldZ() : cell.worldX();
            if (cross != crossCoord) {
                continue;
            }
            stations.add(new Station(cell.worldX(), cell.worldZ(), cell.existingGroundY(), cell.targetY()));
        }
        return stations;
    }
}
