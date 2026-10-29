package com.plot.plugin.road.crossing;

/**
 * Outcome of attaching one road arm to a crossing junction node.
 */
public enum CrossingMaterializeStatus {
    SUCCESS,
    ALREADY_CONNECTED,
    ROAD_NOT_FOUND,
    STATION_UNRESOLVED,
    EDGE_NOT_FOUND,
    SPLIT_FAILED,
    ENDPOINT_RELINK_FAILED;

    public boolean isSuccessful() {
        return this == SUCCESS || this == ALREADY_CONNECTED;
    }
}
