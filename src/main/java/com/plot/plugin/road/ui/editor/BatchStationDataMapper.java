package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 将源道路的桩号工程数据映射到目标道路长度域。
 * <p>
 * 比例模式要求源、目标均有有效长度；任一侧为零则整次映射拒绝（不猜测桩号）。
 */
public final class BatchStationDataMapper {
    private static final double EPSILON = 1e-6;

    private BatchStationDataMapper() {
    }

    public record MapResult<T>(T data, int droppedCount, int clampedCount, boolean rejected) {
        public boolean hasWarnings() {
            return rejected || droppedCount > 0 || clampedCount > 0;
        }

        static <T> MapResult<T> rejected(int inputCount) {
            return new MapResult<>(null, Math.max(0, inputCount), 0, true);
        }

        static <T> MapResult<T> ok(T data, int dropped, int clamped) {
            return new MapResult<>(data, dropped, clamped, false);
        }
    }

    public static MapResult<RoadVariableCrossSections> mapVariableCrossSections(
            RoadVariableCrossSections source,
            double sourceLength,
            double targetLength,
            BatchStationMappingMode mode) {
        if (source == null) {
            return MapResult.ok(null, 0, 0);
        }
        if (mode == BatchStationMappingMode.PROPORTIONAL) {
            return mapVariableProportional(source, sourceLength, targetLength);
        }
        return mapVariableAbsolute(source, targetLength);
    }

    public static MapResult<RoadStationFacilities> mapStationFacilities(
            RoadStationFacilities source,
            double sourceLength,
            double targetLength,
            BatchStationMappingMode mode) {
        if (source == null) {
            return MapResult.ok(null, 0, 0);
        }
        if (mode == BatchStationMappingMode.PROPORTIONAL) {
            return mapFacilitiesProportional(source, sourceLength, targetLength);
        }
        return mapFacilitiesAbsolute(source, targetLength);
    }

    /**
     * @return 映射后的桩号；无效输入或比例模式长度不足时为 {@link Double#NaN}
     */
    public static double mapStation(
            double station,
            double sourceLength,
            double targetLength,
            BatchStationMappingMode mode) {
        if (!Double.isFinite(station) || station < 0.0) {
            return Double.NaN;
        }
        if (mode == BatchStationMappingMode.ABSOLUTE) {
            return station;
        }
        double src = Math.max(sourceLength, 0.0);
        double dst = Math.max(targetLength, 0.0);
        if (src <= EPSILON || dst <= EPSILON) {
            return Double.NaN;
        }
        return station / src * dst;
    }

    public static boolean canMapProportionally(double sourceLength, double targetLength) {
        return Math.max(sourceLength, 0.0) > EPSILON && Math.max(targetLength, 0.0) > EPSILON;
    }

    private static MapResult<RoadVariableCrossSections> mapVariableProportional(
            RoadVariableCrossSections source,
            double sourceLength,
            double targetLength) {
        int inputCount = source.stationCount();
        if (!canMapProportionally(sourceLength, targetLength)) {
            return MapResult.rejected(inputCount);
        }
        List<StationCrossSection> sorted = new ArrayList<>(source.getStations());
        sorted.removeIf(s -> s == null || !Double.isFinite(s.getStation()) || s.getStation() < 0.0);
        sorted.sort(Comparator.comparingDouble(StationCrossSection::getStation));

        List<StationCrossSection> mapped = new ArrayList<>();
        int dropped = inputCount - sorted.size();
        double last = -1.0;
        double limit = targetLength;
        for (StationCrossSection station : sorted) {
            double s = mapStation(
                station.getStation(), sourceLength, targetLength, BatchStationMappingMode.PROPORTIONAL);
            if (!Double.isFinite(s) || s < -EPSILON || s > limit + EPSILON) {
                dropped++;
                continue;
            }
            s = clamp(s, 0.0, limit);
            if (s <= last + EPSILON) {
                // 重叠或非严格递增：丢弃，不静默推移
                dropped++;
                continue;
            }
            mapped.add(StationCrossSection.at(s, station.getCrossSection().copy()));
            last = s;
        }
        return MapResult.ok(new RoadVariableCrossSections(mapped, source.getTransitionMode()), dropped, 0);
    }

    private static MapResult<RoadVariableCrossSections> mapVariableAbsolute(
            RoadVariableCrossSections source,
            double targetLength) {
        List<StationCrossSection> sorted = new ArrayList<>(source.getStations());
        int inputCount = source.stationCount();
        sorted.removeIf(s -> s == null || !Double.isFinite(s.getStation()) || s.getStation() < 0.0);
        sorted.sort(Comparator.comparingDouble(StationCrossSection::getStation));

        List<StationCrossSection> mapped = new ArrayList<>();
        int dropped = inputCount - sorted.size();
        double last = -1.0;
        double limit = Math.max(targetLength, 0.0);
        for (StationCrossSection station : sorted) {
            double s = station.getStation();
            if (s > limit + EPSILON) {
                dropped++;
                continue;
            }
            s = Math.min(s, limit);
            if (s <= last + EPSILON) {
                dropped++;
                continue;
            }
            mapped.add(StationCrossSection.at(s, station.getCrossSection().copy()));
            last = s;
        }
        return MapResult.ok(new RoadVariableCrossSections(mapped, source.getTransitionMode()), dropped, 0);
    }

    private static MapResult<RoadStationFacilities> mapFacilitiesProportional(
            RoadStationFacilities source,
            double sourceLength,
            double targetLength) {
        int inputCount = source.runCount();
        if (!canMapProportionally(sourceLength, targetLength)) {
            return MapResult.rejected(inputCount);
        }
        List<StationFacilityRun> mapped = new ArrayList<>();
        int dropped = 0;
        double limit = targetLength;
        for (StationFacilityRun run : source.getRuns()) {
            if (run == null) {
                dropped++;
                continue;
            }
            double start = mapStation(
                run.getStartStation(), sourceLength, targetLength, BatchStationMappingMode.PROPORTIONAL);
            Double end = run.getEndStation() == null
                ? null
                : mapStation(
                    run.getEndStation(), sourceLength, targetLength, BatchStationMappingMode.PROPORTIONAL);
            StationFacilityRun remapped = clampFacilityRun(run, start, end, limit);
            if (remapped == null) {
                dropped++;
            } else {
                mapped.add(remapped);
            }
        }
        return MapResult.ok(new RoadStationFacilities(mapped), dropped, 0);
    }

    private static MapResult<RoadStationFacilities> mapFacilitiesAbsolute(
            RoadStationFacilities source,
            double targetLength) {
        List<StationFacilityRun> mapped = new ArrayList<>();
        int dropped = 0;
        int clamped = 0;
        double limit = Math.max(targetLength, 0.0);
        for (StationFacilityRun run : source.getRuns()) {
            if (run == null) {
                dropped++;
                continue;
            }
            double start = run.getStartStation();
            Double end = run.getEndStation();
            if (!Double.isFinite(start) || start < 0.0 || start > limit + EPSILON) {
                dropped++;
                continue;
            }
            boolean needsClamp = end != null && end > limit + EPSILON;
            StationFacilityRun remapped = clampFacilityRun(run, start, end, limit);
            if (remapped == null) {
                dropped++;
            } else {
                mapped.add(remapped);
                if (needsClamp) {
                    clamped++;
                }
            }
        }
        return MapResult.ok(new RoadStationFacilities(mapped), dropped, clamped);
    }

    private static StationFacilityRun clampFacilityRun(
            StationFacilityRun template,
            double start,
            Double end,
            double limit) {
        if (!Double.isFinite(start) || start < 0.0) {
            return null;
        }
        if (start > limit + EPSILON) {
            return null;
        }
        double clampedStart = Math.min(Math.max(0.0, start), limit);
        Double clampedEnd = end;
        if (end != null) {
            if (!Double.isFinite(end)) {
                return null;
            }
            clampedEnd = Math.min(end, limit);
            if (clampedEnd <= clampedStart + EPSILON) {
                return null;
            }
        }
        try {
            return new StationFacilityRun(
                clampedStart,
                clampedEnd,
                template.getKind(),
                template.getSide(),
                template.getMaterial(),
                template.getHeight());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
