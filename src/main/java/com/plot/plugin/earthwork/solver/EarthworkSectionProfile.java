package com.plot.plugin.earthwork.solver;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 沿场地较长方向取一列格点，对比现状地面与设计面（剖面 / Before-After）。
 */
public final class EarthworkSectionProfile {
    public static final EarthworkSectionProfile EMPTY = new EarthworkSectionProfile(List.of(), true);

    private final List<Station> stations;
    private final boolean alongX;

    public record Station(int worldX, int worldZ, int existingY, int designY) {
        public int cut() {
            return Math.max(0, existingY - designY);
        }

        public int fill() {
            return Math.max(0, designY - existingY);
        }
    }

    private EarthworkSectionProfile(List<Station> stations, boolean alongX) {
        this.stations = List.copyOf(stations);
        this.alongX = alongX;
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
        int mid = alongX ? (minZ + maxZ) / 2 : (minX + maxX) / 2;
        List<Station> stations = slice(cells, alongX, mid, 0);
        if (stations.size() < 2) {
            stations = slice(cells, alongX, mid, 1);
        }
        stations.sort(alongX
            ? Comparator.comparingInt(Station::worldX).thenComparingInt(Station::worldZ)
            : Comparator.comparingInt(Station::worldZ).thenComparingInt(Station::worldX));
        return new EarthworkSectionProfile(stations, alongX);
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
            return new SectionCutLine(0, 0, 0, alongX);
        }
        int primaryMin = Integer.MAX_VALUE;
        int primaryMax = Integer.MIN_VALUE;
        int crossSum = 0;
        for (Station station : stations) {
            int primary = alongX ? station.worldX() : station.worldZ();
            int cross = alongX ? station.worldZ() : station.worldX();
            primaryMin = Math.min(primaryMin, primary);
            primaryMax = Math.max(primaryMax, primary);
            crossSum += cross;
        }
        int crossMid = crossSum / stations.size();
        return new SectionCutLine(primaryMin, primaryMax, crossMid, alongX);
    }

    public record SectionCutLine(int primaryMin, int primaryMax, int crossMid, boolean alongX) {
    }

    private static List<Station> slice(
            List<DesignTerrainCell> cells,
            boolean alongX,
            int mid,
            int tolerance) {
        List<Station> stations = new ArrayList<>();
        for (DesignTerrainCell cell : cells) {
            int axis = alongX ? cell.worldZ() : cell.worldX();
            if (Math.abs(axis - mid) > tolerance) {
                continue;
            }
            stations.add(new Station(cell.worldX(), cell.worldZ(), cell.existingGroundY(), cell.targetY()));
        }
        return stations;
    }
}
