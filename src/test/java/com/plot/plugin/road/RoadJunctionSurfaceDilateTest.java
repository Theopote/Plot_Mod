package com.plot.plugin.road;

import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.pipeline.corridor.JunctionWorldFootprint;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接缝补缺回归：路口侧不再盲膨胀全部铺装列；缺列补在 footprint.seamTransition。
 */
class RoadJunctionSurfaceDilateTest {

    @Test
    void gapFillDoesNotRewriteApproachArmNextToCore() {
        Set<ColumnKey> roadCore = Set.of(new ColumnKey(0, 0), new ColumnKey(1, 0));
        Set<ColumnKey> fillets = Set.of(new ColumnKey(8, 8));
        Set<ColumnKey> seam = new LinkedHashSet<>();
        Set<ColumnKey> pave = new LinkedHashSet<>();
        pave.addAll(roadCore);
        pave.addAll(fillets);
        pave.add(new ColumnKey(10, 8)); // far side of the actual single-column seam
        Set<ColumnKey> clearance = new LinkedHashSet<>(pave);
        clearance.add(new ColumnKey(2, 0)); // 窄进口外侧，仅与 core 相邻
        clearance.add(new ColumnKey(9, 8)); // 圆角旁缺口
        Map<ColumnKey, com.plot.api.geometry.Vec2d> plans = new LinkedHashMap<>();

        JunctionWorldFootprint.fillSeamGaps(fillets, seam, pave, clearance, plans, null);

        assertTrue(seam.contains(new ColumnKey(9, 8)));
        assertFalse(seam.contains(new ColumnKey(2, 0)),
            "approach-arm column next to core must keep corridor ownership");
    }
}
