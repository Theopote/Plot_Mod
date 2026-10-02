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
        return new ConstructionDetection(List.of(), List.of(), types, List.of(3.0), List.of());
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
