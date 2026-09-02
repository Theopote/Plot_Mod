package com.plot.plugin.earthwork.voxel;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.grading.CutFillClassifier;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将单列挖填决策落地为 {@link BlockRecord}，并累加方量指标。
 */
public final class EarthworkVoxelizer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkVoxelizer");

  /**
   * 测试/兼容用方块采样入口。
   */
    @FunctionalInterface
    public interface BlockSampler {
        String sampleBlockId(BlockPos pos);
    }

    private final BlockSampler blockSampler;

    public EarthworkVoxelizer(BlockSampler blockSampler) {
        this.blockSampler = blockSampler;
    }

    /**
     * @deprecated 使用 {@link BlockSampler}。
     */
    @Deprecated
    public EarthworkVoxelizer(com.plot.plugin.earthwork.EarthworkGenerator.BlockSampler legacySampler) {
        this.blockSampler = legacySampler != null ? legacySampler::sampleBlockId : null;
    }

    /**
     * 对单列应用挖填；返回非零方量时表明该列参与了土方。
     */
    public CutFillClassifier.ColumnDelta applyColumn(
            GradingRegion region,
            World world,
            TerrainSnapshot.Column column,
            int targetElevation,
            int previewGridSize,
            EarthworkGenerationResult result,
            SiteEarthworkReport.VolumeMetrics totals,
            SiteEarthworkReport.VolumeMetrics zoneMetrics) {
        int groundY = column.groundY();
        CutFillClassifier.Kind kind = CutFillClassifier.kind(groundY, targetElevation);
        if (kind != CutFillClassifier.Kind.NONE
            && EarthworkGeometryUtils.matchesPreviewGrid(column.center(), previewGridSize)) {
            result.gridSamples.add(new EarthworkGenerationResult.GridSample(
                column.center(),
                groundY,
                toLegacyChangeType(kind)));
        }

        CutFillClassifier.ColumnDelta delta = CutFillClassifier.delta(groundY, targetElevation);
        if (delta.isNoOp()) {
            return delta;
        }

        String fillBlockId = EarthworkGeometryUtils.resolveFillBlockId(region.getFillMaterial());
        String cutSurfaceBlockId = EarthworkGeometryUtils.resolveCutSurfaceBlockId(region.getCutExposeMaterial());
        long cutChanged = 0L;
        long fillChanged = 0L;

        if (delta.cutVolume() > 0L) {
            for (int y = targetElevation + 1; y <= groundY; y++) {
                BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
                if (recordBlock(result, world, pos, EarthworkGeometryUtils.EXCAVATION_BLOCK_ID,
                    EarthworkGenerationResult.ChangeType.CUT)) {
                    cutChanged++;
                }
            }
            if (cutSurfaceBlockId != null) {
                BlockPos surfacePos = new BlockPos(column.worldX(), targetElevation, column.worldZ());
                if (recordBlock(result, world, surfacePos, cutSurfaceBlockId, EarthworkGenerationResult.ChangeType.CUT)) {
                    cutChanged++;
                }
            }
        } else if (delta.fillVolume() > 0L) {
            for (int y = groundY + 1; y <= targetElevation; y++) {
                BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
                if (recordBlock(result, world, pos, fillBlockId, EarthworkGenerationResult.ChangeType.FILL)) {
                    fillChanged++;
                }
            }
        }

        if (delta.cutVolume() > 0L) {
            totals.addCut(delta.cutVolume(), cutChanged);
            if (zoneMetrics != null) {
                zoneMetrics.addCut(delta.cutVolume(), cutChanged);
            }
        }
        if (delta.fillVolume() > 0L) {
            totals.addFill(delta.fillVolume(), fillChanged);
            if (zoneMetrics != null) {
                zoneMetrics.addFill(delta.fillVolume(), fillChanged);
            }
        }
        return delta;
    }

    public boolean recordBlock(
            EarthworkGenerationResult result,
            World world,
            BlockPos pos,
            String newBlockId,
            EarthworkGenerationResult.ChangeType changeType) {
        if (result.placementRecords.containsKey(pos)) {
            return false;
        }
        String previous = getBlockIdAt(world, pos);
        if (!shouldApplyBlockChange(previous, newBlockId)) {
            return false;
        }
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
        result.changeTypes.put(pos, changeType);
        return true;
    }

    public static boolean shouldApplyBlockChange(String previousBlockId, String newBlockId) {
        return !normalizeBlockId(previousBlockId).equals(normalizeBlockId(newBlockId));
    }

    public static String normalizeBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return Registries.BLOCK.getId(Blocks.AIR).toString();
        }
        return blockId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String getBlockIdAt(World world, BlockPos pos) {
        if (blockSampler != null) {
            String sampled = blockSampler.sampleBlockId(pos);
            if (sampled != null) {
                return sampled;
            }
        }
        if (world == null || pos == null) {
            return Registries.BLOCK.getId(Blocks.AIR).toString();
        }
        try {
            Block block = world.getBlockState(pos).getBlock();
            return Registries.BLOCK.getId(block).toString();
        } catch (Exception e) {
            LOGGER.warn("读取方块失败 {}: {}", pos, e.getMessage());
            return Registries.BLOCK.getId(Blocks.AIR).toString();
        }
    }

    private static EarthworkGenerationResult.ChangeType toLegacyChangeType(CutFillClassifier.Kind kind) {
        return switch (kind) {
            case CUT -> EarthworkGenerationResult.ChangeType.CUT;
            case FILL -> EarthworkGenerationResult.ChangeType.FILL;
            case NONE -> null;
        };
    }
}
