package com.plot.plugin.road.model.section;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 道路沿桩号可变横断面：有序 {@link StationCrossSection} 列表 + 过渡模式。
 * <p>
 * 空列表表示全程使用道路默认 {@link RoadCrossSection}。
 */
public final class RoadVariableCrossSections {

    private final List<StationCrossSection> stations = new ArrayList<>();
    private VariableCrossSectionTransitionMode transitionMode = VariableCrossSectionTransitionMode.STEP;

    public RoadVariableCrossSections() {
    }

    public RoadVariableCrossSections(List<StationCrossSection> stations) {
        this(stations, VariableCrossSectionTransitionMode.STEP);
    }

    public RoadVariableCrossSections(
            List<StationCrossSection> stations,
            VariableCrossSectionTransitionMode transitionMode) {
        if (stations != null) {
            for (StationCrossSection station : stations) {
                addStation(station);
            }
        }
        setTransitionMode(transitionMode);
    }

    public List<StationCrossSection> getStations() {
        return List.copyOf(stations);
    }

    public void addStation(StationCrossSection station) {
        if (station != null) {
            stations.add(station);
        }
    }

    public void clearStations() {
        stations.clear();
    }

    public boolean isEmpty() {
        return stations.isEmpty();
    }

    public int stationCount() {
        return stations.size();
    }

    public VariableCrossSectionTransitionMode getTransitionMode() {
        return transitionMode != null ? transitionMode : VariableCrossSectionTransitionMode.STEP;
    }

    public void setTransitionMode(VariableCrossSectionTransitionMode transitionMode) {
        this.transitionMode = transitionMode != null
            ? transitionMode
            : VariableCrossSectionTransitionMode.STEP;
    }

    /**
     * 按桩号升序；若桩号重复或无效则返回 empty。
     */
    public List<StationCrossSection> sortedStations() {
        if (stations.isEmpty()) {
            return List.of();
        }
        List<StationCrossSection> sorted = new ArrayList<>(stations);
        sorted.sort(Comparator.comparingDouble(StationCrossSection::getStation));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getStation() <= sorted.get(i - 1).getStation()) {
                return List.of();
            }
        }
        return List.copyOf(sorted);
    }

    public boolean isValid() {
        if (stations.size() <= 1) {
            return true;
        }
        return sortedStations().size() == stations.size();
    }

    public RoadVariableCrossSections copy() {
        List<StationCrossSection> copied = new ArrayList<>();
        for (StationCrossSection station : stations) {
            copied.add(station.copy());
        }
        return new RoadVariableCrossSections(copied, getTransitionMode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RoadVariableCrossSections other)) {
            return false;
        }
        return getTransitionMode() == other.getTransitionMode()
            && Objects.equals(stations, other.stations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTransitionMode(), stations);
    }
}
