package com.plot.plugin.road.lamp;

import com.plot.core.block.BlockSpec;
import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
import com.plot.plugin.road.pipeline.corridor.ColumnKey;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Atomically accepts each complete lamp after road and junction placements have been reconciled. */
public final class RoadLampPlacementPass {
    private RoadLampPlacementPass() {
    }

    public static void apply(RoadGenerationResult result, Set<ColumnKey> junctionColumns,
                             Function<BlockPos, String> worldBlock) {
        if (result.roadLampCandidates.isEmpty()) return;
        Map<ColumnKey, List<Headroom>> reserved = new HashMap<>();
        result.openRoadClearanceTops.forEach((pos, top) -> reserved
            .computeIfAbsent(column(pos), ignored -> new ArrayList<>()).add(new Headroom(pos, top)));
        for (var lamp : result.roadLampCandidates) {
            var support = result.placementRecords.get(lamp.support());
            if (support == null || isAir(support.newBlockId)
                    || result.placementLayers.get(lamp.support()) != lamp.supportLayer()) {
                continue;
            }

            Map<BlockPos, String> previous = new HashMap<>();
            for (var voxel : lamp.voxels()) {
                var pos = voxel.position();
                var key = column(pos);
                if (junctionColumns.contains(key) || result.expectedTunnelCavity.contains(pos)
                        || result.expectedTunnelLining.contains(pos)) break;
                var existing = result.placementRecords.get(pos);
                var purpose = result.placementClearancePurposes.get(pos);
                boolean foundationColumn = key.equals(column(lamp.support()));
                if (purpose != null && (purpose != ClearancePurpose.ROAD_HEADROOM || !foundationColumn)) break;
                if (reserved.getOrDefault(key, List.of()).stream().anyMatch(clearance ->
                        clearance.contains(pos) && !lamp.support().equals(clearance.surface())
                            && !(foundationColumn && lamp.supportLayer() == RoadSolidLayer.MEDIAN
                                && clearance.surface().getY() < lamp.support().getY()))) break;
                if (existing != null) {
                    if (!isAir(existing.newBlockId)
                            || (purpose == null && !isAir(existing.previousBlockId)
                                && !matches(existing.previousBlockId, voxel.block()))) break;
                    previous.put(pos, existing.previousBlockId);
                } else {
                    String block = worldBlock.apply(pos);
                    if (block == null || (!isAir(block) && !matches(block, voxel.block()))) break;
                    previous.put(pos, block);
                }
            }
            if (previous.size() != lamp.voxels().size()) continue;
            for (var voxel : lamp.voxels()) {
                var pos = voxel.position();
                // Append the compiled support-first order after the existing pavement.
                result.placementRecords.remove(pos);
                result.recordPlacementOverride(pos, previous.get(pos), voxel.block());
                result.placementLayers.put(pos, RoadSolidLayer.STREETLIGHT);
                result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.DEFAULT);
                result.placementClearancePurposes.remove(pos);
                result.streetlightBlocks.add(pos);
            }
            result.streetlightCount++;
        }
        result.roadLampCandidates.clear();
    }

    private static ColumnKey column(BlockPos pos) {
        return new ColumnKey(pos.getX(), pos.getZ());
    }

    private static boolean isAir(String block) {
        return "minecraft:air".equals(block) || "minecraft:cave_air".equals(block) || "minecraft:void_air".equals(block);
    }

    private static boolean matches(String world, String wanted) {
        var actual = BlockSpec.parse(world);
        var spec = BlockSpec.parse(wanted);
        return actual.blockId().equals(spec.blockId())
            && actual.properties().entrySet().containsAll(spec.properties().entrySet());
    }

    private record Headroom(BlockPos surface, int top) {
        boolean contains(BlockPos pos) {
            return pos.getY() > surface.getY() && pos.getY() <= top;
        }
    }
}
