package com.plot.plugin.earthwork.solver;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 沿截线对比现状地面与设计面（剖面 / Before-After）。
 * <p>
 * 自动模式：沿场地较长方向取一列轴对齐格点。
 * 手动模式：按 {@link EarthworkSectionCutSpec} 任意角取距线足够近的格点。
 */
public final class EarthworkSectionProfile {
    public static final EarthworkSectionProfile EMPTY = new EarthworkSectionProfile(
        List.of(), true, 0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1);

    /** 斜剖时允许的最大垂距（格），约半个对角。 */
    static final double MAX_PERP_DISTANCE = 0.7071;

    private final List<Station> stations;
    private final boolean alongX;
    /** 截线在世界 XZ 上的固定坐标：沿 X 剖面时为 worldZ，沿 Z 剖面时为 worldX（自动模式）。 */
    private final int crossAxisCoordinate;
    private final double originX;
    private final double originZ;
    private final double dirX;
    private final double dirZ;
    private final double viewNx;
    private final double viewNz;
    private final int viewSign;
    private final double firstAlong;

    public record Station(int worldX, int worldZ, int existingY, int designY) {
        public int cut() {
            return Math.max(0, existingY - designY);
        }

        public int fill() {
            return Math.max(0, designY - existingY);
        }
    }

    private EarthworkSectionProfile(
            List<Station> stations,
            boolean alongX,
            int crossAxisCoordinate,
            double originX,
            double originZ,
            double dirX,
            double dirZ,
            double viewNx,
            double viewNz,
            int viewSign) {
        this.stations = List.copyOf(stations);
        this.alongX = alongX;
        this.crossAxisCoordinate = crossAxisCoordinate;
        this.originX = originX;
        this.originZ = originZ;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.viewNx = viewNx;
        this.viewNz = viewNz;
        this.viewSign = viewSign >= 0 ? 1 : -1;
        this.firstAlong = stations.isEmpty()
            ? 0.0
            : projectAlongRelative(
                stations.getFirst().worldX() + 0.5,
                stations.getFirst().worldZ() + 0.5,
                originX,
                originZ,
                dirX,
                dirZ);
    }

    public static EarthworkSectionProfile fromGrid(DesignTerrainGrid grid) {
        return fromGrid(grid, EarthworkSectionCutSpec.AUTOMATIC);
    }

    public static EarthworkSectionProfile fromGrid(DesignTerrainGrid grid, EarthworkSectionCutSpec spec) {
        if (grid == null || grid.cellCount() == 0) {
            return EMPTY;
        }
        if (spec != null && spec.manual()) {
            return fromSpec(grid, spec);
        }
        return fromAutomatic(grid);
    }

    private static EarthworkSectionProfile fromAutomatic(DesignTerrainGrid grid) {
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
        double dirX = alongX ? 1.0 : 0.0;
        double dirZ = alongX ? 0.0 : 1.0;
        // 自动：看向取截线左侧（+Z 或 -X）
        double viewNx = -dirZ;
        double viewNz = dirX;
        double originX = alongX
            ? (minX + maxX) * 0.5 + 0.5
            : crossCoord + 0.5;
        double originZ = alongX
            ? crossCoord + 0.5
            : (minZ + maxZ) * 0.5 + 0.5;
        return new EarthworkSectionProfile(
            stations, alongX, crossCoord, originX, originZ, dirX, dirZ, viewNx, viewNz, 1);
    }

    private static EarthworkSectionProfile fromSpec(DesignTerrainGrid grid, EarthworkSectionCutSpec spec) {
        double dirX = spec.dirX();
        double dirZ = spec.dirZ();
        double originX = spec.originX();
        double originZ = spec.originZ();
        List<ScoredStation> scored = new ArrayList<>();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            double cx = cell.worldX() + 0.5;
            double cz = cell.worldZ() + 0.5;
            double relX = cx - originX;
            double relZ = cz - originZ;
            double along = relX * dirX + relZ * dirZ;
            double perp = relX * (-dirZ) + relZ * dirX;
            if (Math.abs(perp) > MAX_PERP_DISTANCE) {
                continue;
            }
            scored.add(new ScoredStation(
                new Station(cell.worldX(), cell.worldZ(), cell.existingGroundY(), cell.targetY()),
                along,
                Math.abs(perp)));
        }
        if (scored.size() < 2) {
            return EMPTY;
        }
        scored.sort(Comparator
            .comparingDouble(ScoredStation::along)
            .thenComparingDouble(ScoredStation::perpAbs)
            .thenComparingInt(s -> s.station().worldX())
            .thenComparingInt(s -> s.station().worldZ()));
        // 同一沿程附近只保留垂距更近的一格，避免斜线双计
        List<Station> stations = new ArrayList<>();
        ScoredStation lastKept = null;
        for (ScoredStation candidate : scored) {
            if (lastKept != null && Math.abs(candidate.along() - lastKept.along()) < 0.35) {
                if (candidate.perpAbs() < lastKept.perpAbs()) {
                    stations.set(stations.size() - 1, candidate.station());
                    lastKept = candidate;
                }
                continue;
            }
            stations.add(candidate.station());
            lastKept = candidate;
        }
        if (stations.size() < 2) {
            return EMPTY;
        }
        boolean alongX = Math.abs(dirX) >= Math.abs(dirZ);
        int crossCoord = alongX
            ? (int) Math.floor(spec.originZ())
            : (int) Math.floor(spec.originX());
        return new EarthworkSectionProfile(
            stations,
            alongX,
            crossCoord,
            originX,
            originZ,
            dirX,
            dirZ,
            spec.viewNx(),
            spec.viewNz(),
            spec.viewSign());
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

    public double dirX() {
        return dirX;
    }

    public double dirZ() {
        return dirZ;
    }

    public double viewNx() {
        return viewNx;
    }

    public double viewNz() {
        return viewNz;
    }

    public int viewSign() {
        return viewSign;
    }

    /** 沿剖面方向的方块距离（首站为 0）。 */
    public int axisDistance(Station station) {
        if (stations.isEmpty() || station == null) {
            return 0;
        }
        double along = projectAlongRelative(
            station.worldX() + 0.5, station.worldZ() + 0.5, originX, originZ, dirX, dirZ);
        return (int) Math.round(along - firstAlong);
    }

    public int axisSpan() {
        if (stations.size() < 2) {
            return 1;
        }
        return Math.max(1, axisDistance(stations.getLast()));
    }

    /** 相邻采样点在剖面轴向上的间距（格）；大于 1 表示应断线。 */
    public int axisGap(Station a, Station b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }
        return Math.abs(axisDistance(b) - axisDistance(a));
    }

    /** 世界 XZ 上的剖面截线几何（端点 + 看向），供画布叠加。 */
    public SectionCutLine sectionCutLine() {
        if (stations.isEmpty()) {
            return new SectionCutLine(
                originX, originZ,
                originX, originZ,
                viewNx, viewNz,
                0, 0, crossAxisCoordinate, alongX);
        }
        double minAlong = Double.POSITIVE_INFINITY;
        double maxAlong = Double.NEGATIVE_INFINITY;
        for (Station station : stations) {
            double along = projectAlongRelative(
                station.worldX() + 0.5, station.worldZ() + 0.5, originX, originZ, dirX, dirZ);
            minAlong = Math.min(minAlong, along);
            maxAlong = Math.max(maxAlong, along);
        }
        double startX = originX + minAlong * dirX;
        double startZ = originZ + minAlong * dirZ;
        double endX = originX + maxAlong * dirX;
        double endZ = originZ + maxAlong * dirZ;

        int primaryMin = Integer.MAX_VALUE;
        int primaryMax = Integer.MIN_VALUE;
        for (Station station : stations) {
            int primary = alongX ? station.worldX() : station.worldZ();
            primaryMin = Math.min(primaryMin, primary);
            primaryMax = Math.max(primaryMax, primary);
        }
        return new SectionCutLine(
            startX, startZ, endX, endZ,
            viewNx, viewNz,
            primaryMin, primaryMax, crossAxisCoordinate, alongX);
    }

    /**
     * 从当前剖面推导可编辑规格（中点 + 走向），供面板首次手动操作。
     */
    public EarthworkSectionCutSpec toEditableSpec() {
        if (isEmpty()) {
            return EarthworkSectionCutSpec.AUTOMATIC;
        }
        double angle = Math.atan2(dirZ, dirX);
        return new EarthworkSectionCutSpec(originX, originZ, angle, viewSign, true);
    }

    /** 场地参与格点 AABB 中心，供水平/竖直按钮锚定。 */
    public static double[] gridCenter(DesignTerrainGrid grid) {
        if (grid == null || grid.cellCount() == 0) {
            return new double[] {0.5, 0.5};
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            minX = Math.min(minX, cell.worldX());
            maxX = Math.max(maxX, cell.worldX());
            minZ = Math.min(minZ, cell.worldZ());
            maxZ = Math.max(maxZ, cell.worldZ());
            any = true;
        }
        if (!any) {
            return new double[] {0.5, 0.5};
        }
        return new double[] {(minX + maxX) * 0.5 + 0.5, (minZ + maxZ) * 0.5 + 0.5};
    }

    public record SectionCutLine(
            double startWorldX,
            double startWorldZ,
            double endWorldX,
            double endWorldZ,
            double viewNx,
            double viewNz,
            int primaryMin,
            int primaryMax,
            int crossMid,
            boolean alongX) {
    }

    private record ScoredStation(Station station, double along, double perpAbs) {
    }

    private static double projectAlongRelative(
            double x,
            double z,
            double originX,
            double originZ,
            double dirX,
            double dirZ) {
        return (x - originX) * dirX + (z - originZ) * dirZ;
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
