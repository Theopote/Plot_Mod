package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionFilletElevationLoftTest {

    @Test
    void angularBlendSweepsFromStartCurbToEndCurb() {
        Vec2d sharp = new Vec2d(2, 2);
        Vec2d dirA = new Vec2d(0, 1); // north curb
        Vec2d dirB = new Vec2d(1, 0); // east curb
        assertEquals(0.0, JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(dirA)), 1e-6);
        assertEquals(1.0, JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(dirB)), 1e-6);
        double mid = JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(new Vec2d(1, 1).normalize()));
        assertTrue(mid > 0.35 && mid < 0.65, "mid angle should be near 0.5; mid=" + mid);
    }

    @Test
    void reconstructSharpCornerMatchesOrthogonalFillet() {
        Vec2d center = new Vec2d(6, 6);
        Vec2d tStart = new Vec2d(2, 6);
        Vec2d tEnd = new Vec2d(6, 2);
        double sweep = FilletArcSweep.resolveShort(center, tStart, tEnd);
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            center, tStart, tEnd, 4.0, Math.PI / 2.0, sweep);
        Vec2d sharp = com.plot.plugin.road.RoadJunctionGenerator.reconstructFilletSharpCorner(
            fillet, sweep);
        assertTrue(sharp != null);
        assertEquals(2.0, sharp.x, 0.15);
        assertEquals(2.0, sharp.y, 0.15);
    }
}
