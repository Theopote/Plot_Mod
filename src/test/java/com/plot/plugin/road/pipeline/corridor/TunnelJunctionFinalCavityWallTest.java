package com.plot.plugin.road.pipeline.corridor;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最终洞腔并集后，连接处不得把进口/圆角交界列当成外墙柱。
 */
class TunnelJunctionFinalCavityWallTest {

    @Test
    void wallRingUsesUnionBoundaryNotInternalApproachSeam() {
        Set<ColumnKey> filletCavity = new LinkedHashSet<>();
        Set<ColumnKey> approachCavity = new LinkedHashSet<>();
        // 3x3 广场核
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                filletCavity.add(new ColumnKey(x, z));
            }
        }
        // 北向进口洞腔伸入核内（共享 (0,1)）
        for (int z = 1; z <= 4; z++) {
            approachCavity.add(new ColumnKey(0, z));
            approachCavity.add(new ColumnKey(-1, z));
            approachCavity.add(new ColumnKey(1, z));
        }

        Set<ColumnKey> finalCavity = new LinkedHashSet<>(filletCavity);
        finalCavity.addAll(approachCavity);

        Set<ColumnKey> walls = TunnelJunctionShellPass.wallRingAround(finalCavity, 1);

        // 内部连接列 (0,1) 属于并集内部边界邻接洞腔，不得成为墙柱
        assertFalse(walls.contains(new ColumnKey(0, 1)),
            "approach/fillet seam column must not become an internal wall pillar");
        // 外缘仍应有墙
        assertTrue(walls.contains(new ColumnKey(0, 5))
                || walls.contains(new ColumnKey(2, 0))
                || walls.contains(new ColumnKey(-2, 0)),
            "outer cavity boundary must still host wall lining");
    }

    @Test
    void cavityColumnsFromOpenVolumeDedupesXz() {
        Set<BlockPos> volume = Set.of(
            new BlockPos(3, 65, 4),
            new BlockPos(3, 66, 4),
            new BlockPos(3, 67, 5));
        Set<ColumnKey> columns = TunnelJunctionShellPass.cavityColumnsFromOpenVolume(volume);
        assertTrue(columns.contains(new ColumnKey(3, 4)));
        assertTrue(columns.contains(new ColumnKey(3, 5)));
        assertTrue(columns.size() == 2);
    }
}
