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
