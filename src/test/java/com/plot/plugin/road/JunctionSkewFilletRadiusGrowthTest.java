package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 斜交锐角街角：增大请求半径时有效半径与补铺面积必须继续增长，不能卡在重叠核边长平台期。
 */
class JunctionSkewFilletRadiusGrowthTest {

    @Test
    void acuteSkewEffectiveRadiusKeepsGrowingWithRequested() {
        Vec2d center = new Vec2d(0, 0);
        double theta = Math.toRadians(35);
        JunctionRegionBuilder.StraightRoad a =
            new JunctionRegionBuilder.StraightRoad(new Vec2d(1, 0), 3.0);
        JunctionRegionBuilder.StraightRoad b =
            new JunctionRegionBuilder.StraightRoad(
                new Vec2d(Math.cos(theta), Math.sin(theta)), 3.0);

        JunctionRegionGeometry r4 = JunctionRegionBuilder.build(center, a, b, 4.0);
        JunctionRegionGeometry r8 = JunctionRegionBuilder.build(center, a, b, 8.0);
        JunctionRegionGeometry r12 = JunctionRegionBuilder.build(center, a, b, 12.0);

        double acute4 = minAcuteRadius(r4);
        double acute8 = minAcuteRadius(r8);
        double acute12 = minAcuteRadius(r12);
        assertTrue(acute4 > 1.0, "R=4 acute fillet must exist; got " + acute4);
        assertTrue(acute8 > acute4 + 0.75,
            "R=8 acute effective must grow past R=4 plateau; 4=" + acute4 + " 8=" + acute8);
        assertTrue(acute12 > acute8 + 0.75,
            "R=12 acute effective must keep growing; 8=" + acute8 + " 12=" + acute12);

        double area4 = r4.filletAdditions().stream().mapToDouble(JunctionRegionBuilder::polygonArea).sum();
        double area8 = r8.filletAdditions().stream().mapToDouble(JunctionRegionBuilder::polygonArea).sum();
        double area12 = r12.filletAdditions().stream().mapToDouble(JunctionRegionBuilder::polygonArea).sum();
        assertTrue(area8 > area4 + 2.0, "fillet addition area must grow 4→8; " + area4 + "→" + area8);
        assertTrue(area12 > area8 + 2.0, "fillet addition area must grow 8→12; " + area8 + "→" + area12);
    }

    private static double minAcuteRadius(JunctionRegionGeometry geo) {
        double min = Double.POSITIVE_INFINITY;
        for (JunctionRegionGeometry.CurbArc arc : geo.curbArcs()) {
            if (arc.cornerAngle() < Math.PI * 0.5) {
                min = Math.min(min, arc.radius());
            }
        }
        return Double.isFinite(min) ? min : 0.0;
    }
}
