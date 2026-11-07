package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionCurbFilletBuilderTest {

    @Test
    void orthogonalCrossAddsPavementOutsideSharpCorner() {
        Vec2d center = new Vec2d(0, 0);
        double half = 3.0;
        List<JunctionCurbFilletBuilder.Approach> approaches = List.of(
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), half),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), half),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(-1, 0), half),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, -1), half)
        );

        JunctionGeometryResult sharp = JunctionCurbFilletBuilder.build(center, approaches, 6.0, 0.0);
        JunctionGeometryResult filleted = JunctionCurbFilletBuilder.build(center, approaches, 6.0, 4.0);

        assertTrue(sharp.hasFillPolygon());
        assertTrue(filleted.hasFillPolygon());
        assertTrue(filleted.fillets().size() >= 4, "four curb returns expected");
        assertTrue(filleted.effectiveCornerRadius() > 1.5,
            "effective radius must remain user-visible, got " + filleted.effectiveCornerRadius());

        // 街角凹处（路缘交点外侧）：R>0 时应被铺装覆盖
        Vec2d neFillet = new Vec2d(half + 0.5, half + 0.5);
        assertFalse(RoadGeometryUtils.pointInPolygon(neFillet, sharp.fillPolygon()),
            "sharp plus should leave the NE reentrant empty");
        assertTrue(RoadGeometryUtils.pointInPolygon(neFillet, filleted.fillPolygon()),
            "curb fillet must expand pavement into NE street corner; effR="
                + filleted.effectiveCornerRadius()
                + " fillets=" + filleted.fillets().size());
        assertTrue(filleted.effectiveCornerRadius() >= 3.5,
            "requested R=4 should stay largely effective, got " + filleted.effectiveCornerRadius());
    }

    @Test
    void unequalWidthComputesCurbIntersectionAtRealEdges() {
        Vec2d center = new Vec2d(0, 0);
        JunctionCurbFilletBuilder.Approach east =
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), 5.0);
        JunctionCurbFilletBuilder.Approach north =
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), 2.0);
        Vec2d corner = JunctionCurbFilletBuilder.curbIntersection(center, east, north);
        assertEquals(2.0, corner.x, 1e-6, "north road east curb at x=halfNorth");
        assertEquals(5.0, corner.y, 1e-6, "east road north curb at y=halfEast");
    }
}
