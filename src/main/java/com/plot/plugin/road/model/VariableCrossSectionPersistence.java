package com.plot.plugin.road.model;

import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RoadVariableCrossSections} 与 sidecar JSON DTO 互转。
 */
public final class VariableCrossSectionPersistence {

    public static final class StationEntryData {
        public double station;
        public RoadNetwork.CrossSectionData crossSection;
    }

    public static final class VariableCrossSectionsData {
        public List<StationEntryData> stations = new ArrayList<>();
    }

    private VariableCrossSectionPersistence() {
    }

    public static VariableCrossSectionsData toData(RoadVariableCrossSections variable) {
        if (variable == null || variable.isEmpty()) {
            return null;
        }
        List<StationCrossSection> sorted = variable.sortedStations();
        if (sorted.isEmpty()) {
            return null;
        }
        VariableCrossSectionsData data = new VariableCrossSectionsData();
        for (StationCrossSection entry : sorted) {
            StationEntryData entryData = new StationEntryData();
            entryData.station = entry.getStation();
            entryData.crossSection = RoadNetwork.CrossSectionData.from(entry.getCrossSection());
            data.stations.add(entryData);
        }
        return data.stations.isEmpty() ? null : data;
    }

    public static RoadVariableCrossSections fromData(VariableCrossSectionsData data) {
        if (data == null || data.stations == null || data.stations.isEmpty()) {
            return null;
        }
        RoadVariableCrossSections variable = new RoadVariableCrossSections();
        for (StationEntryData entryData : data.stations) {
            if (entryData == null || entryData.crossSection == null) {
                continue;
            }
            variable.addStation(StationCrossSection.at(
                entryData.station,
                entryData.crossSection.toCrossSection()
            ));
        }
        return variable.isEmpty() ? null : variable;
    }
}
