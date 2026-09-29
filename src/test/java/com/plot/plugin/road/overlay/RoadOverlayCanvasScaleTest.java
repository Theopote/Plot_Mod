package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadOverlayCanvasScaleTest {

    private static final ICoordinateService FOUR_BLOCKS_PER_CANVAS_UNIT =
        SnapshotCoordinateService.uniformScale(4.0);

    private static final List<Vec2d> CENTERLINE = List.of(new Vec2d(0, 0), new Vec2d(40, 0));

    @Test
    void corridorHalfWidthScalesWithProjection() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeSlopeBatter(false);
        config.setRoadWidth(5);

        double identity = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, IdentityCoordinateService.INSTANCE);
        double scaled = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, FOUR_BLOCKS_PER_CANVAS_UNIT);

        assertEquals(2.5, identity, 1e-6);
        assertEquals(0.625, scaled, 1e-6);
    }
}
