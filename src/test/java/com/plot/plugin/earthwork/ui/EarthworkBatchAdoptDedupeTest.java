package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.manager.EarthworkPreviewIdentity;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 批量认领：同批次重复轮廓与项目已有轮廓均应过滤。
 * （逻辑与 {@link EarthworkRegionPanel#adoptSelectedRegions} 对齐的纯函数复现。）
 */
class EarthworkBatchAdoptDedupeTest {

    @Test
    void batchDedupeSkipsProjectAndInBatchDuplicates() {
        EarthworkProject project = new EarthworkProject();
        project.addRegion(new GradingRegion("existing", rectangleOutline(0, 3, 0, 3)));

        List<Vec2d> newOutline = rectangleOutline(10, 13, 10, 13);
        List<Vec2d> rotatedSame = List.of(
            newOutline.get(1), newOutline.get(2), newOutline.get(3), newOutline.get(0));
        List<List<Vec2d>> candidates = List.of(
            rectangleOutline(0, 3, 0, 3),   // 与已有重复
            newOutline,                    // 新
            newOutline,                    // 同批次重复
            rotatedSame);                  // 旋转起点，同几何

        List<List<Vec2d>> toAdopt = new ArrayList<>();
        int skipped = 0;
        Set<Long> batchFingerprints = new HashSet<>();
        for (List<Vec2d> points : candidates) {
            long fingerprint = EarthworkPreviewIdentity.normalizedOutlineFingerprint(points);
            if (matchesExisting(project, points) || !batchFingerprints.add(fingerprint)) {
                skipped++;
            } else {
                toAdopt.add(points);
            }
        }

        assertEquals(1, toAdopt.size());
        assertEquals(3, skipped);
    }

    private static boolean matchesExisting(EarthworkProject project, List<Vec2d> outline) {
        long fingerprint = EarthworkPreviewIdentity.normalizedOutlineFingerprint(outline);
        for (GradingRegion existing : project.getRegions().values()) {
            if (EarthworkPreviewIdentity.normalizedOutlineFingerprint(existing.getOuterPoints())
                == fingerprint) {
                return true;
            }
        }
        return false;
    }
}
