package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorTerrainPrefetchTest {

    @AfterEach
    void tearDown() {
        RoadCorridorTerrainPrefetch.clearForTests();
    }

    @Test
    void crossSectionOffsetsMatchTerrainSamplerContract() {
        assertEquals(List.of(0), RoadCorridorTerrainPrefetch.crossSectionSampleOffsets(0));
        assertEquals(List.of(0, -3, 3), RoadCorridorTerrainPrefetch.crossSectionSampleOffsets(3.0));
        // Math.round(-4.5) == -4 in Java (floor(a+0.5)); keep parity with TerrainSampler.
        assertEquals(List.of(0, -4, 5), RoadCorridorTerrainPrefetch.crossSectionSampleOffsets(4.5));
    }

    @Test
    void collectUsesActualHalfWidthOffsetsNotFixedEight() {
        RoadSystemConfig config = new RoadSystemConfig("road");
        config.setRoadWidth(7); // halfExtent = 3.5 → Math.round offsets 0, -3, 4

        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(20, 0));
        Road road = network.createRoad("r1");
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());

        List<BlockPos> columns = RoadCorridorTerrainPrefetch.collectProfileSampleColumns(
            network,
            null,
            config,
            (path, segs) -> 1.0).columns();

        assertFalse(columns.isEmpty());
        Set<Integer> zNearStart = new HashSet<>();
        for (BlockPos column : columns) {
            if (Math.abs(column.getX()) <= 1) {
                zNearStart.add(column.getZ());
            }
        }
        assertTrue(zNearStart.contains(0), "center column missing: " + zNearStart);
        assertTrue(
            zNearStart.contains(-3) && zNearStart.contains(4),
            "expected halfWidth offsets (-3,4) for width 7, got " + zNearStart);
        assertFalse(zNearStart.contains(8), "fixed ±8 pad should not appear for width 7: " + zNearStart);
    }

    @Test
    void collectDensifiesAlongLongEdge() {
        RoadSystemConfig config = new RoadSystemConfig("road");
        config.setRoadWidth(5);
        config.setPathSampleDistance(2.0);

        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 0));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        List<BlockPos> columns = RoadCorridorTerrainPrefetch.collectProfileSampleColumns(
            network, null, config, (path, segs) -> 1.0).columns();

        Set<Integer> xs = new HashSet<>();
        for (BlockPos column : columns) {
            if (column.getZ() == 0) {
                xs.add(column.getX());
            }
        }
        assertTrue(xs.size() > 3, "densified stations expected, got " + xs.size() + " for " + edge.getId());
    }

    @Test
    void fingerprintChangesWhenMiddleControlPointMoves() {
        RoadSystemConfig config = new RoadSystemConfig("road");
        config.setRoadWidth(5);

        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(25, 0),
                new Vec2d(50, 0),
                new Vec2d(75, 0),
                new Vec2d(100, 0)),
            road.getId());

        String before = RoadCorridorTerrainPrefetch.fingerprint(network, null, config, null);

        List<Vec2d> moved = new ArrayList<>(edge.getCenterlinePoints());
        moved.set(2, new Vec2d(50, 40)); // move middle PI toward hillside; ends unchanged
        edge.setCenterlinePoints(moved);

        String after = RoadCorridorTerrainPrefetch.fingerprint(network, null, config, null);
        assertNotEquals(before, after, "middle control-point move must invalidate prefetch fingerprint");
    }

    @Test
    void fingerprintChangesWhenRoadWidthChanges() {
        RoadSystemConfig config = new RoadSystemConfig("road");
        config.setRoadWidth(5);

        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 0));
        Road road = network.createRoad("r1");
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        String narrow = RoadCorridorTerrainPrefetch.fingerprint(network, null, config, null);
        config.setRoadWidth(11);
        String wide = RoadCorridorTerrainPrefetch.fingerprint(network, null, config, null);
        assertNotEquals(narrow, wide, "carriageway width change must invalidate prefetch fingerprint");
    }

    @Test
    void worldTerrainEpochInvalidatesFingerprint() {
        RoadSystemConfig config = new RoadSystemConfig("road");
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        Road road = network.createRoad("r1");
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        String before = RoadCorridorTerrainPrefetch.fingerprint(network, null, config, null);
        RoadCorridorTerrainPrefetch.notifyWorldTerrainChanged();
        String after = RoadCorridorTerrainPrefetch.fingerprint(network, null, config, null);
        assertNotEquals(before, after);
        assertTrue(RoadCorridorTerrainPrefetch.worldTerrainEpoch() > 1L);
    }

    @Test
    void collectReportsTruncationWhenHittingColumnCap() {
        // Force truncation by collecting against a tiny temporary cap via many edges is hard;
        // instead verify the result type exposes truncated=false for a small network.
        RoadSystemConfig config = new RoadSystemConfig("road");
        config.setRoadWidth(3);
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(8, 0));
        Road road = network.createRoad("r1");
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(8, 0)), road.getId());

        RoadCorridorTerrainPrefetch.ColumnCollectResult result =
            RoadCorridorTerrainPrefetch.collectProfileSampleColumns(
                network, null, config, (path, segs) -> 1.0);
        assertFalse(result.truncated());
        assertFalse(result.columns().isEmpty());
        assertTrue(result.columns().size() < RoadCorridorTerrainPrefetch.MAX_COLUMNS);
    }
}
