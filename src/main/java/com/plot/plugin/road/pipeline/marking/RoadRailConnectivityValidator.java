package com.plot.plugin.road.pipeline.marking;

import com.plot.core.block.BlockSpec;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 装饰性铁路交汇的轻量连通校验：邻接断点、轨下支撑缺失。
 * 失败只计入结果计数并打 warn，不伪称「矿车可通行」。
 */
public final class RoadRailConnectivityValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadRailConnectivity");

    private RoadRailConnectivityValidator() {
    }

    public record Report(int railCount, int orphanRails, int unsupportedRails) {
        public boolean hasIssues() {
            return orphanRails > 0 || unsupportedRails > 0;
        }
    }

    public static Report validate(RoadGenerationResult result) {
        if (result == null || result.placementRecords.isEmpty()) {
            return new Report(0, 0, 0);
        }
        Map<BlockPos, String> rails = new HashMap<>();
        Set<Long> solidColumns = new HashSet<>();
        for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
            BlockRecord record = entry.getValue();
            if (record == null || record.newBlockId == null) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (RoadRailShapeResolver.isRailBlockId(record.newBlockId)) {
                rails.put(pos, record.newBlockId);
            } else if (!"minecraft:air".equals(BlockSpec.parse(record.newBlockId).blockId())) {
                solidColumns.add(RoadRailPlacementPass.cellKey(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        if (rails.isEmpty()) {
            return new Report(0, 0, 0);
        }

        Set<Long> railCells = new HashSet<>();
        for (BlockPos pos : rails.keySet()) {
            railCells.add(RoadRailPlacementPass.cellKey(pos.getX(), pos.getY(), pos.getZ()));
        }

        int orphans = 0;
        int unsupported = 0;
        for (Map.Entry<BlockPos, String> entry : rails.entrySet()) {
            BlockPos pos = entry.getKey();
            var neighbors = RoadRailPlacementPass.neighborsAt(pos, railCells);
            int degree = count(neighbors.north()) + count(neighbors.south())
                + count(neighbors.east()) + count(neighbors.west());
            // 孤立单格：同批另有铁轨却无四邻 → 断点候选（装饰标记旁应能接到直股）
            if (degree == 0 && rails.size() > 1) {
                orphans++;
            }
            long supportKey = RoadRailPlacementPass.cellKey(pos.getX(), pos.getY() - 1, pos.getZ());
            if (!solidColumns.contains(supportKey)
                    && !railCells.contains(supportKey)) {
                unsupported++;
            }
        }

        result.railOrphanCount = orphans;
        result.railUnsupportedCount = unsupported;
        result.railBlockCount = rails.size();

        Report report = new Report(rails.size(), orphans, unsupported);
        if (report.hasIssues()) {
            LOGGER.warn(
                "铁路连通校验：{} 格铁轨，孤立/断点候选 {}，缺支撑 {}（装饰性交汇，不保证矿车通行）",
                report.railCount(),
                report.orphanRails(),
                report.unsupportedRails());
        }
        return report;
    }

    private static int count(boolean flag) {
        return flag ? 1 : 0;
    }
}
