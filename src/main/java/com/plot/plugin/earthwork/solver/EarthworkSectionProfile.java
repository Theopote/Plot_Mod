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
        List.of(), true, 0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1, false);

    /** 斜剖时允许的最大垂距（格）；略大于半对角，保证斜线能咬住相邻区格。 */
    static final double MAX_PERP_DISTANCE = 0.85;
    /** 沿程间距超过该值视为空洞，剖面折线断开（斜向邻格 √2≈1.41 仍相连）。 */
    static final double POLYLINE_BREAK_GAP = 1.75;
    /** 画布剖切线最短半长（格）；无交点或跨度过短时仍保持可视长度。 */
    static final double MIN_CUT_LINE_HALF_LENGTH = 12.0;

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
    /** 无采样站时仍可画引导剖切线（手动旋到区外）。 */
    private final boolean cutLineVisible;
    private final double firstAlong;

    public record Station(int worldX, int worldZ, int existingY, int designY, String zoneId) {
        public Station(int worldX, int worldZ, int existingY, int designY) {
            this(worldX, worldZ, existingY, designY, "");
        }

        public int cut() {
            return Math.max(0, existingY - designY);
        }

        public int fill() {
            return Math.max(0, designY - existingY);
        }

        public boolean zoned() {
            return zoneId != null && !zoneId.isBlank();
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
        this(stations, alongX, crossAxisCoordinate, originX, originZ, dirX, dirZ, viewNx, viewNz, viewSign, true);
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
            int viewSign,
            boolean cutLineVisible) {
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
        this.cutLineVisible = cutLineVisible;
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
        double zoneMinAlong = Double.POSITIVE_INFINITY;
        double zoneMaxAlong = Double.NEGATIVE_INFINITY;
        boolean anyZone = false;
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
            boolean zoned = cell.zoneId() != null && !cell.zoneId().isBlank();
            if (!zoned) {
                continue; // 剖面只取已归属分区，区间空隙断开，多区各自成段
            }
            scored.add(new ScoredStation(
                new Station(cell.worldX(), cell.worldZ(), cell.existingGroundY(), cell.targetY(), cell.zoneId()),
                along,
                Math.abs(perp),
                true));
            if (zoned) {
                zoneMinAlong = Math.min(zoneMinAlong, along);
                zoneMaxAlong = Math.max(zoneMaxAlong, along);
                anyZone = true;
            }
        }
        if (scored.size() < 2) {
            return guideFromSpec(spec);
        }
        // 有分区时只保留分区沿程包络（+半格），避免场地空白把多区挤成图上一小段
        double rangeMin;
        double rangeMax;
        if (anyZone) {
            rangeMin = zoneMinAlong - 0.5;
            rangeMax = zoneMaxAlong + 0.5;
        } else {
            rangeMin = Double.POSITIVE_INFINITY;
            rangeMax = Double.NEGATIVE_INFINITY;
            for (ScoredStation s : scored) {
                rangeMin = Math.min(rangeMin, s.along());
                rangeMax = Math.max(rangeMax, s.along());
            }
        }
        // 沿截线每 1 格取一站：优先已归属分区，再取垂距更近
        List<Station> stations = new ArrayList<>();
        int stepStart = (int) Math.floor(rangeMin);
        int stepEnd = (int) Math.ceil(rangeMax);
        for (int step = stepStart; step <= stepEnd; step++) {
            double t = step;
            ScoredStation best = null;
            for (ScoredStation candidate : scored) {
                if (candidate.along() < rangeMin || candidate.along() > rangeMax) {
                    continue;
                }
                if (Math.abs(candidate.along() - t) > 0.55) {
                    continue;
                }
                if (best == null
                    || (candidate.zoned() && !best.zoned())
                    || (candidate.zoned() == best.zoned() && candidate.perpAbs() < best.perpAbs())
                    || (candidate.zoned() == best.zoned()
                        && Math.abs(candidate.perpAbs() - best.perpAbs()) < 1e-9
                        && Math.abs(candidate.along() - t) < Math.abs(best.along() - t))) {
                    best = candidate;
                }
            }
            if (best == null) {
                continue;
            }
            if (!stations.isEmpty()) {
                Station prev = stations.getLast();
                if (prev.worldX() == best.station().worldX() && prev.worldZ() == best.station().worldZ()) {
                    continue;
                }
            }
            stations.add(best.station());
        }
        if (stations.size() < 2) {
            return guideFromSpec(spec);
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

    /** 无交点时仍保留走向与最短可视剖切线，方便继续旋转/平移。 */
    static EarthworkSectionProfile guideFromSpec(EarthworkSectionCutSpec spec) {
        if (spec == null || !spec.manual()) {
            return EMPTY;
        }
        boolean alongX = Math.abs(spec.dirX()) >= Math.abs(spec.dirZ());
        int crossCoord = alongX
            ? (int) Math.floor(spec.originZ())
            : (int) Math.floor(spec.originX());
        return new EarthworkSectionProfile(
            List.of(),
            alongX,
            crossCoord,
            spec.originX(),
            spec.originZ(),
            spec.dirX(),
            spec.dirZ(),
            spec.viewNx(),
            spec.viewNz(),
            spec.viewSign(),
            true);
    }

    /** 无足够采样站，图表不画折线。 */
    public boolean isEmpty() {
        return stations.size() < 2;
    }

    /** 画布是否绘制剖切线（含旋到区外的最短引导线）。 */
    public boolean hasDrawableCutLine() {
        return cutLineVisible;
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

    /** 沿剖面方向的方块距离（首站为 0），四舍五入；图表定位请用 {@link #axisDistanceExact}。 */
    public int axisDistance(Station station) {
        return (int) Math.round(axisDistanceExact(station));
    }

    /** 沿剖面方向的精确距离（首站为 0），斜剖时避免四舍五入造成断段。 */
    public double axisDistanceExact(Station station) {
        if (stations.isEmpty() || station == null) {
            return 0.0;
        }
        double along = projectAlongRelative(
            station.worldX() + 0.5, station.worldZ() + 0.5, originX, originZ, dirX, dirZ);
        return along - firstAlong;
    }

    public int axisSpan() {
        return Math.max(1, (int) Math.round(axisSpanExact()));
    }

    public double axisSpanExact() {
        if (stations.size() < 2) {
            return 1.0;
        }
        return Math.max(1e-6, axisDistanceExact(stations.getLast()));
    }

    /** 相邻采样点在剖面轴向上的间距（格，四舍五入）。 */
    public int axisGap(Station a, Station b) {
        return (int) Math.round(axisGapExact(a, b));
    }

    public double axisGapExact(Station a, Station b) {
        if (a == null || b == null) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(axisDistanceExact(b) - axisDistanceExact(a));
    }

    /** 间距过大视为空洞，剖面折线应断开（斜向邻格仍相连）。 */
    public boolean shouldBreakPolyline(Station a, Station b) {
        return axisGapExact(a, b) > POLYLINE_BREAK_GAP;
    }

    /** 世界 XZ 上的剖面截线几何（端点 + 看向），供画布叠加；不足最短长度时向两端扩展。 */
    public SectionCutLine sectionCutLine() {
        double minAlong;
        double maxAlong;
        int primaryMin = 0;
        int primaryMax = 0;
        if (stations.isEmpty()) {
            minAlong = -MIN_CUT_LINE_HALF_LENGTH;
            maxAlong = MIN_CUT_LINE_HALF_LENGTH;
        } else {
            minAlong = Double.POSITIVE_INFINITY;
            maxAlong = Double.NEGATIVE_INFINITY;
            primaryMin = Integer.MAX_VALUE;
            primaryMax = Integer.MIN_VALUE;
            for (Station station : stations) {
                double along = projectAlongRelative(
                    station.worldX() + 0.5, station.worldZ() + 0.5, originX, originZ, dirX, dirZ);
                minAlong = Math.min(minAlong, along);
                maxAlong = Math.max(maxAlong, along);
                int primary = alongX ? station.worldX() : station.worldZ();
                primaryMin = Math.min(primaryMin, primary);
                primaryMax = Math.max(primaryMax, primary);
            }
            double span = maxAlong - minAlong;
            if (span < MIN_CUT_LINE_HALF_LENGTH * 2.0) {
                double mid = (minAlong + maxAlong) * 0.5;
                minAlong = mid - MIN_CUT_LINE_HALF_LENGTH;
                maxAlong = mid + MIN_CUT_LINE_HALF_LENGTH;
            }
        }
        double startX = originX + minAlong * dirX;
        double startZ = originZ + minAlong * dirZ;
        double endX = originX + maxAlong * dirX;
        double endZ = originZ + maxAlong * dirZ;
        return new SectionCutLine(
            startX, startZ, endX, endZ,
            viewNx, viewNz,
            primaryMin, primaryMax, crossAxisCoordinate, alongX);
    }

    /**
     * 从当前剖面推导可编辑规格（中点 + 走向），供面板首次手动操作。
     */
    public EarthworkSectionCutSpec toEditableSpec() {
        if (!hasDrawableCutLine()) {
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

    private record ScoredStation(Station station, double along, double perpAbs, boolean zoned) {
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
        int bestZoneCount = -1;
        for (int cross : crossValues) {
            List<Station> sliced = sliceExact(cells, alongX, cross);
            int count = sliced.size();
            if (count < 2) {
                continue;
            }
            int zoneCount = (int) sliced.stream().map(Station::zoneId).filter(id -> id != null && !id.isBlank()).distinct().count();
            int distance = Math.abs(cross - preferredCross);
            if (zoneCount > bestZoneCount
                || (zoneCount == bestZoneCount && count > bestCount)
                || (zoneCount == bestZoneCount && count == bestCount && distance < bestDistance)) {
                bestZoneCount = zoneCount;
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
            String zoneId = cell.zoneId() != null ? cell.zoneId() : "";
            if (zoneId.isBlank()) {
                continue; // 自动剖面同样只取分区格，多区在同列时各自成段
            }
            stations.add(new Station(cell.worldX(), cell.worldZ(), cell.existingGroundY(), cell.targetY(), zoneId));
        }
        return stations;
    }
}
