package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTunnelStylePersistenceTest {

    @Test
    void jsonRoundTripPreservesTunnelStyleOverride() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("tunnel-style");
        TunnelStyle override = new TunnelStyle();
        override.setShape(TunnelShape.HORSESHOE);
        override.setClearHeight(8);
        override.setLightingMode(TunnelLightingMode.WALL_BANDS);
        override.setLightSpacing(10);
        road.setTunnelStyle(override);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        String json = network.toJson();
        assertTrue(json.contains("\"tunnelStyle\""));
        assertTrue(json.contains("\"HORSESHOE\""));

        Road restored = RoadNetwork.parseSnapshot(json).getRoad("tunnel-style");
        TunnelStyle stored = restored.getStoredTunnelStyle();
        assertNotNull(stored);
        assertEquals(TunnelShape.HORSESHOE, stored.getStoredShape());
        assertEquals(8, stored.getStoredClearHeight());
        assertEquals(TunnelLightingMode.WALL_BANDS, stored.getStoredLightingMode());
        assertEquals(10, stored.getStoredLightSpacing());
    }

    @Test
    void jsonRoundTripOmitsInheritedTunnelStyle() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("tunnel-inherit");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        String json = network.toJson();
        Road restored = RoadNetwork.parseSnapshot(json).getRoad("tunnel-inherit");
        assertNull(restored.getStoredTunnelStyle());
    }
}
