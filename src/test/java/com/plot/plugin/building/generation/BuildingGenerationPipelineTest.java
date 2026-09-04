package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.building.generation.stage.BuildingGenerationStage;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BuildingGenerationPipeline 架构测试。
 */
class BuildingGenerationPipelineTest {

    @Test
    void defaultStageOrderIncludesAccessoryBeforeOpening() {
        BuildingGenerationPipeline pipeline = BuildingGenerationPipeline.createDefault();
        assertEquals(
            List.of("foundation", "wall", "floor", "roof", "accessory", "opening"),
            pipeline.getStageNames());
    }

    @Test
    void stagesExecuteInConfiguredOrder() {
        List<String> executed = new ArrayList<>();
        BuildingGenerationPipeline pipeline = new BuildingGenerationPipeline(List.of(
            recordingStage("a", executed),
            recordingStage("b", executed),
            recordingStage("c", executed)
        ));

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext spyContext = spyValidContext(result);

        pipeline.generate(spyContext);

        assertEquals(List.of("a", "b", "c"), executed);
        assertEquals(0, result.blockCount);
    }

    @Test
    void invalidContextSkipsStagesAndReturnsEmptyResult() {
        AtomicInteger calls = new AtomicInteger();
        BuildingGenerationPipeline pipeline = new BuildingGenerationPipeline(List.of(
            new BuildingGenerationStage() {
                @Override
                public void generate(BuildingGenerationContext context) {
                    calls.incrementAndGet();
                }

                @Override
                public String name() {
                    return "spy";
                }
            }
        ));

        BuildingGenerationContext context = BuildingGenerationContext.create(
            null, null, stubCoordinateService(), stubProjectionService());
        assertFalse(context.isValid());

        BuildingGenerationResult result = pipeline.generate(context);
        assertEquals(0, calls.get());
        assertEquals(0, result.placementRecords.size());
        assertEquals(0, result.blockCount);
    }

    @Test
    void pipelineSetsBlockCountFromPlacementRecords() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = spyValidContext(result);

        BuildingGenerationPipeline pipeline = new BuildingGenerationPipeline(List.of(
            new BuildingGenerationStage() {
                @Override
                public void generate(BuildingGenerationContext ctx) {
                    BuildingBlockWriter.recordBlockWithPrevious(
                        ctx.getResult(),
                        new BlockPos(0, 64, 0),
                        "minecraft:air",
                        "minecraft:stone");
                    BuildingBlockWriter.recordBlockWithPrevious(
                        ctx.getResult(),
                        new BlockPos(1, 64, 0),
                        "minecraft:air",
                        "minecraft:stone");
                }

                @Override
                public String name() {
                    return "write-two";
                }
            }
        ));

        BuildingGenerationResult out = pipeline.generate(context);
        assertEquals(2, out.blockCount);
        assertEquals(2, out.placementRecords.size());
    }

    @Test
    void laterStageOverridesEarlierStageNewBlockId() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = spyValidContext(result);
        BlockPos pos = new BlockPos(5, 70, 5);

        BuildingGenerationPipeline pipeline = new BuildingGenerationPipeline(List.of(
            new BuildingGenerationStage() {
                @Override
                public void generate(BuildingGenerationContext ctx) {
                    BuildingBlockWriter.recordBlockWithPrevious(
                        ctx.getResult(), pos, "minecraft:grass_block", "minecraft:cobblestone");
                }

                @Override
                public String name() {
                    return "wall";
                }
            },
            new BuildingGenerationStage() {
                @Override
                public void generate(BuildingGenerationContext ctx) {
                    BuildingBlockWriter.recordBlockWithPrevious(
                        ctx.getResult(), pos, "minecraft:dirt", "minecraft:air");
                }

                @Override
                public String name() {
                    return "opening";
                }
            }
        ));

        BuildingGenerationResult out = pipeline.generate(context);
        assertEquals("minecraft:grass_block", out.placementRecords.get(pos).previousBlockId);
        assertEquals("minecraft:air", out.placementRecords.get(pos).newBlockId);
    }

    @Test
    void emptyStagesRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new BuildingGenerationPipeline(List.of()));
    }

    @Test
    void nullFootprintContextIsInvalid() {
        BuildingGenerationContext context = BuildingGenerationContext.create(
            null, null, stubCoordinateService(), stubProjectionService());
        assertFalse(context.isValid());
        assertTrue(context.getOuterPoints().isEmpty());
        assertTrue(context.getFootprintCells().isEmpty());
    }

    @Test
    void insufficientPointsContextIsInvalid() {
        BuildingFootprint footprint = new BuildingFootprint(
            List.of(new Vec2d(0, 0), new Vec2d(1, 0)), false);
        BuildingGenerationContext context = BuildingGenerationContext.create(
            footprint, null, stubCoordinateService(), stubProjectionService());
        assertFalse(context.isValid());
    }

    private static BuildingGenerationStage recordingStage(String name, List<String> executed) {
        return new BuildingGenerationStage() {
            @Override
            public void generate(BuildingGenerationContext context) {
                executed.add(name);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    private static BuildingGenerationContext spyValidContext(BuildingGenerationResult result) {
        return BuildingGenerationContext.forTesting(
            new BuildingFootprint(List.of(
                new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4), new Vec2d(0, 4)
            ), true),
            stubCoordinateService(),
            stubProjectionService(),
            result
        );
    }

    private static ICoordinateService stubCoordinateService() {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(-512, 512, -512, 512);
            }
        };
    }

    private static IBlockProjectionService stubProjectionService() {
        return new IBlockProjectionService() {
            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return false;
            }
        };
    }
}
