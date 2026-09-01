package com.plot.plugin.road.pipeline.construction;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadConstructionClassifierTest {

    @Test
    void classifyMarksBridgeWhenTargetIsHighAboveGround() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setBridgeThreshold(2);
        config.setTunnelThreshold(2);
        config.setMinimumConstructionRunLength(1.0);

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0));
        SegmentHeightInfo heightInfo = new SegmentHeightInfo(segment, 64, 64, 70, 70, 0.0);
        TerrainSampler terrain = new FlatTerrainSampler(64);

        ConstructionDetection detection = RoadConstructionClassifier.classify(
            List.of(segment),
            List.of(heightInfo),
            terrain,
            config,
            canvas -> BlockPos.ORIGIN);

        assertEquals(RoadConstructionType.BRIDGE, detection.constructionTypes().getFirst());
        assertEquals(1, detection.bridges().size());
        assertTrue(detection.tunnels().isEmpty());
        assertEquals(1, detection.runCount(RoadConstructionType.BRIDGE));
        assertEquals(0.0, detection.runs().getFirst().startStation(), 1e-6);
        assertEquals(10.0, detection.runs().getFirst().endStation(), 1e-6);
    }
}
