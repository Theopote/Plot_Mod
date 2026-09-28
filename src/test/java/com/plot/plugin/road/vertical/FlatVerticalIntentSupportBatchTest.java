package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatVerticalIntentSupportBatchTest {

    private static final RoadSystemConfig CONFIG = new RoadSystemConfig("test");

    @Test
    void summarizeFlatBaseElevationsDetectsMixedValues() {
        RoadNetwork network = new RoadNetwork();
        Road flatA = network.createRoad("flat-a");
        Road flatB = network.createRoad("flat-b");
        addStraightRoad(network, flatA, 80.0);
        addStraightRoad(network, flatB, 90.0);
        FlatVerticalIntentSupport.enableFlatWithBase(network, flatA, CONFIG, 70.0);
        FlatVerticalIntentSupport.enableFlatWithBase(network, flatB, CONFIG, 74.0);

        FlatVerticalIntentSupport.BatchElevationSummary summary =
            FlatVerticalIntentSupport.summarizeFlatBaseElevations(
                network, List.of(flatA.getId(), flatB.getId()));

        assertTrue(summary.mixed());
        assertEquals(2, summary.flatRoadCount());
    }

    @Test
    void applyBatchFlatRecommendedUsesPerRoadBaseline() {
        RoadNetwork network = new RoadNetwork();
        Road adaptive = network.createRoad("adaptive");
        Road flat = network.createRoad("flat");
        addStraightRoad(network, adaptive, 100.0);
        addStraightRoad(network, flat, 100.0);
        adaptive.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
        FlatVerticalIntentSupport.enableFlatWithBase(network, flat, CONFIG, 68.0);

        int changed = FlatVerticalIntentSupport.applyBatchFlatRecommended(
            network, List.of(adaptive.getId(), flat.getId()), CONFIG);

        assertEquals(2, changed);
        assertEquals(RoadVerticalMode.FLAT, adaptive.getVerticalMode());
        assertEquals(68.0, FlatVerticalIntentSupport.resolveIntent(network, flat).getBaseElevation(), 1e-6);
    }

    @Test
    void applyBatchFlatUniformBaseSetsSharedElevation() {
        RoadNetwork network = new RoadNetwork();
        Road first = network.createRoad("first");
        Road second = network.createRoad("second");
        addStraightRoad(network, first, 100.0);
        addStraightRoad(network, second, 120.0);
        FlatVerticalIntentSupport.enableFlatWithBase(network, first, CONFIG, 70.0);
        second.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);

        int changed = FlatVerticalIntentSupport.applyBatchFlatUniformBase(
            network, List.of(first.getId(), second.getId()), 72.0, CONFIG);

        assertEquals(2, changed);
        assertEquals(72.0, FlatVerticalIntentSupport.resolveIntent(network, first).getBaseElevation(), 1e-6);
        assertEquals(72.0, FlatVerticalIntentSupport.resolveIntent(network, second).getBaseElevation(), 1e-6);
    }

    @Test
    void applyBatchTerrainAdaptiveConvertsFlatRoads() {
        RoadNetwork network = new RoadNetwork();
        Road flat = network.createRoad("flat");
        addStraightRoad(network, flat, 100.0);
        FlatVerticalIntentSupport.enableFlatWithBase(network, flat, CONFIG, 71.0);

        int changed = FlatVerticalIntentSupport.applyBatchTerrainAdaptive(
            network, List.of(flat.getId()), CONFIG);

        assertEquals(1, changed);
        assertEquals(RoadVerticalMode.AUTO_SMOOTH, flat.getVerticalMode());
        assertTrue(flat.getFlatVerticalIntent() == null);
        assertTrue(flat.getVerticalAlignment() == null);
    }

    @Test
    void flatToAdaptiveClearsCompiledProfile() {
        RoadNetwork network = new RoadNetwork();
        Road flat = network.createRoad("flat");
        addStraightRoad(network, flat, 100.0);
        FlatVerticalIntentSupport.enableFlatWithBase(network, flat, CONFIG, 80.0);
        assertFalse(flat.getVerticalAlignment().isEmpty());

        FlatVerticalIntentSupport.enableTerrainAdaptive(network, flat, CONFIG);

        assertEquals(RoadVerticalMode.AUTO_SMOOTH, flat.getVerticalMode());
        assertTrue(flat.getFlatVerticalIntent() == null);
        assertTrue(flat.getVerticalAlignment() == null);
    }

    private static void addStraightRoad(RoadNetwork network, Road road, double length) {
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
    }
}
