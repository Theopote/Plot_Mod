package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineTowerIntegrationTest {

    @Test
    void towerStructureWithAttachmentsProducesThreeConductors() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(40.0);
        line.setPoleDesignId("tapered-lattice");
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(TowerStructurePresets.taperedLatticePoleDesign("tapered-lattice", "Tower"));

        PowerLineGenerationResult result = new PowerLineGenerator(
            identityCoordinates(),
            projection()).generate(line, flatTerrain(64), new PoleDesignResolver(designs));

        assertTrue(result.structureBlockCount > 0);
        Set<Integer> wireZs = wireZValues(result, 64 + 18);
        assertTrue(wireZs.contains(-6));
        assertTrue(wireZs.contains(0));
        assertTrue(wireZs.contains(6));
    }

    @Test
    void cornerTowerRotatesStructureAndConductors() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(40, 0),
            new Vec2d(40, 40)));
        line.setPoleDesignId("tapered-lattice");
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(TowerStructurePresets.taperedLatticePoleDesign("tapered-lattice", "Tower"));

        PowerLineGenerationResult result = new PowerLineGenerator(
            identityCoordinates(),
            projection()).generate(line, flatTerrain(64), new PoleDesignResolver(designs));

        assertTrue(result.structureBlockCount > 0);
        assertFalse(result.warnings.stream().anyMatch(w -> w.contains("missing_attachment_downstream")));
    }

    @Test
    void legacyWoodPoleUnchangedWithTowerCatalogPresent() {
        PowerLineFootprint legacy = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        legacy.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        legacy.setMaxPoleSpacing(50.0);
        legacy.setSagRatio(0.0);

        PowerLineGenerationResult legacyResult = new PowerLineGenerator(
            identityCoordinates(),
            projection()).generate(
                legacy,
                flatTerrain(64),
                new PoleDesignResolver(new PowerLineDesignProject()));

        assertTrue(legacyResult.structureBlockCount == 0);
        PoleDesign pole = PoleDesignCatalog.simpleWoodPole();
        int wireY = 64 + (int) pole.getAttachments().getFirst().getVerticalOffset();
        WireTestSupport.assertHorizontalWireCoversX(legacyResult, wireY, 0, 20);
    }

    @Test
    void diagonalPathRotatesTowerAt45Degrees() {
        PowerLineFootprint line = WireTestSupport.diagonalLine45(30.0);
        line.setPoleDesignId("tapered-lattice");
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(TowerStructurePresets.taperedLatticePoleDesign("tapered-lattice", "Tower"));

        PowerLineGenerationResult result = new PowerLineGenerator(
            identityCoordinates(),
            projection()).generate(line, flatTerrain(64), new PoleDesignResolver(designs));

        assertTrue(result.structureBlockCount > 0);
        Set<Integer> xs = new HashSet<>();
        Set<Integer> zs = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if ("minecraft:iron_bars".equals(record.newBlockId) && record.pos.getY() > 64) {
                xs.add(record.pos.getX());
                zs.add(record.pos.getZ());
            }
        }
        assertTrue(xs.size() > 1);
        assertTrue(zs.size() > 1);
    }

    @Test
    void unevenTerrainProducesBaseWarning() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(20.0);
        line.setPoleDesignId("tapered-lattice");

        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(TowerStructurePresets.taperedLatticePoleDesign("tapered-lattice", "Tower"));

        PowerLineGenerationResult result = new PowerLineGenerator(
            identityCoordinates(),
            projection()).generate(
                line,
                unevenTerrain(),
                new PoleDesignResolver(designs));

        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("tower_base_uneven")));
    }

    private static Set<Integer> wireZValues(PowerLineGenerationResult result, int wireY) {
        Set<Integer> zs = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == wireY && "minecraft:iron_bars".equals(record.newBlockId)) {
                zs.add(record.pos.getZ());
            }
        }
        return zs;
    }

    private static TerrainSampler unevenTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint.x < 5 ? 60 : 66;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }

    private static ICoordinateService identityCoordinates() {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, 100, 0, 100);
            }
        };
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
}
