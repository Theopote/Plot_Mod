package com.plot.plugin.road.pipeline.construction;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterCrossingConstructionResolverTest {

    @Test
    void causewayCrossingZoneForcesFillConstruction() {
        ConstructionDetection base = detection(List.of(RoadConstructionType.ROAD));
        WaterCrossing causeway = new WaterCrossing(
            0.0, 0.5, 2.5, 3.0,
            1.0, 2.0, 2.0, 1.0, 1.0,
            64, 64, 63, WaterCrossingStrategy.CAUSEWAY);
        List<SegmentHeightInfo> heightInfos = List.of(
            heightInfo(0.0, 3.0, 64, 64, 63, 63, 62, 63));

        ConstructionDetection resolved = WaterCrossingConstructionResolver.apply(
            base,
            List.of(causeway),
            heightInfos,
            1.0,
            new RoadSystemConfig("test"),
            null,
            canvas -> new net.minecraft.util.math.BlockPos(0, 0, 0));

        assertEquals(RoadConstructionType.FILL, resolved.constructionTypes().getFirst());
    }

    @Test
    void bridgeCrossingZoneForcesBridgeConstruction() {
        ConstructionDetection base = detection(List.of(RoadConstructionType.ROAD));
        WaterCrossing bridge = new WaterCrossing(
            0.0, 5.0, 25.0, 30.0,
            10.0, 20.0, 20.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.BRIDGE);
        List<SegmentHeightInfo> heightInfos = List.of(
            heightInfo(0.0, 30.0, 68, 68, 69, 69, 55, 55));

        ConstructionDetection resolved = WaterCrossingConstructionResolver.apply(
            base,
            List.of(bridge),
            heightInfos,
            1.0,
            new RoadSystemConfig("test"),
            null,
            canvas -> new net.minecraft.util.math.BlockPos(0, 0, 0));

        assertEquals(RoadConstructionType.BRIDGE, resolved.constructionTypes().getFirst());
        assertTrue(resolved.bridges().size() >= 1);
    }

    @Test
    void causewayApproachZoneForcesFillConstruction() {
        ConstructionDetection base = detection(List.of(RoadConstructionType.ROAD));
        WaterCrossing causeway = new WaterCrossing(
            0.0, 0.5, 2.5, 3.0,
            1.0, 2.0, 2.0, 1.0, 1.0,
            64, 64, 63, WaterCrossingStrategy.CAUSEWAY);
        List<SegmentHeightInfo> heightInfos = List.of(
            heightInfo(0.0, 0.4, 64, 64, null, null, 64, 63));

        ConstructionDetection resolved = WaterCrossingConstructionResolver.apply(
            base,
            List.of(causeway),
            heightInfos,
            1.0,
            new RoadSystemConfig("test"),
            null,
            canvas -> new net.minecraft.util.math.BlockPos(0, 0, 0));

        assertEquals(RoadConstructionType.FILL, resolved.constructionTypes().getFirst());
        assertTrue(WaterCrossingConstructionResolver.isCausewayFillStation(List.of(causeway), 0.2));
    }

    @Test
    void bridgeApproachZoneDoesNotForceFillConstruction() {
        ConstructionDetection base = detection(List.of(RoadConstructionType.ROAD));
        WaterCrossing bridge = new WaterCrossing(
            0.0, 5.0, 25.0, 30.0,
            10.0, 20.0, 20.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.BRIDGE);
        List<SegmentHeightInfo> heightInfos = List.of(
            heightInfo(0.0, 4.0, 68, 68, null, null, 68, 68));

        ConstructionDetection resolved = WaterCrossingConstructionResolver.apply(
            base,
            List.of(bridge),
            heightInfos,
            1.0,
            new RoadSystemConfig("test"),
            null,
            canvas -> new net.minecraft.util.math.BlockPos(0, 0, 0));

        assertEquals(RoadConstructionType.ROAD, resolved.constructionTypes().getFirst());
        assertTrue(!WaterCrossingConstructionResolver.isCausewayFillStation(List.of(bridge), 2.0));
    }

    @Test
    void adjacentCausewayAndBridgeDoNotCrossContaminateStrategy() {
        ConstructionDetection base = detection(List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.ROAD,
            RoadConstructionType.ROAD));
        WaterCrossing causeway = new WaterCrossing(
            0.0, 1.0, 4.0, 5.0,
            1.5, 3.5, 3.0, 1.0, 1.0,
            64, 64, 63, WaterCrossingStrategy.CAUSEWAY);
        WaterCrossing bridge = new WaterCrossing(
            3.0, 6.0, 30.0, 33.0,
            10.0, 20.0, 24.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.BRIDGE);
        List<SegmentHeightInfo> heightInfos = List.of(
            heightInfo(0.0, 4.0, 64, 64, 63, 63, 64, 64),
            heightInfo(4.0, 6.0, 64, 64, 63, 69, 64, 64),
            heightInfo(6.0, 10.0, 68, 68, 69, 69, 64, 64));

        ConstructionDetection resolved = WaterCrossingConstructionResolver.apply(
            base,
            List.of(causeway, bridge),
            heightInfos,
            1.0,
            new RoadSystemConfig("test"),
            null,
            canvas -> new net.minecraft.util.math.BlockPos(0, 0, 0));

        assertEquals(
            RoadConstructionType.ROAD,
            resolved.constructionTypes().get(1),
            "bridge approach must not inherit another crossing's crossing zone");
    }

    @Test
    void adjacentBridgeAndTunnelDoNotCrossContaminateStrategy() {
        ConstructionDetection base = detection(List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.ROAD,
            RoadConstructionType.ROAD));
        WaterCrossing bridge = new WaterCrossing(
            0.0, 2.0, 10.0, 12.0,
            3.0, 9.0, 8.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.BRIDGE);
        WaterCrossing tunnel = new WaterCrossing(
            12.0, 18.0, 25.0, 28.0,
            19.0, 24.0, 7.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.TUNNEL_CANDIDATE);
        List<SegmentHeightInfo> heightInfos = List.of(
            heightInfo(0.0, 12.0, 68, 68, 69, 69, 64, 64),
            heightInfo(12.0, 17.0, 68, 68, 69, 69, 64, 64),
            heightInfo(17.0, 28.0, 68, 68, 69, 69, 64, 64));
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setAllowUnderwaterRoad(true);

        ConstructionDetection resolved = WaterCrossingConstructionResolver.apply(
            base,
            List.of(bridge, tunnel),
            heightInfos,
            1.0,
            config,
            null,
            canvas -> new net.minecraft.util.math.BlockPos(0, 0, 0));

        assertEquals(
            RoadConstructionType.ROAD,
            resolved.constructionTypes().get(1),
            "tunnel approach must not inherit another crossing's crossing zone");
    }

    @Test
    void bridgeDeckStationIsLimitedToCrossingZone() {
        WaterCrossing bridge = new WaterCrossing(
            0.0, 4.0, 8.0, 12.0,
            5.0, 7.0, 4.0, 2.0, 3.0,
            50, 50, 51, WaterCrossingStrategy.BRIDGE);

        assertTrue(WaterCrossingConstructionResolver.isBridgeDeckStation(List.of(bridge), 4.0));
        assertTrue(WaterCrossingConstructionResolver.isBridgeDeckStation(List.of(bridge), 8.0));
        assertTrue(!WaterCrossingConstructionResolver.isBridgeDeckStation(List.of(bridge), 2.0));
        assertTrue(!WaterCrossingConstructionResolver.isBridgeDeckStation(List.of(bridge), 10.0));
        assertTrue(WaterCrossingConstructionResolver.isBridgeStructureStation(List.of(bridge), 2.0));
        assertTrue(!WaterCrossingConstructionResolver.isBridgeStructureStation(List.of(bridge), 15.0));
    }

    @Test
    void longBridgeUsesWiderPillarSpacing() {
        WaterCrossing longBridge = new WaterCrossing(
            0.0, 0.0, 100.0, 110.0,
            0.0, 100.0, 100.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.LONG_BRIDGE);
        double spacing = WaterCrossingConstructionResolver.bridgePillarSpacingBlocks(
            List.of(longBridge),
            50.0,
            6.0);
        assertEquals(12.0, spacing, 1e-6);
    }

    private static ConstructionDetection detection(List<RoadConstructionType> types) {
        List<Double> distances = types.stream().map(type -> 3.0).toList();
        return new ConstructionDetection(List.of(), List.of(), types, distances, List.of());
    }

    private static SegmentHeightInfo heightInfo(
            double startX,
            double endX,
            int groundStart,
            int groundEnd,
            Integer waterStart,
            Integer waterEnd,
            int targetStart,
            int targetEnd) {
        PathSegment segment = new PathSegment(new Vec2d(startX, 0), new Vec2d(endX, 0));
        return new SegmentHeightInfo(
            segment,
            groundStart,
            groundEnd,
            waterStart,
            waterEnd,
            targetStart,
            targetEnd,
            targetStart,
            targetEnd,
            segment.distance);
    }
}
