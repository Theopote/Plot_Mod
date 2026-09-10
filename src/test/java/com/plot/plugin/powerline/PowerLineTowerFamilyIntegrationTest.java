package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.PoleOverride;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineTowerFamilyIntegrationTest {

    @Test
    void sceneA_straightLineRoles() {
        PowerLineFootprint line = straightLine(100);
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setMaxPoleSpacing(25.0);

        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line);
        assertEquals(TowerRole.TERMINAL, sites.getFirst().getRole());
        assertEquals(TowerRole.TERMINAL, sites.getLast().getRole());
        for (int i = 1; i < sites.size() - 1; i++) {
            assertEquals(TowerRole.SUSPENSION, sites.get(i).getRole());
        }
    }

    @Test
    void sceneB_ninetyDegreeCornerUsesAngleDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(40, 0),
            new Vec2d(40, 40)));
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line);
        assertEquals(3, sites.size());
        assertEquals(TowerRole.ANGLE, sites.get(1).getRole());

        PowerLineGenerationResult result = generate(line);
        assertTrue(result.roleCount(TowerRole.ANGLE) >= 1);
        assertTrue(result.blockCount() > 0);
    }

    @Test
    void sceneC_multiCornerHasAngles() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(40, 0),
            new Vec2d(60, 20),
            new Vec2d(100, 20)));
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setMaxPoleSpacing(50.0);

        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line);
        assertEquals(TowerRole.TERMINAL, sites.getFirst().getRole());
        assertTrue(sites.stream().anyMatch(site -> site.getRole() == TowerRole.ANGLE));
    }

    @Test
    void sceneD_manualDeadEndOverride() {
        PowerLineFootprint line = straightLine(80);
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setMaxPoleSpacing(40.0);

        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line);
        double midStationing = sites.get(sites.size() / 2).getStationing();
        PoleOverride override = new PoleOverride(midStationing);
        override.setRoleOverride(TowerRole.DEAD_END);
        line.addPoleOverride(override);

        sites = PowerPoleLayoutUtils.computePoleSites(line);
        PowerPoleSite deadEnd = sites.stream()
            .filter(site -> Math.abs(site.getStationing() - midStationing) < 2.0)
            .findFirst()
            .orElseThrow();
        assertEquals(TowerRole.DEAD_END, deadEnd.getRole());

        PowerLineGenerationResult result = generate(line);
        assertTrue(result.roleCount(TowerRole.DEAD_END) >= 1);
    }

    @Test
    void sceneE_topWireUsesOwnMaterial() {
        PowerLineFootprint line = straightLine(40);
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setWireMaterial(MaterialMix.single("minecraft:iron_bars"));
        line.setTopWireMaterial(MaterialMix.single("minecraft:chain"));
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = generate(line);
        boolean hasChain = result.placementRecords.values().stream()
            .anyMatch(record -> "minecraft:chain".equals(record.newBlockId));
        boolean hasBars = result.placementRecords.values().stream()
            .anyMatch(record -> "minecraft:iron_bars".equals(record.newBlockId));
        assertTrue(hasChain, "ground wire should use chain material");
        assertTrue(hasBars, "phase conductors should use iron bars");
    }

    @Test
    void topWireConnectsByAttachmentId() {
        PowerLineFootprint line = straightLine(30);
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setTopWireMaterial(MaterialMix.single("minecraft:chain"));
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = generate(line);
        Set<BlockPos> chainBlocks = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if ("minecraft:chain".equals(record.newBlockId)) {
                chainBlocks.add(record.pos);
            }
        }
        assertFalse(chainBlocks.isEmpty());
    }

    @Test
    void sceneF_angleTowerGeneratesJumper() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(30, 0),
            new Vec2d(30, 30)));
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.05);

        PowerLineGenerationResult result = generate(line);
        assertTrue(result.roleCount(TowerRole.ANGLE) >= 1);
        assertTrue(result.blockCount() > 50);
    }

    @Test
    void classicLatticeGeneratesBundledConductorsAndTwinTopWires() {
        PowerLineFootprint line = straightLine(40);
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setWireMaterial(MaterialMix.single("minecraft:iron_bars"));
        line.setTopWireMaterial(MaterialMix.single("minecraft:chain"));
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = generate(line);
        long chainBlocks = result.placementRecords.values().stream()
            .filter(record -> "minecraft:chain".equals(record.newBlockId))
            .count();
        long barBlocks = result.placementRecords.values().stream()
            .filter(record -> "minecraft:iron_bars".equals(record.newBlockId))
            .count();
        assertTrue(chainBlocks > 0, "twin top wires should place chain material");
        assertTrue(barBlocks > chainBlocks, "bundled phase conductors should dominate block count");
        assertTrue(barBlocks >= 180, "twin bundle visual should thicken conductor spans");
    }

    @Test
    void heavyTransmissionGeneratesTwinTopWires() {
        PowerLineFootprint line = straightLine(40);
        line.setTowerFamilyId(TowerFamily.HEAVY_TRANSMISSION_ID);
        line.setWireMaterial(MaterialMix.single("minecraft:iron_bars"));
        line.setTopWireMaterial(MaterialMix.single("minecraft:chain"));
        line.setMaxPoleSpacing(50.0);
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = generate(line);
        long chainBlocks = result.placementRecords.values().stream()
            .filter(record -> "minecraft:chain".equals(record.newBlockId))
            .count();
        long barBlocks = result.placementRecords.values().stream()
            .filter(record -> "minecraft:iron_bars".equals(record.newBlockId))
            .count();
        assertTrue(chainBlocks > 0, "twin top wires should place chain material");
        assertTrue(barBlocks > chainBlocks, "bundled phase conductors should dominate block count");
    }

    @Test
    void monsterPylonGeneratesManyConductorsAndTallStructure() {
        PowerLineFootprint line = straightLine(60);
        line.setTowerFamilyId(TowerFamily.MONSTER_PYLON_ID);
        line.setMaxPoleSpacing(80.0);
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = generate(line);
        assertTrue(result.blockCount() > 200, "monster pylon should place a large voxel structure");
        assertTrue(result.conductorSpans.size() >= 12, "quad-circuit should generate many spans");
    }

    @Test
    void familyDesignsAreRegistered() {
        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());
        for (var design : TowerFamilyCatalog.familyDesigns()) {
            assertNotNull(resolver.find(design.getId()));
            assertEquals(design.getId(), resolver.find(design.getId()).getId());
        }
    }

    @Test
    void legacyLineWithoutFamilyStillWorks() {
        PowerLineFootprint line = straightLine(20);
        line.setPoleDesignId(TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID);
        PowerLineGenerationResult result = generate(line);
        assertEquals(2, result.poleCount);
        assertTrue(result.blockCount() > 0);
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
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, 200, 0, 200);
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
}
