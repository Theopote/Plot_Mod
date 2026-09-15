package com.plot.plugin.powerline.engineering.optimization;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTowerOptimizationProposerTest {

    @Test
    void disabledAutomaticSelectionIsBlockedUnlessAssumed() {
        PowerLineFootprint line = straightLine(80);
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setMaxPoleSpacing(40.0);
        line.setAutomaticTowerSelectionEnabled(true);

        PowerLineGenerationResult generation = generate(line);
        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());
        OptimizationResult enabled = AutoTowerOptimizationProposer.propose(generation, line, resolver);

        line.setAutomaticTowerSelectionEnabled(false);
        assertTrue(AutoTowerOptimizationProposer.propose(generation, line, resolver).getActions().isEmpty());

        OptimizationResult assumed = AutoTowerOptimizationProposer.propose(generation, line, resolver, true);
        assertEquals(enabled.getActions().size(), assumed.getActions().size());
    }

    private static PowerLineFootprint straightLine(double length) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(length, 0)));
        line.setSagRatio(0.0);
        line.setMaxPoleSpacing(100.0);
        return line;
    }

    private static PowerLineGenerationResult generate(PowerLineFootprint line) {
        return new PowerLineGenerator(identityCoordinates(), projection()).generate(
            line,
            flatTerrain(64),
            new PoleDesignResolver(new PowerLineDesignProject()));
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }

    private static ICoordinateService identityCoordinates() {
        return com.plot.test.world.IdentityCoordinateService.INSTANCE;
    }

    private static IBlockProjectionService projection() {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }
}
