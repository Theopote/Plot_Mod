package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 进口窗口漏检时仍可从边施工类型推断洞顶 cap，且不登记为隧道进口。
 */
class TunnelCavityClearCapFallbackTest {

    @Test
    void edgeTunnelFamilyInfersCapWithoutApproaches() {
        RoadNetwork network = stubNetwork();
        RoadNode junction = network.getNodes().values().iterator().next();
        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());
        RoadSystemConfig config = new RoadSystemConfig("tunnel-cap-fallback");
        int junctionY = 64;
        int clearHeight = ResolvedTunnelStyle.defaults().clearHeight();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : edges) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }

        assertNull(TunnelJunctionSeedResolver.resolveTunnelCavityClearCap(List.of()),
            "empty approaches alone yield no cap");
        Integer cap = TunnelJunctionSeedResolver.inferTunnelCavityClearCapFromEdges(
            network, edges, edgeResults, junctionY, config);
        assertEquals(junctionY + clearHeight, cap);
    }

    @Test
    void openRoadEdgesDoNotInferCap() {
        RoadNetwork network = stubNetwork();
        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());
        RoadSystemConfig config = new RoadSystemConfig("open-cap-fallback");
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : edges) {
            edgeResults.put(edge.getId(), new RoadGenerationResult(edge.getLength()));
        }
        assertNull(TunnelJunctionSeedResolver.inferTunnelCavityClearCapFromEdges(
            network, edges, edgeResults, 64, config));
    }

    private static RoadNetwork stubNetwork() {
        RoadNetwork network = new RoadNetwork();
        var road = network.createRoad("road");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());
        return network;
    }
}
