package com.plot.plugin.building.generation.stage;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.FoundationElevationSource;
import com.plot.plugin.building.generation.resolve.GenerationSiteResolver;
import com.plot.plugin.building.generation.resolve.MassingGeometryResolver;
import com.plot.plugin.building.generation.resolve.MaterialResolver;
import com.plot.plugin.building.generation.resolve.ResolvedBuildingDefinition;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.plugin.building.site.BuildingSiteAnalysis;
import com.plot.plugin.building.site.BuildingSiteAnalyzer;
import com.plot.plugin.building.site.BuildingSiteColumnSample;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Foundation 水域填方与 SitePreparation 行为（无 Minecraft World 时验证 placement 计划）。
 */
class FoundationWaterAndSitePrepTest {

    @Test
    void foundationFillsFromLakeBottomToBaseThroughWaterColumn() {
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0), new Vec2d(2, 0), new Vec2d(2, 2), new Vec2d(0, 2)
        ), true);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        BuildingGenerationResult result = new BuildingGenerationResult();
        MassingGeometryResolver.ResolvedMassingGeometry massing =
            MassingGeometryResolver.resolve(definition, result);

        Map<Long, BuildingSiteColumnSample> columns = new HashMap<>();
        for (BuildingGenerationContext.GridCell cell : massing.footprintCells()) {
            BlockPos col = new BlockPos((int) Math.floor(cell.center().x), 0, (int) Math.floor(cell.center().y));
            columns.put(
                BuildingSiteAnalyzer.packColumn(col.getX(), col.getZ()),
                new BuildingSiteColumnSample(60, 64, OptionalInt.of(64), 0, 0));
        }

        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            List.copyOf(columns.values()),
            com.plot.plugin.building.site.TerrainElevationStrategy.BALANCED);
        GenerationSiteResolver.ResolvedSiteElevation site = new GenerationSiteResolver.ResolvedSiteElevation(
            null, null, 61, 65, FoundationElevationSource.TERRAIN, true);

        ResolvedBuildingDefinition resolved = new ResolvedBuildingDefinition(
            definition,
            massing,
            analysis,
            site,
            MaterialResolver.resolve(definition),
            columns);

        BuildingGenerationContext context = BuildingGenerationContext.fromResolved(
            footprint,
            definition,
            null,
            identityCoords(),
            stubProjection(),
            result,
            resolved);

        new FoundationGenerationStage().generate(context);

        assertTrue(result.fillVolume > 0);
        for (BuildingGenerationContext.GridCell cell : massing.footprintCells()) {
            BlockPos col = context.canvasToColumn(cell.center());
            for (int y = 61; y <= 65; y++) {
                BlockPos pos = new BlockPos(col.getX(), y, col.getZ());
                assertTrue(result.placementRecords.containsKey(pos), "missing fill at " + pos);
                assertEquals(
                    context.getFoundationFillBlockId(),
                    result.placementRecords.get(pos).newBlockId);
            }
        }
    }

    @Test
    void sitePreparationWithoutWorldIsNoOp() {
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0), new Vec2d(2, 0), new Vec2d(2, 2), new Vec2d(0, 2)
        ), true);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint, identityCoords(), stubProjection(), result);

        new SitePreparationStage().generate(context);
        assertEquals(0, result.placementRecords.size());
    }

    @Test
    void structureConflictWarningSurfacesFromAnalysis() {
        BuildingSiteColumnSample conflict = new BuildingSiteColumnSample(
            64, 66, OptionalInt.empty(), 0, 3);
        BuildingSiteAnalysis analysis = BuildingSiteAnalyzer.analyzeSamples(
            List.of(conflict, conflict, conflict, conflict),
            com.plot.plugin.building.site.TerrainElevationStrategy.BALANCED);
        assertTrue(analysis.structureConflictCount() > 0);

        BuildingGenerationResult result = new BuildingGenerationResult();
        GenerationSiteResolver.decide(
            null, null, analysis, List.of(64, 64, 64, 64), result);
        assertTrue(result.warnings.contains("plugin.building.warn.structure_conflict"));
        assertFalse(result.warnings.contains("plugin.building.warn.tree_clear_limit"));
    }

    @Test
    void emptyFallbackUsesDefaultElevation() {
        BuildingSiteAnalysis fallback = BuildingSiteAnalysis.emptyFallback(
            EngineeringTerrainService.DEFAULT_GROUND_ELEVATION);
        assertEquals(64, fallback.balancedGroundElevation());
        assertEquals(0, fallback.sampledColumnCount());
    }

    private static ICoordinateService identityCoords() {
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

    private static IBlockProjectionService stubProjection() {
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
