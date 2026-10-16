package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spatial index of final placement records by world XZ column.
 */
public final class PlacementRecordColumnIndex {
    private final Map<ColumnKey, List<BlockPos>> positionsByColumn;
    private final Map<BlockPos, BlockRecord> records;

    private PlacementRecordColumnIndex(
            Map<ColumnKey, List<BlockPos>> positionsByColumn,
            Map<BlockPos, BlockRecord> records) {
        this.positionsByColumn = positionsByColumn;
        this.records = records;
    }

    public static PlacementRecordColumnIndex build(RoadGenerationResult aggregate) {
        if (aggregate == null || aggregate.placementRecords == null || aggregate.placementRecords.isEmpty()) {
            return new PlacementRecordColumnIndex(Map.of(), Map.of());
        }
        Map<ColumnKey, List<BlockPos>> byColumn = new HashMap<>();
        for (BlockPos pos : aggregate.placementRecords.keySet()) {
            if (pos == null) {
                continue;
            }
            ColumnKey key = new ColumnKey(pos.getX(), pos.getZ());
            byColumn.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pos);
        }
        for (List<BlockPos> positions : byColumn.values()) {
            positions.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        }
        return new PlacementRecordColumnIndex(
            Collections.unmodifiableMap(byColumn),
            aggregate.placementRecords);
    }

    public List<BlockPos> positionsInColumn(int worldX, int worldZ) {
        return positionsByColumn.getOrDefault(new ColumnKey(worldX, worldZ), List.of());
    }

    public BlockRecord recordAt(BlockPos pos) {
        return records.get(pos);
    }

    public RoadSolidLayer layerAt(BlockPos pos, RoadGenerationResult aggregate) {
        if (aggregate == null || aggregate.placementLayers == null || pos == null) {
            return null;
        }
        return aggregate.placementLayers.get(pos);
    }

    public boolean isTunnelAir(BlockPos pos, RoadGenerationResult aggregate) {
        BlockRecord record = recordAt(pos);
        if (record == null || !"minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        RoadSolidLayer layer = layerAt(pos, aggregate);
        return layer == RoadSolidLayer.TUNNEL
            || (aggregate != null && aggregate.tunnelBlocks.contains(pos));
    }
}
