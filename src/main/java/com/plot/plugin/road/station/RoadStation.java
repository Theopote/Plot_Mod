package com.plot.plugin.road.station;

import java.util.Objects;

/**
 * 道路沿程里程点：从道路链起点按拓扑序累计的弧长坐标（米）。
 *
 * @see RoadStationing
 */
public record RoadStation(String roadId, double chainageMeters) {

    public RoadStation {
        Objects.requireNonNull(roadId, "roadId");
        if (roadId.isBlank()) {
            throw new IllegalArgumentException("roadId is blank");
        }
        if (!Double.isFinite(chainageMeters)) {
            throw new IllegalArgumentException("chainageMeters must be finite");
        }
    }
}
