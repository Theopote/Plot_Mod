package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineSourcePathTest {

    @Test
    void bezierTowerLayoutFollowsCurveNotChord() {
        BezierCurveShape curve = sampleBezier();
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(curve, IdentityCoordinateService.INSTANCE);
        footprint.setMaxPoleSpacing(25.0);

        PowerPoleLayoutUtils.layoutAndSyncFootprint(footprint, IdentityCoordinateService.INSTANCE);

        assertTrue(footprint.hasSourcePath());
        assertTrue(footprint.getPathPoints().size() >= 3);
        PowerLineSourcePath source = footprint.resolveSourcePath();
        for (Vec2d tower : footprint.getPathPoints()) {
            double station = source.stationAtPoint(tower, IdentityCoordinateService.INSTANCE);
            Vec2d onCurve = source.pointAtStation(station, IdentityCoordinateService.INSTANCE);
            assertTrue(
                tower.distance(onCurve) < 0.5,
                "tower should lie on source curve");
        }
        assertTrue(
            footprint.getPathPoints().stream().anyMatch(p -> p.y > 1.0),
            "towers on a bulging bezier should leave the start-end chord");
    }

    @Test
    void sourceDescriptorRoundTripsThroughProjectJson() {
        BezierCurveShape curve = sampleBezier();
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(curve, IdentityCoordinateService.INSTANCE);
        com.plot.plugin.powerline.model.PowerLineProject project =
            new com.plot.plugin.powerline.model.PowerLineProject();
        project.addLine(footprint);

        com.plot.plugin.powerline.model.PowerLineProject restored =
            com.plot.plugin.powerline.model.PowerLineProject.fromJson(project.toJson());
        PowerLineFootprint restoredLine = restored.getLine(footprint.getId());

        assertTrue(restoredLine.hasSourcePath());
        assertEquals(
            footprint.getSourceDescriptor().fingerprint(),
            restoredLine.getSourceDescriptor().fingerprint());
    }

    private static BezierCurveShape sampleBezier() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(100, 0));
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{new Vec2d(0, 40), new Vec2d(100, 40)});
        return new BezierCurveShape(anchors, controls, false);
    }
}
