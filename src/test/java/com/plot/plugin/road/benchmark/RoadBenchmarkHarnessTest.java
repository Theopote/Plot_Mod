package com.plot.plugin.road.benchmark;

import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadBenchmarkHarnessTest {

    @Test
    void generatePreviewDoesNotMutateLiveDerivedCenterline() {
        RoadSystemConfig config = RoadBenchmarkHarness.benchmarkConfig();
        config.setPathSampleDistance(4.0);

        RoadGenerator generator = new RoadGenerator(config, null, BlockProjectionHandler.getInstance());
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(60, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(60, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 12), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(60.0));
        road.setHorizontalAlignment(alignment);

        double liveYBefore = edge.getCenterlinePoints().getFirst().y;
        assertEquals(0.0, liveYBefore, 1e-6);

        RoadBenchmarkHarness.generatePreview(network, generator, new FlatTerrainSampler(64));

        assertEquals(0.0, edge.getCenterlinePoints().getFirst().y, 1e-6,
            "benchmark preview must use snapshot path and not write derived centerline to live network");
    }
}
