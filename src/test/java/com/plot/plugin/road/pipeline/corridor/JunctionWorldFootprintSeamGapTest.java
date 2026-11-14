package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接缝缺列补铺：只从圆角/接缝种子四邻域补缺口，不从核区外扩侵占进口走廊。
 */
class JunctionWorldFootprintSeamGapTest {

    @Test
    void fillSeamGapsOnlyFromFilletSeedsNotRoadCore() {
        Set<ColumnKey> roadCore = new LinkedHashSet<>();
        roadCore.add(new ColumnKey(0, 0));
        roadCore.add(new ColumnKey(1, 0)); // 进口臂方向

        Set<ColumnKey> filletAdditions = new LinkedHashSet<>();
        filletAdditions.add(new ColumnKey(5, 5));

        Set<ColumnKey> seamTransition = new LinkedHashSet<>();
        Set<ColumnKey> pave = new LinkedHashSet<>();
        pave.addAll(roadCore);
        pave.addAll(filletAdditions);
        pave.add(new ColumnKey(7, 5));
        pave.add(new ColumnKey(5, 7));

        Set<ColumnKey> clearance = new LinkedHashSet<>(pave);
        // 进口臂外侧一格（若从 core 膨胀会被误吞）
        clearance.add(new ColumnKey(2, 0));
        // 圆角旁真正缺列
        clearance.add(new ColumnKey(6, 5));
        clearance.add(new ColumnKey(5, 6));
        clearance.add(new ColumnKey(4, 5)); // only touches one fillet voxel: exterior, not a gap

        Map<ColumnKey, Vec2d> planPoints = new LinkedHashMap<>();
        for (ColumnKey key : clearance) {
            planPoints.put(key, new Vec2d(key.worldX(), key.worldZ()));
        }

        JunctionWorldFootprint.fillSeamGaps(
            filletAdditions, seamTransition, pave, clearance, planPoints, null);

        assertTrue(seamTransition.contains(new ColumnKey(6, 5)), "fillet neighbor gap filled");
        assertTrue(seamTransition.contains(new ColumnKey(5, 6)), "fillet neighbor gap filled");
        assertFalse(seamTransition.contains(new ColumnKey(2, 0)),
            "must not steal approach-arm column adjacent only to roadCore");
        assertTrue(pave.contains(new ColumnKey(6, 5)));
        assertTrue(pave.containsAll(roadCore), "core columns unchanged");
        assertFalse(pave.contains(new ColumnKey(4, 5)), "must not dilate exterior fillet boundary");

        // 被三面铺装围住的空洞即使不碰 fillet 种子也要补
        pave.add(new ColumnKey(-1, 1));
        pave.add(new ColumnKey(1, 1));
        pave.add(new ColumnKey(0, 2));
        clearance.add(new ColumnKey(-1, 1));
        clearance.add(new ColumnKey(1, 1));
        clearance.add(new ColumnKey(0, 2));
        clearance.add(new ColumnKey(0, 1));
        planPoints.put(new ColumnKey(0, 1), new Vec2d(0, 1));
        JunctionWorldFootprint.fillSeamGaps(
            filletAdditions, seamTransition, pave, clearance, planPoints, null);
        assertTrue(pave.contains(new ColumnKey(0, 1)),
            "column with ≥3 pave neighbors is a true enclosed gap");
    }

    @Test
    void footprintSeamGapsStayInsideCorridorTrimEqualsPave() {
        Vec2d center = new Vec2d(6, 6);
        Vec2d tStart = new Vec2d(2, 6);
        Vec2d tEnd = new Vec2d(6, 2);
        double sweep = FilletArcSweep.resolveShort(center, tStart, tEnd);
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            center, tStart, tEnd, 4.0, Math.PI / 2.0, sweep);
        // 锐角核 + 圆角 gore：模拟区域式结果
        List<Vec2d> overlap = List.of(
            new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4), new Vec2d(0, 4));
        List<Vec2d> fill = List.of(
            new Vec2d(-1, -1), new Vec2d(10, -1), new Vec2d(10, 10), new Vec2d(-1, 10));
        JunctionGeometryResult geometry = new JunctionGeometryResult(
            fill,
            overlap,
            List.of(),
            4.0,
            4.0,
            List.of(fillet),
            overlap,
            List.of(List.of(
                new Vec2d(2, 2), new Vec2d(2, 6), new Vec2d(6, 6), new Vec2d(6, 2))));

        JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, null);
        assertFalse(footprint.paveColumns().isEmpty());
        assertTrue(footprint.corridorTrimColumns().equals(footprint.paveColumns()),
            "gap-fill must keep corridorTrim ≡ pave");
        assertTrue(footprint.clearance().containsAll(footprint.paveColumns()));
    }
}
