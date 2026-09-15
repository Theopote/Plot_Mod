package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StructureAwareDecorationClearanceTest {

    private static final String GRASS = "minecraft:short_grass";
    private static final String AIR = "minecraft:air";

    @Test
    void clearsDecorationsAdjacentToStructureBlocks() {
        TerrainSampler terrain = woodedColumnSampler();
        Map<BlockPos, String> world = new HashMap<>();
        BlockPos structure = new BlockPos(0, 65, 0);
        BlockPos adjacentGrass = new BlockPos(1, 65, 0);
        world.put(adjacentGrass, GRASS);

        PowerLineGenerationResult result = new PowerLineGenerationResult(testFootprint());
        StructureAwareDecorationClearance.clearNearStructureBlocks(
            Set.of(structure),
            terrain,
            result,
            projectionFor(world));

        assertTrue(isClearanceAir(result, adjacentGrass));
        assertEquals(GRASS, result.placementRecords.get(adjacentGrass).previousBlockId);
    }

    @Test
    void doesNotClearDecorationsOutsideInflateRadius() {
        TerrainSampler terrain = woodedColumnSampler();
        Map<BlockPos, String> world = new HashMap<>();
        BlockPos distantGrass = new BlockPos(4, 65, 0);
        world.put(distantGrass, GRASS);

        PowerLineGenerationResult result = new PowerLineGenerationResult(testFootprint());
        StructureAwareDecorationClearance.clearNearStructureBlocks(
            Set.of(new BlockPos(0, 65, 0)),
            1,
            terrain,
            result,
            projectionFor(world));

        assertFalse(result.placementRecords.containsKey(distantGrass));
    }

    @Test
    void doesNotOverrideStructureBlocksWithClearance() {
        TerrainSampler terrain = woodedColumnSampler();
        Map<BlockPos, String> world = new HashMap<>();
        BlockPos shared = new BlockPos(2, 66, 0);
        world.put(shared, GRASS);

        PowerLineGenerationResult result = new PowerLineGenerationResult(testFootprint());
        PlacementWriter.put(
            result,
            projectionFor(world),
            shared,
            "minecraft:iron_bars",
            PlacementCategory.STRUCTURE);

        StructureAwareDecorationClearance.clearNearStructureBlocks(
            Set.of(shared),
            1,
            terrain,
            result,
            projectionFor(world));

        assertEquals("minecraft:iron_bars", result.placementRecords.get(shared).newBlockId);
        assertEquals(PlacementCategory.STRUCTURE, result.placementCategories.get(shared));
    }

    @Test
    void generatorClearsNearTowerButNotDistantBboxDecorations() {
        PoleDesign towerDesign = TowerStructurePresets.taperedLatticePoleDesign("tapered-lattice", "Tower");
        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(towerDesign);

        PowerLineGenerationResult probe = generateSingleTower(designs, projectionFor(new HashMap<>()));
        Set<BlockPos> structureBlocks = structureBlocks(probe);
        BlockPos legBlock = findSurfaceStructureBlock(structureBlocks);
        BlockPos nearDecoration = findAdjacentClearableCell(legBlock, structureBlocks);
        BlockPos farDecoration = distantBboxDecoration(towerDesign, structureBlocks);

        Map<BlockPos, String> world = new HashMap<>();
        world.put(nearDecoration, GRASS);
        world.put(farDecoration, GRASS);

        PowerLineGenerationResult result = generateSingleTower(designs, projectionFor(world));

        assertTrue(
            isClearanceAir(result, nearDecoration),
            "expected adjacent decoration cleared at " + nearDecoration);
        assertFalse(
            isClearanceAir(result, farDecoration),
            "expected distant in-bbox decoration left untouched at " + farDecoration);
        assertNotEquals(AIR, blockIdAt(result, legBlock));
    }

    @Test
    void generatorDoesNotUseBboxClearanceForTowerSites() {
        PoleDesign towerDesign = TowerStructurePresets.taperedLatticePoleDesign("tapered-lattice", "Tower");
        PoleSiteDecorationClearance.SiteFootprint footprint =
            PoleSiteDecorationClearance.computeFootprint(towerDesign, 24.0);
        BlockPos onlyInBbox = new BlockPos(footprint.lateralRadiusBlocks(), 65, 0);

        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(towerDesign);
        Map<BlockPos, String> world = Map.of(onlyInBbox, GRASS);

        PowerLineGenerationResult result = generateSingleTower(designs, projectionFor(world));

        assertFalse(
            isClearanceAir(result, onlyInBbox),
            "tower path should not apply bbox clearance at " + onlyInBbox);
    }

    private static PowerLineGenerationResult generateSingleTower(
            PowerLineDesignProject designs,
            IBlockProjectionService projection) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setPoleDesignId("tapered-lattice");
        line.setMaxPoleSpacing(100.0);
        line.setSagRatio(0.0);
        return new PowerLineGenerator(IdentityCoordinateService.INSTANCE, projection)
            .generate(line, woodedColumnSampler(), new PoleDesignResolver(designs));
    }

    private static Set<BlockPos> structureBlocks(PowerLineGenerationResult result) {
        Set<BlockPos> blocks = new LinkedHashSet<>();
        for (BlockPos pos : result.placementRecords.keySet()) {
            if (isStructureLike(result, pos)) {
                blocks.add(pos);
            }
        }
        return blocks;
    }

    private static BlockPos findSurfaceStructureBlock(Set<BlockPos> structureBlocks) {
        return structureBlocks.stream()
            .filter(pos -> pos.getY() >= 65)
            .min(Comparator.comparingInt(Vec3i::getY))
            .orElseThrow();
    }

    private static BlockPos findAdjacentClearableCell(BlockPos legBlock, Set<BlockPos> structureBlocks) {
        for (BlockPos candidate : inflate(legBlock, 1)) {
            if (candidate.equals(legBlock) || structureBlocks.contains(candidate)) {
                continue;
            }
            if (candidate.getY() < 65 || candidate.getY() > 67) {
                continue;
            }
            return candidate;
        }
        throw new IllegalStateException("no adjacent clearable cell near " + legBlock);
    }

    private static BlockPos distantBboxDecoration(PoleDesign design, Set<BlockPos> structureBlocks) {
        PoleSiteDecorationClearance.SiteFootprint footprint =
            PoleSiteDecorationClearance.computeFootprint(design, 24.0);
        Set<BlockPos> inflatedStructure = new LinkedHashSet<>();
        for (BlockPos structure : structureBlocks) {
            inflatedStructure.addAll(inflate(structure, 1));
        }
        for (int lateral = footprint.lateralRadiusBlocks(); lateral >= 0; lateral--) {
            for (int longitudinal = footprint.longitudinalRadiusBlocks(); longitudinal >= 0; longitudinal--) {
                for (int[] quadrant : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
                    BlockPos candidate = new BlockPos(
                        quadrant[0] * lateral,
                        65,
                        quadrant[1] * longitudinal);
                    if (!inflatedStructure.contains(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        throw new IllegalStateException("unable to find in-bbox decoration outside structure inflate zone");
    }

    private static Set<BlockPos> inflate(BlockPos origin, int radius) {
        Set<BlockPos> expanded = new LinkedHashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    expanded.add(origin.add(dx, dy, dz));
                }
            }
        }
        return expanded;
    }

    private static boolean isStructureLike(PowerLineGenerationResult result, BlockPos pos) {
        PlacementCategory category = result.placementCategories.get(pos);
        return category == PlacementCategory.STRUCTURE
            || category == PlacementCategory.ARM
            || category == PlacementCategory.FOUNDATION;
    }

    private static boolean isClearanceAir(PowerLineGenerationResult result, BlockPos pos) {
        if (!result.placementRecords.containsKey(pos)) {
            return false;
        }
        return PlacementCategory.CLEARANCE == result.placementCategories.get(pos)
            && AIR.equals(result.placementRecords.get(pos).newBlockId);
    }

    private static String blockIdAt(PowerLineGenerationResult result, BlockPos pos) {
        return Optional.ofNullable(result.placementRecords.get(pos))
            .map(record -> record.newBlockId)
            .orElse(null);
    }

    private static PowerLineFootprint testFootprint() {
        PowerLineFootprint footprint = new PowerLineFootprint(java.util.List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        footprint.setMaxPoleSpacing(50.0);
        return footprint;
    }

    private static TerrainSampler woodedColumnSampler() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 67;
            }

            @Override
            public boolean isSolidBlock(int worldX, int blockY, int worldZ) {
                return blockY <= 64;
            }

            @Override
            public boolean isRoadClearableDecoration(int worldX, int blockY, int worldZ) {
                return blockY >= 65 && blockY <= 67;
            }
        };
    }

    private static IBlockProjectionService projectionFor(Map<BlockPos, String> world) {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return world.getOrDefault(pos, AIR);
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                world.put(pos.toImmutable(), blockId);
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }
}
