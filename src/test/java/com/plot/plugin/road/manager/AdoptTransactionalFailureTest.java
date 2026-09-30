package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认领失败时不应留下脏 live 路网（事务式回滚，而非依赖用户 Undo）。
 */
class AdoptTransactionalFailureTest {

    @Test
    void adoptKeepsSuccessfulPathsWhenLaterPathFails() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"),
            new RoadProjectStatus(),
            new AdoptFailureAfterFirstSuccessBuilder());

        manager.adoptSelectedPaths(List.of(
            new PolylineShape(List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false),
            new PolylineShape(List.of(new Vec2d(0, 20), new Vec2d(10, 20)), false)));

        RoadNetwork after = manager.getNetwork();
        assertEquals(1, after.getEdges().size(), "partial success: first adopted path must remain");
        assertEquals(2, after.getNodes().size());
        assertEquals(1, manager.getSelectedEdgeIds().size());
    }

    @Test
    void adoptRestoresNetworkWhenAdoptFails() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"),
            new RoadProjectStatus(),
            new AdoptFailureBuilder());

        RoadNetwork network = manager.getNetwork();
        assertEquals(0, network.getEdges().size());

        manager.adoptSelectedPaths(List.of(new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false)));

        RoadNetwork after = manager.getNetwork();
        assertEquals(0, after.getEdges().size());
        assertEquals(0, after.getNodes().size());
        assertTrue(manager.getSelectedEdgeIds().isEmpty());
    }

    private static final class AdoptFailureBuilder extends RoadNetworkBuilder {
        @Override
        public AdoptResult adoptShape(
                RoadNetwork network,
                Shape shape,
                RoadSystemConfig defaults) {
            throw new RuntimeException("simulated adopt failure");
        }
    }

    private static final class AdoptFailureAfterFirstSuccessBuilder extends RoadNetworkBuilder {
        private int adoptCalls;

        @Override
        public AdoptResult adoptShape(
                RoadNetwork network,
                Shape shape,
                RoadSystemConfig defaults) {
            adoptCalls++;
            if (adoptCalls >= 2) {
                throw new RuntimeException("simulated adopt failure on second path");
            }
            return super.adoptShape(network, shape, defaults);
        }
    }
}
