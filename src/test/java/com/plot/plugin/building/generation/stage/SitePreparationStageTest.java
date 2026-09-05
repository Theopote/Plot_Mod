package com.plot.plugin.building.generation.stage;

import com.plot.plugin.building.generation.siteprep.NaturalDecorationCleaner;
import com.plot.plugin.building.site.BuildingSiteAnalyzer;
import com.plot.plugin.building.site.BuildingSiteColumnSample;
import com.plot.plugin.building.site.SiteIssue;
import com.plot.plugin.building.site.TerrainElevationStrategy;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C01–C06：自然清理 / 构筑物冲突规则（无 Minecraft World 的纯逻辑覆盖）。
 */
class SitePreparationStageTest {

    @Test
    void c01c02c03ClearableNaturalDecorationUsesSharedServiceApi() {
        // 无 World 时 clearable API 安全返回 false；真实草/花/叶清理依赖 EngineeringTerrainService
        // 分类（已在 EngineeringTerrainServiceTest 覆盖）。此处锁定清理器安全上限常量。
        assertTrue(NaturalDecorationCleaner.MAX_TREE_CLEAR_BLOCKS > 0);
        assertTrue(NaturalDecorationCleaner.MAX_TREE_CLEAR_RADIUS > 0);
        assertTrue(NaturalDecorationCleaner.MAX_VERTICAL_RANGE > 0);
        assertFalse(NaturalDecorationCleaner.isNaturalDecoration(null, new BlockPos(0, 64, 0)));
    }

    @Test
    void c04TreeConnectedClearingWithinLimits() {
        Set<Long> logs = Set.of(
            BlockPos.asLong(2, 70, 2),
            BlockPos.asLong(2, 71, 2));
        Set<Long> leaves = Set.of(BlockPos.asLong(3, 71, 2));

        NaturalDecorationCleaner.TreeClearResult cleared = NaturalDecorationCleaner.collectTreeBlocks(
            new BlockPos(2, 70, 2),
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> leaves.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())));

        assertEquals(3, cleared.blocks().size());
        assertFalse(cleared.hitLimit());
    }

    @Test
    void c05ExistingStructureCountedNotAutoDeleted() {
        BuildingSiteColumnSample column = new BuildingSiteColumnSample(
            64, 67, OptionalInt.empty(), 0, 2);
        var analysis = BuildingSiteAnalyzer.analyzeSamples(
            List.of(column), TerrainElevationStrategy.BALANCED);
        assertTrue(analysis.hasIssue(SiteIssue.STRUCTURE_CONFLICT));
        assertEquals(2, analysis.structureConflictCount());
    }

    @Test
    void c06OutsideFootprintTreeRadiusNotCleared() {
        int far = NaturalDecorationCleaner.MAX_TREE_CLEAR_RADIUS + 3;
        Set<Long> logs = Set.of(
            BlockPos.asLong(0, 64, 0),
            BlockPos.asLong(far, 64, 0));

        NaturalDecorationCleaner.TreeClearResult cleared = NaturalDecorationCleaner.collectTreeBlocks(
            new BlockPos(0, 64, 0),
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> false);

        assertEquals(1, cleared.blocks().size());
    }
}
