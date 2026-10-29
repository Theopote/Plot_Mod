package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.geometry.RoadCorridorGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadOverlaySlopeBatterDisplayTest {

    @Test
    void snapshotKeepsPavementCorridorAndAddsDashedSlopeRingWhenEnabled() {
        RoadSystemConfig config = new RoadSystemConfig("slope-display");
        config.setRoadWidth(7);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeBikeLane(false);
        config.setIncludeSlopeBatter(true);
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        config.setCutSlopeRatio(1.0f);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setIncludeSlopeBatter(true);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(80, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        selected.add(road.getId());

        List<RoadOverlayEntry> withoutSlope = RoadOverlayController.snapshot(
            network, config, IdentityCoordinateService.INSTANCE, 1L,
            selected, road.getId(), List.of(), false, java.util.Set.of(), false);
        List<RoadOverlayEntry> withSlope = RoadOverlayController.snapshot(
            network, config, IdentityCoordinateService.INSTANCE, 1L,
            selected, road.getId(), List.of(), false, java.util.Set.of(), true);

        assertFalse(withoutSlope.isEmpty());
        assertFalse(withSlope.isEmpty());
        RoadOverlayEntry plain = withoutSlope.getFirst();
        RoadOverlayEntry annotated = withSlope.getFirst();

        assertFalse(plain.hasSlopeBatterOverlay());
        assertTrue(annotated.hasSlopeBatterOverlay());

        RoadCorridorGeometry pavementPlain = plain.corridorGeometry();
        RoadCorridorGeometry pavementAnnotated = annotated.corridorGeometry();
        // Enabling the checkbox must not widen the hard-pavement fill used for hit-test/outline.
        assertFalse(RoadOverlayGeometry.containsPoint(pavementPlain, 40, 8));
        assertFalse(RoadOverlayGeometry.containsPoint(pavementAnnotated, 40, 8));
        assertTrue(RoadOverlayGeometry.containsPoint(annotated.slopeBatterGeometry(), 40, 8));
        assertTrue(RoadOverlayStyle.slopeBatter().dashed());
    }
}
