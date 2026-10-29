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
    /** 分区剖面沿程外扩下限（格）。 */
    public static final int ZONE_SECTION_MARGIN_BLOCKS = 16;

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
    /**
     * 画布剖切线沿程端点（相对 origin）；NaN 表示按采样站推算。
     * 分区剖面写入「区内跨度 ± margin」，即使一侧无外扩格也对称伸出。
     */
    private final double cutLineAlongMin;
    private final double cutLineAlongMax;
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
        this(
            stations, alongX, crossAxisCoordinate, originX, originZ, dirX, dirZ,
            viewNx, viewNz, viewSign, cutLineVisible, Double.NaN, Double.NaN);
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
            boolean cutLineVisible,
            double cutLineAlongMin,
            double cutLineAlongMax) {
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
        this.cutLineAlongMin = cutLineAlongMin;
        this.cutLineAlongMax = cutLineAlongMax;
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

    /**
     * 单分区剖面：沿截线覆盖该区格点，并向外扩 {@code max(16, 沿程跨度×25%)}；
     * 扩边取无归属格（现状），排除其他分区格。
     */
    public static EarthworkSectionProfile fromGridForZone(
            DesignTerrainGrid grid,
            String zoneId,
            EarthworkSectionCutSpec spec) {
        if (grid == null || grid.cellCount() == 0 || zoneId == null || zoneId.isBlank()) {
            return EMPTY;
        }
        List<DesignTerrainCell> zoneCells = collectZoneCells(grid, zoneId);
        if (zoneCells.isEmpty()) {
            return EMPTY;
        }
        EarthworkSectionCutSpec effective = (spec != null && spec.manual())
            ? spec
            : deriveAutoSpecForZone(zoneCells);
        return sliceZoneWithMargin(grid, zoneId, effective);
    }

    /** 分区格 AABB 中心，供剖面控件锚定。 */
    public static double[] gridCenterForZone(DesignTerrainGrid grid, String zoneId) {
        if (grid == null || zoneId == null || zoneId.isBlank()) {
            return new double[] {0.5, 0.5};
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (DesignTerrainCell cell : collectZoneCells(grid, zoneId)) {
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

    public static double zoneSectionMargin(double zoneAlongSpan) {
        return Math.max(ZONE_SECTION_MARGIN_BLOCKS, Math.abs(zoneAlongSpan) * 0.25);
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
        List<Station> stations = pickStationsByAlongBuckets(scored, rangeMin, rangeMax, null, null);
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

    private static List<DesignTerrainCell> collectZoneCells(DesignTerrainGrid grid, String zoneId) {
        List<DesignTerrainCell> cells = new ArrayList<>();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            if (zoneId.equals(cell.zoneId())) {
                cells.add(cell);
            }
        }
        return cells;
    }

    /** 沿分区较长方向取轴对齐截线（非 manual，每次按当前格重算）。 */
    private static EarthworkSectionCutSpec deriveAutoSpecForZone(List<DesignTerrainCell> zoneCells) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DesignTerrainCell cell : zoneCells) {
            minX = Math.min(minX, cell.worldX());
            maxX = Math.max(maxX, cell.worldX());
            minZ = Math.min(minZ, cell.worldZ());
            maxZ = Math.max(maxZ, cell.worldZ());
        }
        boolean alongX = (maxX - minX) >= (maxZ - minZ);
        int midCross = alongX ? (minZ + maxZ) / 2 : (minX + maxX) / 2;
        int crossCoord = chooseCutLineCoordinate(zoneCells, alongX, midCross);
        double originX = alongX ? (minX + maxX) * 0.5 + 0.5 : crossCoord + 0.5;
        double originZ = alongX ? crossCoord + 0.5 : (minZ + maxZ) * 0.5 + 0.5;
        double angle = alongX ? 0.0 : Math.PI / 2.0;
        return new EarthworkSectionCutSpec(originX, originZ, angle, 1, false);
    }

    private static EarthworkSectionProfile sliceZoneWithMargin(
            DesignTerrainGrid grid,
            String zoneId,
            EarthworkSectionCutSpec spec) {
        double dirX = spec.dirX();
        double dirZ = spec.dirZ();
        double originX = spec.originX();
        double originZ = spec.originZ();
        List<ScoredStation> scored = new ArrayList<>();
        // 沿程包络只统计截线走廊内的本区格
        double zoneMinAlong = Double.POSITIVE_INFINITY;
        double zoneMaxAlong = Double.NEGATIVE_INFINITY;
        boolean anyZoneCell = false;
        boolean anyOwnOnCorridor = false;
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            String cellZone = cell.zoneId() != null ? cell.zoneId() : "";
            boolean own = zoneId.equals(cellZone);
            boolean exterior = cellZone.isBlank();
            if (!own && !exterior) {
                continue;
            }
            if (own) {
                anyZoneCell = true;
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
            if (own) {
                zoneMinAlong = Math.min(zoneMinAlong, along);
                zoneMaxAlong = Math.max(zoneMaxAlong, along);
                anyOwnOnCorridor = true;
            }
            int existing = cell.existingGroundY();
            int design = own ? cell.targetY() : existing;
            String stationZone = own ? zoneId : "";
            scored.add(new ScoredStation(
                new Station(cell.worldX(), cell.worldZ(), existing, design, stationZone),
                along,
                Math.abs(perp),
                own));
        }
        if (!anyZoneCell || !anyOwnOnCorridor) {
            return guideFromSpec(spec);
        }
        double zoneSpan = zoneMaxAlong - zoneMinAlong;
        double lineMargin = zoneSectionMargin(zoneSpan);
        double sampleMargin = Math.clamp(zoneSpan * 0.5 + 2.0, 4.0, lineMargin);
        double sampleMin = zoneMinAlong - sampleMargin;
        double sampleMax = zoneMaxAlong + sampleMargin;
        double cutMin = zoneMinAlong - lineMargin;
        double cutMax = zoneMaxAlong + lineMargin;
        List<Station> stations = pickStationsByAlongBuckets(
            scored, sampleMin, sampleMax, zoneMinAlong, zoneMaxAlong);
        if (stations.size() < 2) {
            return guideFromSpec(spec, cutMin, cutMax);
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
            spec.viewSign(),
            true,
            cutMin,
            cutMax);
    }

    /**
     * 候选一次分桶：每个沿程步只保留最优格，复杂度约 O(C+S)，避免站×候选嵌套扫描。
     *
     * @param zoneMinAlong 非 null 时，区内沿程禁止空白格入站
     */
    static List<Station> pickStationsByAlongBuckets(
            List<ScoredStation> scored,
            double rangeMin,
            double rangeMax,
            Double zoneMinAlong,
            Double zoneMaxAlong) {
        int stepStart = (int) Math.floor(rangeMin);
        int stepEnd = (int) Math.ceil(rangeMax);
        if (stepEnd < stepStart) {
            return List.of();
        }
        int bucketCount = stepEnd - stepStart + 1;
        ScoredStation[] bestByStep = new ScoredStation[bucketCount];
        for (ScoredStation candidate : scored) {
            if (candidate.along() < rangeMin || candidate.along() > rangeMax) {
                continue;
            }
            int lo = Math.max(stepStart, (int) Math.ceil(candidate.along() - 0.55));
            int hi = Math.min(stepEnd, (int) Math.floor(candidate.along() + 0.55));
            for (int step = lo; step <= hi; step++) {
                boolean insideZone = zoneMinAlong != null
                    && zoneMaxAlong != null
                    && step >= zoneMinAlong - 0.55
                    && step <= zoneMaxAlong + 0.55;
                if (insideZone && !candidate.zoned()) {
                    continue;
                }
                int idx = step - stepStart;
                ScoredStation current = bestByStep[idx];
                if (current == null || isBetterStationCandidate(candidate, current, step)) {
                    bestByStep[idx] = candidate;
                }
            }
        }
        List<Station> stations = new ArrayList<>();
        for (ScoredStation best : bestByStep) {
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
        return stations;
    }

    private static boolean isBetterStationCandidate(ScoredStation candidate, ScoredStation best, int step) {
        double t = step;
        if (candidate.zoned() && !best.zoned()) {
            return true;
        }
        if (candidate.zoned() == best.zoned() && candidate.perpAbs() < best.perpAbs()) {
            return true;
        }
        return candidate.zoned() == best.zoned()
            && Math.abs(candidate.perpAbs() - best.perpAbs()) < 1e-9
            && Math.abs(candidate.along() - t) < Math.abs(best.along() - t);
    }

    /** 无交点时仍保留走向与最短可视剖切线，方便继续旋转/平移。 */
    static EarthworkSectionProfile guideFromSpec(EarthworkSectionCutSpec spec) {
        return guideFromSpec(spec, Double.NaN, Double.NaN);
    }

    static EarthworkSectionProfile guideFromSpec(
            EarthworkSectionCutSpec spec,
            double cutLineAlongMin,
            double cutLineAlongMax) {
        if (spec == null) {
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
            true,
            cutLineAlongMin,
            cutLineAlongMax);
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

    /** 剖面图横轴是否含截线外扩空白（区外对照）。 */
    public boolean hasChartAxisPadding() {
        return !Double.isNaN(cutLineAlongMin)
            && !Double.isNaN(cutLineAlongMax)
            && cutLineAlongMax >= cutLineAlongMin
            && !stations.isEmpty();
    }

    /** 剖面图横轴起点（相对首站，可为负，表示左侧空白外扩）。 */
    public double chartAxisMin() {
        if (!hasChartAxisPadding()) {
            return 0.0;
        }
        return Math.min(0.0, cutLineAlongMin - firstAlong);
    }

    /** 剖面图横轴终点（相对首站）。 */
    public double chartAxisMax() {
        if (!hasChartAxisPadding()) {
            return axisSpanExact();
        }
        return Math.max(axisSpanExact(), cutLineAlongMax - firstAlong);
    }

    public double chartAxisSpan() {
        return Math.max(1e-6, chartAxisMax() - chartAxisMin());
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
        if (!Double.isNaN(cutLineAlongMin) && !Double.isNaN(cutLineAlongMax)
            && cutLineAlongMax >= cutLineAlongMin) {
            // 分区剖面：按区内跨度 ± margin 对称画线，不因一侧缺格而贴边
            minAlong = cutLineAlongMin;
            maxAlong = cutLineAlongMax;
        } else if (stations.isEmpty()) {
            minAlong = -MIN_CUT_LINE_HALF_LENGTH;
            maxAlong = MIN_CUT_LINE_HALF_LENGTH;
        } else {
            minAlong = Double.POSITIVE_INFINITY;
            maxAlong = Double.NEGATIVE_INFINITY;
            for (Station station : stations) {
                double along = projectAlongRelative(
                    station.worldX() + 0.5, station.worldZ() + 0.5, originX, originZ, dirX, dirZ);
                minAlong = Math.min(minAlong, along);
                maxAlong = Math.max(maxAlong, along);
            }
        }
        double span = maxAlong - minAlong;
        if (span < MIN_CUT_LINE_HALF_LENGTH * 2.0) {
            double mid = (minAlong + maxAlong) * 0.5;
            minAlong = mid - MIN_CUT_LINE_HALF_LENGTH;
            maxAlong = mid + MIN_CUT_LINE_HALF_LENGTH;
        }
        if (!stations.isEmpty()) {
            primaryMin = Integer.MAX_VALUE;
            primaryMax = Integer.MIN_VALUE;
            for (Station station : stations) {
                int primary = alongX ? station.worldX() : station.worldZ();
                primaryMin = Math.min(primaryMin, primary);
                primaryMax = Math.max(primaryMax, primary);
            }
        } else {
            primaryMin = (int) Math.floor(Math.min(
                originX + minAlong * dirX, originX + maxAlong * dirX));
            primaryMax = (int) Math.floor(Math.max(
                originX + minAlong * dirX, originX + maxAlong * dirX));
            if (!alongX) {
                primaryMin = (int) Math.floor(Math.min(
                    originZ + minAlong * dirZ, originZ + maxAlong * dirZ));
                primaryMax = (int) Math.floor(Math.max(
                    originZ + minAlong * dirZ, originZ + maxAlong * dirZ));
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
