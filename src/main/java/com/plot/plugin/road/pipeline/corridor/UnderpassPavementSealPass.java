package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

/**
 * 按设计期望路面（{@link ExpectedRoadSurface}）逐格校验并补齐下层/立交路面。
 * <p>
 * 不再用邻格半径或白色混凝土兜底猜测范围；缺失或错材质时按期望位置/材质恢复。
 * 合法 ROAD_MARKING 覆盖保留；其它层则恢复为期望路面。
 */
public final class UnderpassPavementSealPass {
    private UnderpassPavementSealPass() {
    }

    public static void apply(RoadGenerationResult result) {
        if (result == null || result.expectedRoadSurfaces.isEmpty()) {
            return;
        }
        for (ExpectedRoadSurface expected : result.expectedRoadSurfaces.values()) {
            if (expected == null || !expected.isCarriageway()) {
                continue;
            }
            restoreIfNeeded(result, expected);
        }
    }

    private static void restoreIfNeeded(RoadGenerationResult result, ExpectedRoadSurface expected) {
        BlockPos pos = expected.position();
        var actual = result.placementRecords.get(pos);
        RoadSolidLayer actualLayer = result.placementLayers.get(pos);
        if (actual != null
                && expected.materialId().equals(actual.newBlockId)
                && actualLayer == RoadSolidLayer.ROAD) {
            return;
        }
        // 标线等合法覆盖：路面材质已在同柱其它层或标线本身可接受时跳过强制覆盖。
        if (actual != null && actualLayer == RoadSolidLayer.MARKING) {
            return;
        }
        String previous = actual != null ? actual.previousBlockId : "minecraft:air";
        if (previous == null || previous.isBlank()) {
            previous = "minecraft:air";
        }
        result.recordPlacementOverride(pos, previous, expected.materialId());
        result.placementLayers.put(pos, expected.layer());
        result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.DEFAULT);
        result.roadBlocks.add(pos);
        // 保留已有净空顶；若无记录则不强行猜测，避免误清桥下非道路区。
    }
}
