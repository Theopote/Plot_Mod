package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 将源道路的桩号工程数据映射到目标道路长度域。
 */
public final class BatchStationDataMapper {
    private static final double EPSILON = 1e-6;

    private BatchStationDataMapper() {
    }

    public record MapResult<T>(T data, int droppedCount, int clampedCount) {
        public boolean hasWarnings() {
            return droppedCount > 0 || clampedCount > 0;
        }
    }

    public static MapResult<RoadVariableCrossSections> mapVariableCrossSections(
            RoadVariableCrossSections source,
            double sourceLength,
            double targetLength,
            BatchStationMappingMode mode) {
        if (source == null) {
            return new MapResult<>(null, 0, 0);
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
            return new MapResult<>(null, 0, 0);
        }
        if (mode == BatchStationMappingMode.PROPORTIONAL) {
            return mapFacilitiesProportional(source, sourceLength, targetLength);
        }
        return mapFacilitiesAbsolute(source, targetLength);
    }

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
        if (src <= EPSILON) {
            // 源无长度（仅草稿路）：目标也无长度时原样保留，否则夹到目标长
            return dst <= EPSILON ? station : Math.min(station, dst);
        }
        return station / src * dst;
    }

    private static MapResult<RoadVariableCrossSections> mapVariableProportional(
            RoadVariableCrossSections source,
            double sourceLength,
            double targetLength) {
        if (Math.max(sourceLength, 0.0) <= EPSILON && Math.max(targetLength, 0.0) <= EPSILON) {
            return new MapResult<>(source.copy(), 0, 0);
        }
        List<StationCrossSection> mapped = new ArrayList<>();
        double last = -1.0;
        for (StationCrossSection station : source.getStations()) {
            if (station == null) {
                continue;
            }
            double s = mapStation(
                station.getStation(), sourceLength, targetLength, BatchStationMappingMode.PROPORTIONAL);
            if (!Double.isFinite(s)) {
                continue;
            }
            s = clamp(s, 0.0, Math.max(targetLength, 0.0));
            if (s <= last + EPSILON) {
                s = last + EPSILON * 10.0;
            }
            if (targetLength > EPSILON && s > targetLength + EPSILON) {
                continue;
            }
            mapped.add(StationCrossSection.at(s, station.getCrossSection().copy()));
            last = s;
        }
        return new MapResult<>(
            new RoadVariableCrossSections(mapped, source.getTransitionMode()),
            0,
            0);
    }

    private static MapResult<RoadVariableCrossSections> mapVariableAbsolute(
            RoadVariableCrossSections source,
            double targetLength) {
        List<StationCrossSection> mapped = new ArrayList<>();
        int dropped = 0;
        double limit = Math.max(targetLength, 0.0);
        for (StationCrossSection station : source.getStations()) {
            if (station == null) {
                continue;
            }
            double s = station.getStation();
            if (!Double.isFinite(s) || s < 0.0) {
                dropped++;
                continue;
            }
            if (s > limit + EPSILON) {
                dropped++;
                continue;
            }
            mapped.add(StationCrossSection.at(Math.min(s, limit), station.getCrossSection().copy()));
        }
        return new MapResult<>(
            new RoadVariableCrossSections(mapped, source.getTransitionMode()),
            dropped,
            0);
    }

    private static MapResult<RoadStationFacilities> mapFacilitiesProportional(
            RoadStationFacilities source,
            double sourceLength,
            double targetLength) {
        // 双边均无长度：原样复制（多选草稿路尚未铺设时）
        if (Math.max(sourceLength, 0.0) <= EPSILON && Math.max(targetLength, 0.0) <= EPSILON) {
            return new MapResult<>(source.copy(), 0, 0);
        }
        List<StationFacilityRun> mapped = new ArrayList<>();
        int dropped = 0;
        double limit = Math.max(targetLength, 0.0);
        for (StationFacilityRun run : source.getRuns()) {
            if (run == null) {
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
        return new MapResult<>(new RoadStationFacilities(mapped), dropped, 0);
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
        return new MapResult<>(new RoadStationFacilities(mapped), dropped, clamped);
    }

    private static StationFacilityRun clampFacilityRun(
            StationFacilityRun template,
            double start,
            Double end,
            double limit) {
        if (!Double.isFinite(start) || start < 0.0) {
            return null;
        }
        double clampedStart = Math.min(start, limit);
        if (clampedStart > limit + EPSILON) {
            return null;
        }
        Double clampedEnd = end;
        if (end != null) {
            if (!Double.isFinite(end)) {
                return null;
            }
            clampedEnd = Math.min(end, limit);
            if (clampedEnd <= clampedStart + EPSILON) {
                return null;
            }
        } else if (limit <= EPSILON && clampedStart > EPSILON) {
            return null;
        }
        try {
            return new StationFacilityRun(
                Math.max(0.0, clampedStart),
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
