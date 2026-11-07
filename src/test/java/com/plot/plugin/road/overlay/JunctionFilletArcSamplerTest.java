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

    @Test
    void acuteGrassAngleMustNotFlipIntoNearFullCircle() {
        // 草地楔形 35° 时切点圆心角≈145°；若误用草地角匹配会翻成 ~215° 近整圆
        JunctionGeometryResult.FilletCorner corner = new JunctionGeometryResult.FilletCorner(
            new Vec2d(10, 0),
            new Vec2d(10 + Math.cos(Math.toRadians(20)), Math.sin(Math.toRadians(20))),
            new Vec2d(10 + Math.cos(Math.toRadians(-20)), Math.sin(Math.toRadians(-20))),
            1.0,
            Math.toRadians(35.0));
        List<Vec2d> arc = JunctionFilletArcSampler.sampleCorner(corner, 16);
        assertTrue(arc.size() >= 3);
        double chord = arc.getFirst().distance(arc.getLast());
        double path = 0.0;
        for (int i = 1; i < arc.size(); i++) {
            path += arc.get(i - 1).distance(arc.get(i));
        }
        // 短弧路径应接近圆心角对应弧长（约 40°≈0.70），绝不能接近整圆 2π
        assertTrue(path < Math.PI + 0.2,
            "sampler must keep short curb arc; pathLen=" + path + " chord=" + chord);
        assertTrue(path < 2.5 * chord + 0.5,
            "path far longer than chord indicates wrong-direction long sweep");
    }
}
