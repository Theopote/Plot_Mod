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
 * 装饰性铁路交汇的轻量连通校验。
 * <ul>
 *   <li>孤立轨格：无四邻占用</li>
 *   <li>轨向不连续：邻格存在但 shape 未双向伸出</li>
 *   <li>已确认缺支撑：生成记录中下方为空气</li>
 *   <li>支撑信息不足：生成记录未覆盖下方格（世界可能已有实体）</li>
 * </ul>
 */
public final class RoadRailConnectivityValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadRailConnectivity");

    private RoadRailConnectivityValidator() {
    }

    public record Report(
            int railCount,
            int orphanRails,
            int shapeDiscontinuities,
            int unsupportedRails,
            int supportUnknownRails) {
        public boolean hasIssues() {
            return orphanRails > 0
                || shapeDiscontinuities > 0
                || unsupportedRails > 0;
        }
    }

    public static Report validate(RoadGenerationResult result) {
        if (result == null || result.placementRecords.isEmpty()) {
            return new Report(0, 0, 0, 0, 0);
        }
        Map<BlockPos, String> rails = new HashMap<>();
        Set<Long> solidCells = new HashSet<>();
        Set<Long> airCells = new HashSet<>();
        Set<Long> knownCells = new HashSet<>();
        for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
            BlockRecord record = entry.getValue();
            if (record == null || record.newBlockId == null) {
                continue;
            }
            BlockPos pos = entry.getKey();
            long key = RoadRailPlacementPass.cellKey(pos.getX(), pos.getY(), pos.getZ());
            knownCells.add(key);
            if (RoadRailShapeResolver.isRailBlockId(record.newBlockId)) {
                rails.put(pos, record.newBlockId);
            } else if ("minecraft:air".equals(BlockSpec.parse(record.newBlockId).blockId())) {
                airCells.add(key);
            } else {
                solidCells.add(key);
            }
        }
        if (rails.isEmpty()) {
            return new Report(0, 0, 0, 0, 0);
        }

        Set<Long> railCells = new HashSet<>();
        for (BlockPos pos : rails.keySet()) {
            railCells.add(RoadRailPlacementPass.cellKey(pos.getX(), pos.getY(), pos.getZ()));
        }

        int orphans = 0;
        int discontinuities = 0;
        int unsupported = 0;
        int supportUnknown = 0;
        Set<Long> countedDiscontinuityEdges = new HashSet<>();

        for (Map.Entry<BlockPos, String> entry : rails.entrySet()) {
            BlockPos pos = entry.getKey();
            String shapeSpec = entry.getValue();
            var neighbors = RoadRailPlacementPass.neighborsAt(pos, railCells);
            int degree = count(neighbors.north()) + count(neighbors.south())
                + count(neighbors.east()) + count(neighbors.west());
            if (degree == 0 && rails.size() > 1) {
                orphans++;
            }

            discontinuities += countShapeBreak(
                pos, shapeSpec, neighbors, rails, countedDiscontinuityEdges);

            long supportKey = RoadRailPlacementPass.cellKey(pos.getX(), pos.getY() - 1, pos.getZ());
            if (solidCells.contains(supportKey) || railCells.contains(supportKey)) {
                continue;
            }
            if (airCells.contains(supportKey)) {
                unsupported++;
            } else if (!knownCells.contains(supportKey)) {
                supportUnknown++;
            } else {
                unsupported++;
            }
        }

        result.railOrphanCount = orphans;
        result.railShapeDiscontinuityCount = discontinuities;
        result.railUnsupportedCount = unsupported;
        result.railSupportUnknownCount = supportUnknown;
        result.railBlockCount = rails.size();

        Report report = new Report(rails.size(), orphans, discontinuities, unsupported, supportUnknown);
        if (report.hasIssues() || supportUnknown > 0) {
            LOGGER.warn(
                "铁路连通校验：{} 格铁轨，孤立 {}，轨向不连续 {}，确认缺支撑 {}，支撑未知 {}（装饰性交汇）",
                report.railCount(),
                report.orphanRails(),
                report.shapeDiscontinuities(),
                report.unsupportedRails(),
                report.supportUnknownRails());
        }
        return report;
    }

    private static int countShapeBreak(
            BlockPos pos,
            String shapeSpec,
            RoadRailShapeResolver.RailNeighbors neighbors,
            Map<BlockPos, String> rails,
            Set<Long> countedEdges) {
        int breaks = 0;
        breaks += checkPair(
            pos, shapeSpec, neighbors.north(), 0, -1, neighbors.northDeltaY(),
            RoadRailShapeResolver.Cardinal.NORTH, rails, countedEdges);
        breaks += checkPair(
            pos, shapeSpec, neighbors.south(), 0, 1, neighbors.southDeltaY(),
            RoadRailShapeResolver.Cardinal.SOUTH, rails, countedEdges);
        breaks += checkPair(
            pos, shapeSpec, neighbors.east(), 1, 0, neighbors.eastDeltaY(),
            RoadRailShapeResolver.Cardinal.EAST, rails, countedEdges);
        breaks += checkPair(
            pos, shapeSpec, neighbors.west(), -1, 0, neighbors.westDeltaY(),
            RoadRailShapeResolver.Cardinal.WEST, rails, countedEdges);
        return breaks;
    }

    private static int checkPair(
            BlockPos pos,
            String shapeSpec,
            boolean present,
            int dx,
            int dz,
            int deltaY,
            RoadRailShapeResolver.Cardinal dir,
            Map<BlockPos, String> rails,
            Set<Long> countedEdges) {
        if (!present) {
            return 0;
        }
        BlockPos other = new BlockPos(pos.getX() + dx, pos.getY() + deltaY, pos.getZ() + dz);
        String otherSpec = rails.get(other);
        if (otherSpec == null) {
            // ±1 高差时可能键不同；再试同柱邻高
            otherSpec = rails.get(new BlockPos(pos.getX() + dx, pos.getY() + 1, pos.getZ() + dz));
            if (otherSpec == null) {
                otherSpec = rails.get(new BlockPos(pos.getX() + dx, pos.getY() - 1, pos.getZ() + dz));
            }
            if (otherSpec == null) {
                return 0;
            }
        }
        long edge = edgeKey(pos, other);
        if (!countedEdges.add(edge)) {
            return 0;
        }
        if (RoadRailShapeResolver.mutuallyConnected(shapeSpec, dir, otherSpec)) {
            return 0;
        }
        return 1;
    }

    private static long edgeKey(BlockPos a, BlockPos b) {
        long ka = RoadRailPlacementPass.cellKey(a.getX(), a.getY(), a.getZ());
        long kb = RoadRailPlacementPass.cellKey(b.getX(), b.getY(), b.getZ());
        return ka < kb ? (ka * 31) ^ kb : (kb * 31) ^ ka;
    }

    private static int count(boolean flag) {
        return flag ? 1 : 0;
    }
}
