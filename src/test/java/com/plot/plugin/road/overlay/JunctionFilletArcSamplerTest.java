package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.JunctionGeometryResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionFilletArcSamplerTest {

    @Test
    void samplesOpenArcBetweenTangentPointsNotClosedRingAroundCenter() {
        JunctionGeometryResult.FilletCorner corner = new JunctionGeometryResult.FilletCorner(
            new Vec2d(3, 3),
            new Vec2d(5, 2),
            new Vec2d(2, 5),
            2.0,
            Math.PI / 2.0);
        List<Vec2d> arc = JunctionFilletArcSampler.sampleCorner(corner, 8);
        assertTrue(arc.size() >= 3);
        assertEquals(5.0, arc.getFirst().x, 1e-6);
        assertEquals(2.0, arc.getFirst().y, 1e-6);
        assertEquals(2.0, arc.getLast().x, 1e-6);
        assertEquals(5.0, arc.getLast().y, 1e-6);
        // 弧上点大致落在半径圆上，且不经过交叉中心 (0,0)
        for (int i = 1; i < arc.size() - 1; i++) {
            assertEquals(2.0, arc.get(i).distance(corner.center()), 0.05);
            assertTrue(arc.get(i).distance(new Vec2d(0, 0)) > 2.0);
        }
    }
}
