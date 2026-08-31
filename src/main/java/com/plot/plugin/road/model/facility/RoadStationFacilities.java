package com.plot.plugin.road.model.facility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 道路沿桩号附属设施列表（挡土墙、护栏、排水等区间布置）。
 * <p>
 * 空列表表示未定义桩号设施。
 */
public final class RoadStationFacilities {

    private final List<StationFacilityRun> runs = new ArrayList<>();

    public RoadStationFacilities() {
    }

    public RoadStationFacilities(List<StationFacilityRun> runs) {
        if (runs != null) {
            for (StationFacilityRun run : runs) {
                addRun(run);
            }
        }
    }

    public List<StationFacilityRun> getRuns() {
        return List.copyOf(runs);
    }

    public void addRun(StationFacilityRun run) {
        if (run != null) {
            runs.add(run);
        }
    }

    public void clearRuns() {
        runs.clear();
    }

    public boolean isEmpty() {
        return runs.isEmpty();
    }

    public int runCount() {
        return runs.size();
    }

    public List<StationFacilityRun> sortedRuns() {
        if (runs.isEmpty()) {
            return List.of();
        }
        List<StationFacilityRun> sorted = new ArrayList<>(runs);
        sorted.sort(Comparator
            .comparingDouble(StationFacilityRun::getStartStation)
            .thenComparing(run -> run.getEndStation() == null ? Double.MAX_VALUE : run.getEndStation()));
        return List.copyOf(sorted);
    }

    public boolean isValid() {
        for (StationFacilityRun run : runs) {
            if (run == null) {
                return false;
            }
        }
        return true;
    }

    public RoadStationFacilities copy() {
        List<StationFacilityRun> copied = new ArrayList<>();
        for (StationFacilityRun run : runs) {
            copied.add(run.copy());
        }
        return new RoadStationFacilities(copied);
    }
}
