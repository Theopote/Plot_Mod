package com.plot.plugin.road.pipeline.marking;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 全网放置记录合并后，按邻格占用回填铁轨 {@code shape}，保证落地连通。
 */
public final class RoadRailPlacementPass {
    private RoadRailPlacementPass() {
    }

    public static void apply(RoadGenerationResult result) {
        if (result == null || result.placementRecords.isEmpty()) {
            return;
        }
        Set<Long> railCells = new HashSet<>();
        for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
            BlockRecord record = entry.getValue();
            if (record == null || !RoadRailShapeResolver.isRailBlockId(record.newBlockId)) {
                continue;
            }
            BlockPos pos = entry.getKey();
            railCells.add(cellKey(pos.getX(), pos.getY(), pos.getZ()));
        }
        if (railCells.isEmpty()) {
            return;
        }
        Map<BlockPos, BlockRecord> rewritten = new HashMap<>();
        for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
            BlockRecord record = entry.getValue();
            if (record == null || !RoadRailShapeResolver.isRailBlockId(record.newBlockId)) {
                continue;
            }
            BlockPos pos = entry.getKey();
            RoadRailShapeResolver.RailNeighbors neighbors = neighborsAt(pos, railCells);
            String shaped = RoadRailShapeResolver.resolvePlacement(record.newBlockId, neighbors);
            if (shaped != null && !shaped.equals(record.newBlockId)) {
                rewritten.put(pos, new BlockRecord(pos, record.previousBlockId, shaped));
            }
        }
        result.placementRecords.putAll(rewritten);
    }

    public static RoadRailShapeResolver.RailNeighbors neighborsAt(BlockPos pos, Set<Long> railCells) {
        DirScan north = scan(pos.getX(), pos.getZ() - 1, pos.getY(), railCells);
        DirScan south = scan(pos.getX(), pos.getZ() + 1, pos.getY(), railCells);
        DirScan east = scan(pos.getX() + 1, pos.getZ(), pos.getY(), railCells);
        DirScan west = scan(pos.getX() - 1, pos.getZ(), pos.getY(), railCells);
        return new RoadRailShapeResolver.RailNeighbors(
            north.present,
            south.present,
            east.present,
            west.present,
            north.deltaY,
            south.deltaY,
            east.deltaY,
            west.deltaY);
    }

    private static DirScan scan(int x, int z, int y, Set<Long> railCells) {
        if (railCells.contains(cellKey(x, y, z))) {
            return new DirScan(true, 0);
        }
        if (railCells.contains(cellKey(x, y + 1, z))) {
            return new DirScan(true, 1);
        }
        if (railCells.contains(cellKey(x, y - 1, z))) {
            return new DirScan(true, -1);
        }
        return new DirScan(false, 0);
    }

    /** 与 {@link RoadRailConnectivityValidator} 共用的铁轨格键。 */
    public static long cellKey(int x, int y, int z) {
        return (((long) x) << 42) ^ (((long) (y + 512) & 0x3ffL) << 32) ^ (z & 0xffffffffL);
    }

    private record DirScan(boolean present, int deltaY) {
    }
}
