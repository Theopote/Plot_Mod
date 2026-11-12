package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平坦同层路口：进口锚点同高时圆角不得被削峰/台阶循环改低。
 */
class JunctionElevationFlatUniformTest {

    @Test
    void flatApproachAnchorsForceUniformJunctionY() {
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(8, 8),
            new Vec2d(3, 8),
            new Vec2d(8, 3),
            8.0,
            Math.PI / 2.0,
            FilletArcSweep.resolveShort(new Vec2d(8, 8), new Vec2d(3, 8), new Vec2d(8, 3)));
        JunctionGeometryResult geometry = new JunctionGeometryResult(
            List.of(new Vec2d(-6, -6), new Vec2d(20, -6), new Vec2d(20, 20), new Vec2d(-6, 20)),
            List.of(new Vec2d(-4, -4), new Vec2d(16, -4), new Vec2d(16, 16), new Vec2d(-4, 16)),
            List.of(),
            8.0,
            8.0,
            List.of(fillet),
            List.of(new Vec2d(-2, -2), new Vec2d(10, -2), new Vec2d(10, 10), new Vec2d(-2, 10)),
            List.of());
        RoadTerrainClearanceUtils.BlockColumnResolver columns =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.x);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.y);
                }
            };
        JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, null);
        JunctionElevationField field = JunctionElevationField.build(
            footprint, JunctionFilletElevationLoft.inactive(64), columns, 64);

        assertTrue(JunctionElevationField.isUniformFlatApproaches(
            JunctionFilletElevationLoft.inactive(64).approachRoadY(), 64));
        for (var entry : field.targetYByColumn().entrySet()) {
            assertEquals(64, entry.getValue(), "flat junction column " + entry.getKey());
        }
    }
}
