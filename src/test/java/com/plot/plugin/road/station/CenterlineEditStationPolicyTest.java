package com.plot.plugin.road.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CenterlineEditStationPolicyTest {

    @Test
    void insertPiUsesPreserveWhenSegmentLengthUnchanged() {
        assertEquals(
            CenterlineEditStationPolicy.PRESERVE_STATION,
            CenterlineEditOperation.INSERT_PI.resolveStationPolicy(100.0, 100.0));
    }

    @Test
    void insertPiUsesReparameterizeWhenSegmentLengthChanges() {
        assertEquals(
            CenterlineEditStationPolicy.REPARAMETERIZE_STATION,
            CenterlineEditOperation.INSERT_PI.resolveStationPolicy(100.0, 105.0));
    }

    @Test
    void operationsDeclareExpectedDefaultPolicies() {
        assertEquals(CenterlineEditStationPolicy.REPARAMETERIZE_STATION,
            CenterlineEditOperation.FILLET.defaultStationPolicy());
        assertEquals(CenterlineEditStationPolicy.PRESERVE_STATION,
            CenterlineEditOperation.SPLIT_EDGE.defaultStationPolicy());
        assertEquals(CenterlineEditStationPolicy.PRESERVE_STATION,
            CenterlineEditOperation.MERGE_EDGE.defaultStationPolicy());
        assertEquals(CenterlineEditStationPolicy.PARTITION_AND_RESET_TAIL,
            CenterlineEditOperation.SPLIT_ROAD.defaultStationPolicy());
        assertEquals(CenterlineEditStationPolicy.OFFSET_BY_HEAD_LENGTH,
            CenterlineEditOperation.MERGE_ROAD.defaultStationPolicy());
        assertEquals(CenterlineEditStationPolicy.PRESERVE_STATION,
            CenterlineEditOperation.REVERSE_EDGE.defaultStationPolicy());
        assertEquals(CenterlineEditStationPolicy.MIRROR_FULL_ROAD,
            CenterlineEditOperation.REVERSE_ROAD.defaultStationPolicy());
    }
}
