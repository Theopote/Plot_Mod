package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成栅格必须落在锐角→弧的补铺侧，不能落在圆心→弧的草地扇形。
 */
class JunctionFilletGoreStampSideTest {

    @Test
    void gorePolygonCoversPavementSideNotGrassSector() {
        // 正交街角：圆心在 (6,6)，R=4，切点 (2,6)/(6,2)，锐点约 (2,2)
        Vec2d center = new Vec2d(6, 6);
        Vec2d tStart = new Vec2d(2, 6);
        Vec2d tEnd = new Vec2d(6, 2);
        double radius = 4.0;
        double grass = Math.PI / 2.0;
        double sweep = FilletArcSweep.resolveShort(center, tStart, tEnd);
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            center, tStart, tEnd, radius, grass, sweep);

        List<Vec2d> gore = RoadJunctionGenerator.filletGorePolygon(fillet);
        assertTrue(gore.size() >= 4, "gore must include sharp + arc");

        // 对角线上：锐点(2,2) → 弧中点≈(3.17,3.17) → 圆心(6,6)
        Vec2d pavementSample = new Vec2d(2.5, 2.5); // 锐点与弧之间（补铺侧）
        Vec2d grassSample = new Vec2d(4.5, 4.5);    // 弧外侧朝向圆心（草地扇形侧）

        assertTrue(
            RoadGeometryUtils.pointInPolygon(pavementSample, gore),
            "pavement gore sample must be inside sharp→arc polygon");
        assertFalse(
            RoadGeometryUtils.pointInPolygon(grassSample, gore),
            "grass outside the curb arc must stay outside gore");
    }
}
