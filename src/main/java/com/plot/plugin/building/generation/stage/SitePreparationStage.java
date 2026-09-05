package com.plot.plugin.building.generation.stage;

import com.plot.api.world.IBlockProjectionService;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.siteprep.NaturalDecorationCleaner;
import com.plot.plugin.building.site.BuildingSiteAnalyzer;
import com.plot.plugin.building.site.BuildingSiteColumnSample;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/**
 * 场地准备：清理 footprint 列上的自然附着物；记录人工构筑冲突 warning。
 * <p>
 * 不做地形切填（见 {@link FoundationGenerationStage}）。
 */
public final class SitePreparationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "site_preparation";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        BuildingGenerationResult result = context.getResult();
        World world = context.getWorld();
        if (world == null) {
            return;
        }

        IBlockProjectionService projection = context.getProjectionService();
        Set<Long> cleared = new HashSet<>();
        boolean treeLimitWarned = false;

        for (BuildingGenerationContext.GridCell cell : context.getFootprintCells()) {
            BlockPos column = context.canvasToColumn(cell.center());
            BuildingSiteColumnSample sample = context.siteColumnSample(column.getX(), column.getZ());
            if (sample == null) {
                sample = BuildingSiteAnalyzer.sampleColumn(world, column.getX(), column.getZ());
            }

            int groundY = sample.groundY();
            int rawY = sample.rawSurfaceY();
            if (rawY < groundY + 1) {
                continue;
            }

            for (int y = groundY + 1; y <= rawY; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                long key = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
                if (cleared.contains(key)) {
                    continue;
                }
                if (!NaturalDecorationCleaner.isNaturalDecoration(world, pos)) {
                    continue;
                }

                if (NaturalDecorationCleaner.isLog(world, pos)) {
                    NaturalDecorationCleaner.TreeClearResult tree =
                        NaturalDecorationCleaner.collectTreeBlocks(world, pos);
                    if (tree.hitLimit() && !treeLimitWarned) {
                        if (!result.warnings.contains("plugin.building.warn.tree_clear_limit")) {
                            result.warnings.add("plugin.building.warn.tree_clear_limit");
                        }
                        treeLimitWarned = true;
                    }
                    for (BlockPos treePos : tree.blocks()) {
                        long treeKey = BlockPos.asLong(treePos.getX(), treePos.getY(), treePos.getZ());
                        if (cleared.add(treeKey)) {
                            BuildingBlockWriter.recordBlock(
                                result, treePos, "minecraft:air", projection);
                        }
                    }
                } else {
                    cleared.add(key);
                    BuildingBlockWriter.recordBlock(result, pos, "minecraft:air", projection);
                }
            }
        }

        if (context.getSiteAnalysis() != null
                && context.getSiteAnalysis().structureConflictCount() > 0
                && !result.warnings.contains("plugin.building.warn.structure_conflict")) {
            result.warnings.add("plugin.building.warn.structure_conflict");
        }
    }
}
