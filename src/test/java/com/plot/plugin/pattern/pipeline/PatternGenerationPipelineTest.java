package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.PatternGenerationIssue;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSpace;
import com.plot.plugin.pattern.space.PatternSampling;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternGenerationPipelineTest {

    @Test
    void samplingAndResolverUsePatternSpaceCoordinates() {
        ProceduralPatternConfig pattern = new ProceduralPatternConfig();
        pattern.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        pattern.setMaterials(List.of("minecraft:white_wool", "minecraft:black_wool"));
        pattern.setTileSize(2.0);

        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)));
        footprint.setPattern(pattern);

        PatternSpace space = PatternSpace.fromFootprint(footprint);
        List<PatternSample> samples = PatternSampling.collectFootprintSamples(footprint.getOuterPoints());
        assertFalse(samples.isEmpty());

        PatternMaterialResolver resolver = new ProceduralPatternMaterialResolver(pattern);
        assertEquals("minecraft:white_wool", resolver.resolveMaterial(space, new PatternSample(1, 1)));
        assertEquals("minecraft:black_wool", resolver.resolveMaterial(space, new PatternSample(3, 1)));
    }

    @Test
    void holesExcludeInteriorCells() {
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(6, 0),
            new Vec2d(6, 6),
            new Vec2d(0, 6)));
        footprint.setHoles(List.of(List.of(
            new Vec2d(2, 2),
            new Vec2d(4, 2),
            new Vec2d(4, 4),
            new Vec2d(2, 4))));

        int withoutHole = PatternSampling.collectFootprintSamples(footprint.getOuterPoints()).size();
        int withHole = PatternSampling.collectFootprintSamples(
            footprint.getOuterPoints(),
            footprint.getHoles()).size();

        assertTrue(withHole < withoutHole);
        assertTrue(withHole > 0);
    }

    @Test
    void pipelineProjectsSamplesToPlacementRecords() {
        ProceduralPatternConfig pattern = new ProceduralPatternConfig();
        pattern.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        pattern.setMaterials(List.of("minecraft:white_wool", "minecraft:black_wool"));
        pattern.setTileSize(2.0);

        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)));
        footprint.setPattern(pattern);

        IBlockProjectionService projection = new IBlockProjectionService() {
            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:grass_block";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return false;
            }
        };

        PatternGenerationPipeline pipeline = new PatternGenerationPipeline(
            IdentityCoordinateService.INSTANCE,
            projection);
        PatternGenerationResult result = pipeline.generate(
            footprint,
            new ProceduralPatternMaterialResolver(pattern),
            null);

        assertTrue(result.getBlockCount() > 0);
        assertTrue(result.placementRecords.values().stream()
            .anyMatch(record -> "minecraft:white_wool".equals(record.newBlockId)));
        assertTrue(result.placementRecords.values().stream()
            .anyMatch(record -> "minecraft:black_wool".equals(record.newBlockId)));
        assertTrue(result.placementRecords.values().stream()
            .allMatch(record -> record.pos.getY() == 64));
    }

    @Test
    void rejectsRegionWithExcessiveEstimatedSampleCount() {
        PatternFootprint footprint = new PatternFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(1000, 0),
            new Vec2d(1000, 1000),
            new Vec2d(0, 1000)));

        PatternGenerationPipeline pipeline = new PatternGenerationPipeline(
            IdentityCoordinateService.INSTANCE,
            new IBlockProjectionService() {
                @Override
                public PlacementReadiness checkWorldModificationReadiness() {
                    return PlacementReadiness.ok();
                }

                @Override
                public String getBlockIdAt(BlockPos pos) {
                    return "minecraft:grass_block";
                }

                @Override
                public boolean setBlockAt(BlockPos pos, String blockId) {
                    return false;
                }
            });

        PatternGenerationResult result = pipeline.generate(
            footprint,
            new ProceduralPatternMaterialResolver(new ProceduralPatternConfig()),
            null);

        assertEquals(PatternGenerationIssue.REGION_TOO_LARGE, result.getIssue());
        assertTrue(result.placementRecords.isEmpty());
    }
}
