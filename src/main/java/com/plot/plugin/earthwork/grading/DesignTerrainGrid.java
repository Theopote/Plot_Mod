package com.plot.plugin.earthwork.grading;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Site 级合成设计地形网格（运行时，不持久化）。
 */
public final class DesignTerrainGrid {
    private final Map<Long, DesignTerrainCell> cells = new LinkedHashMap<>();
    private int minTargetY = Integer.MAX_VALUE;
    private int maxTargetY = Integer.MIN_VALUE;
    private int coveredCells;
    private int excludedCells;
    private int unchangedCells;

    public Map<Long, DesignTerrainCell> cells() {
        return Collections.unmodifiableMap(cells);
    }

    public int cellCount() {
        return cells.size();
    }

    public int minTargetY() {
        return cells.isEmpty() ? 0 : minTargetY;
    }

    public int maxTargetY() {
        return cells.isEmpty() ? 0 : maxTargetY;
    }

    public int coveredCells() {
        return coveredCells;
    }

    public int excludedCells() {
        return excludedCells;
    }

    public int unchangedCells() {
        return unchangedCells;
    }

    public DesignTerrainCell getOrCreate(int worldX, int worldZ, DesignTerrainCell template) {
        long key = cellKey(worldX, worldZ);
        return cells.computeIfAbsent(key, ignored -> template);
    }

    public void put(int worldX, int worldZ, DesignTerrainCell cell) {
        cells.put(cellKey(worldX, worldZ), cell);
    }

    public DesignTerrainCell get(int worldX, int worldZ) {
        return cells.get(cellKey(worldX, worldZ));
    }

    public void finalizeStats() {
        coveredCells = 0;
        excludedCells = 0;
        unchangedCells = 0;
        minTargetY = Integer.MAX_VALUE;
        maxTargetY = Integer.MIN_VALUE;
        for (DesignTerrainCell cell : cells.values()) {
            minTargetY = Math.min(minTargetY, cell.targetY());
            maxTargetY = Math.max(maxTargetY, cell.targetY());
            if (cell.excluded()) {
                excludedCells++;
            } else if (cell.zoneId() != null && !cell.zoneId().isBlank()) {
                coveredCells++;
            }
            if (cell.deltaY() == 0) {
                unchangedCells++;
            }
        }
        if (cells.isEmpty()) {
            minTargetY = 0;
            maxTargetY = 0;
        }
    }

    public static long cellKey(int worldX, int worldZ) {
        return ((long) worldX << 32) | (worldZ & 0xFFFFFFFFL);
    }

    /** 参与土方格点的内容指纹，用于图表缓存失效。 */
    public static long earthworkContentFingerprint(DesignTerrainGrid grid) {
        if (grid == null || grid.cellCount() == 0) {
            return 0L;
        }
        long hash = 17L + grid.cellCount();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            hash = 31L * hash + cell.worldX();
            hash = 31L * hash + cell.worldZ();
            hash = 31L * hash + cell.existingGroundY();
            hash = 31L * hash + cell.targetY();
        }
        return hash;
    }
}
