package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeGroundHeightResolverTest {

    @Test
    void samplesFlatTerrainAtNodePosition() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        NodeGroundHeightResolver resolver = new NodeGroundHeightResolver(config);
        RoadNode node = new RoadNode(new Vec2d(10, 20));

        int height = resolver.groundHeightAtNode(new FlatTerrainSampler(64), node, null);

        assertEquals(64, height);
    }

    @Test
    void usesEdgeTangentAtJunction() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        NodeGroundHeightResolver resolver = new NodeGroundHeightResolver(config);
        RoadNetwork network = new RoadNetwork();
        RoadNode node = network.createNode(new Vec2d(0, 0));
        RoadNode other = network.createNode(new Vec2d(10, 0));
        network.createEdge(node.getId(), other.getId(), java.util.List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)));

        Vec2d tangent = resolver.nodeTangent(node, network);

        assertEquals(1.0, tangent.normalize().x, 1e-9);
    }
}
