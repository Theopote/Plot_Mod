package com.plot.plugin.road.model;

import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RoadStationFacilities} 与 sidecar JSON DTO 互转。
 */
public final class StationFacilityPersistence {

    public static final class FacilityRunData {
        public double startStation;
        public Double endStation;
        public String kind;
        public String side;
        public String material;
        public Double height;
    }

    public static final class StationFacilitiesData {
        public List<FacilityRunData> runs = new ArrayList<>();
    }

    private StationFacilityPersistence() {
    }

    public static StationFacilitiesData toData(RoadStationFacilities facilities) {
        if (facilities == null || facilities.isEmpty()) {
            return null;
        }
        List<StationFacilityRun> sorted = facilities.sortedRuns();
        if (sorted.isEmpty()) {
            return null;
        }
        StationFacilitiesData data = new StationFacilitiesData();
        for (StationFacilityRun run : sorted) {
            FacilityRunData runData = new FacilityRunData();
            runData.startStation = run.getStartStation();
            runData.endStation = run.getEndStation();
            runData.kind = run.getKind().name();
            runData.side = run.getSide().name();
            runData.material = run.getMaterial();
            runData.height = run.getHeight();
            data.runs.add(runData);
        }
        return data.runs.isEmpty() ? null : data;
    }

    public static RoadStationFacilities fromData(StationFacilitiesData data) {
        if (data == null || data.runs == null || data.runs.isEmpty()) {
            return null;
        }
        RoadStationFacilities facilities = new RoadStationFacilities();
        for (FacilityRunData runData : data.runs) {
            if (runData == null || runData.kind == null || runData.side == null) {
                continue;
            }
            try {
                RoadFacilityKind kind = RoadFacilityKind.valueOf(runData.kind);
                RoadFacilitySide side = RoadFacilitySide.valueOf(runData.side);
                facilities.addRun(new StationFacilityRun(
                    runData.startStation,
                    runData.endStation,
                    kind,
                    side,
                    runData.material,
                    runData.height
                ));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid enum or station values.
            }
        }
        return facilities.isEmpty() ? null : facilities;
    }
}
