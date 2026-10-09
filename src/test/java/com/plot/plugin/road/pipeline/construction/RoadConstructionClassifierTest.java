package com.plot.plugin.road.pipeline.construction;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadConstructionClassifierTest {

    @Test
    void classifyMarksBridgeWhenTargetIsHighAboveGround() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.SMOOTH);

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0));
        SegmentHeightInfo heightInfo = new SegmentHeightInfo(segment, 64, 64, 70, 70, 0.0);
        TerrainSampler terrain = new FlatTerrainSampler(64);

        ConstructionDetection detection = RoadConstructionClassifier.classify(
            List.of(segment),
            List.of(heightInfo),
            terrain,
            config,
            canvas -> BlockPos.ORIGIN);

        assertEquals(RoadConstructionType.BRIDGE_ABUTMENT, detection.constructionTypes().getFirst());
        assertEquals(1, detection.bridges().size());
        assertTrue(detection.tunnels().isEmpty());
        assertEquals(1, detection.runCount(RoadConstructionType.BRIDGE));
        assertEquals(0.0, detection.runs().getFirst().startStation(), 1e-6);
        assertEquals(10.0, detection.runs().getFirst().endStation(), 1e-6);
    }

    @Test
    void structureRunFlagsMarkOnlyPortalSegments() {
        List<RoadConstructionType> types = List.of(
            RoadConstructionType.CUT,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.FILL);

        assertFalse(RoadConstructionClassifier.isStructureRunStart(types, 0));
        assertTrue(RoadConstructionClassifier.isStructureRunStart(types, 1));
        assertFalse(RoadConstructionClassifier.isStructureRunStart(types, 2));
        assertFalse(RoadConstructionClassifier.isStructureRunEnd(types, 2));
        assertTrue(RoadConstructionClassifier.isStructureRunEnd(types, 3));
        assertFalse(RoadConstructionClassifier.isStructureRunEnd(types, 4));
    }

    @Test
    void markPortalAndAbutmentRewritesStructureRunEnds() {
        List<RoadConstructionType> types = new java.util.ArrayList<>(List.of(
            RoadConstructionType.CUT,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.FILL));

        RoadConstructionClassifier.markPortalAndAbutmentSegments(types);

        assertEquals(RoadConstructionType.CUT, types.get(0));
        assertEquals(RoadConstructionType.TUNNEL_PORTAL, types.get(1));
        assertEquals(RoadConstructionType.TUNNEL, types.get(2));
        assertEquals(RoadConstructionType.TUNNEL_PORTAL, types.get(3));
        assertEquals(RoadConstructionType.BRIDGE_ABUTMENT, types.get(4));
        assertEquals(RoadConstructionType.BRIDGE_ABUTMENT, types.get(5));
        assertEquals(RoadConstructionType.FILL, types.get(6));
        assertTrue(RoadConstructionClassifier.isStructureRunStart(types, 1));
        assertFalse(RoadConstructionClassifier.isStructureRunStart(types, 2));
        assertTrue(RoadConstructionClassifier.isStructureRunEnd(types, 3));
    }
}
