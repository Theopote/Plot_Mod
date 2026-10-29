package com.plot.plugin.road.crossing;

/**
 * Explicit failure for a registered {@link RoadCrossing} whose arms were not both attached.
 */
public record CrossingMaterializationFailure(
        String crossingId,
        String roadAId,
        String roadBId,
        CrossingMaterializeStatus armA,
        CrossingMaterializeStatus armB,
        String detail) {

    public CrossingMaterializationFailure {
        crossingId = crossingId != null ? crossingId : "";
        roadAId = roadAId != null ? roadAId : "";
        roadBId = roadBId != null ? roadBId : "";
        armA = armA != null ? armA : CrossingMaterializeStatus.SPLIT_FAILED;
        armB = armB != null ? armB : CrossingMaterializeStatus.SPLIT_FAILED;
        detail = detail != null ? detail : "";
    }
}
