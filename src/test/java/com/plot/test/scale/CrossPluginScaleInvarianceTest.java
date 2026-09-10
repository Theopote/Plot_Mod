package com.plot.test.scale;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.WorldProjectionMath;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.resolve.MassingGeometryResolver;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;
import com.plot.plugin.earthwork.geometry.ZoneBoundarySlopeApplicator;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkSiteBoundaryUtils;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineHost;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.plot.test.scale.ScaleInvarianceProjections.FAR;
import static com.plot.test.scale.ScaleInvarianceProjections.FAR_BLOCKS_PER_CANVAS_UNIT;
import static com.plot.test.scale.ScaleInvarianceProjections.NEAR;
import static com.plot.test.scale.ScaleInvarianceProjections.canvasEastOf;
import static com.plot.test.scale.ScaleInvarianceProjections.canvasUnitsPerWorldBlock;
import static com.plot.test.scale.ScaleInvarianceProjections.worldBlocksPerCanvasUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 跨插件尺度不变性：同一 block 参数在近景/远景投影下应产生等价的世界方块几何。
 */
@DisplayName("Cross-plugin scale invariance")
class CrossPluginScaleInvarianceTest {

    private static final List<Vec2d> BUILDING_SQUARE = List.of(
        new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 20), new Vec2d(0, 20));

    private static final List<Vec2d> EARTHWORK_PAD = List.of(
        new Vec2d(4, 4), new Vec2d(8, 4), new Vec2d(8, 8), new Vec2d(4, 8));

    @Nested
    @DisplayName("Core projection math")
    class CoreProjectionMath {

        @Test
        void uniformScaleFactorsMatchExpectation() {
            assertEquals(1.0, worldBlocksPerCanvasUnit(NEAR), 1e-6);
            assertEquals(FAR_BLOCKS_PER_CANVAS_UNIT, worldBlocksPerCanvasUnit(FAR), 1e-6);
        }

        @Test
        void canvasScaleHelpersAgreeWithWorldProjectionMath() {
            Vec2d origin = new Vec2d(10, 10);
            Vec2d direction = new Vec2d(0, 1);
            for (ICoordinateService coordinates : List.of(NEAR, FAR)) {
                double core = canvasUnitsPerWorldBlock(coordinates, origin, direction);
                double building = BuildingCanvasScale.capture(coordinates, List.of(origin))
                    .blocksToCanvas(1.0, origin, direction);
                double earthwork = EarthworkCanvasScale.capture(coordinates, List.of(origin))
                    .blocksToCanvas(1.0, origin, direction);
                assertEquals(core, building, 1e-6);
                assertEquals(core, earthwork, 1e-6);
            }
        }
    }

    @Nested
    @DisplayName("PowerLine")
    class PowerLineScaleInvariance {

        @Test
        void poleSpacingPreservesWorldBlockSpan() {
            List<Vec2d> path = List.of(new Vec2d(0, 0), new Vec2d(100, 0));
            double spacingBlocks = 20.0;

            List<PowerPoleSite> nearView = PowerPoleLayoutUtils.computePoleSites(path, 5.0, spacingBlocks, NEAR);
            List<PowerPoleSite> farView = PowerPoleLayoutUtils.computePoleSites(path, 5.0, spacingBlocks, FAR);

            assertTypicalSpan(nearView, spacingBlocks);
            assertTypicalSpan(farView, spacingBlocks);
        }
    }

    @Nested
    @DisplayName("Building")
    class BuildingScaleInvariance {

        @Test
        void wallThicknessIsInvariantInWorldBlocks() {
            BuildingFootprint footprint = new BuildingFootprint(BUILDING_SQUARE, true);
            footprint.setWallThickness(2);
            BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
            BuildingGenerationResult result = new BuildingGenerationResult();

            var nearMassing = MassingGeometryResolver.resolve(
                definition, result, BuildingCanvasScale.capture(NEAR, BUILDING_SQUARE));
            var farMassing = MassingGeometryResolver.resolve(
                definition, result, BuildingCanvasScale.capture(FAR, BUILDING_SQUARE));

            assertEquals(
                minWorldX(nearMassing.innerPoints(), NEAR),
                minWorldX(farMassing.innerPoints(), FAR),
                0.5);
        }

        @Test
        void wallThicknessIsInvariantInPlacedVoxels() {
            BuildingFootprint footprint = new BuildingFootprint(BUILDING_SQUARE, true);
            footprint.setWallThickness(2);
            footprint.setFloors(1);
            footprint.setFloorHeight(3);

            double nearThickness = southWallThickness(generateWallVoxels(NEAR));
            double farThickness = southWallThickness(generateWallVoxels(FAR));

            assertEquals(2.0, nearThickness, 0.51);
            assertEquals(2.0, farThickness, 0.51);
            assertEquals(nearThickness, farThickness, 0.51);
        }
    }

    @Nested
    @DisplayName("Earthwork")
    class EarthworkScaleInvariance {

        @Test
        void captureBoundaryMarginIsInvariantInWorldBlocks() {
            EarthworkSite site = new EarthworkSite("site");
            GradingZone pad = new GradingZone("pad", EARTHWORK_PAD);
            ZoneEdgeSettings edge = pad.getEdgeSettings();
            edge.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
            edge.setMaximumReachBlocks(6);
            site.addZone(pad);

            EarthworkCanvasScale nearScale = EarthworkCanvasScale.capture(NEAR, EARTHWORK_PAD);
            EarthworkCanvasScale farScale = EarthworkCanvasScale.capture(FAR, EARTHWORK_PAD);

            double nearMargin = worldMarginAlongX(
                EARTHWORK_PAD,
                EarthworkSiteBoundaryUtils.resolveCaptureBoundary(site, nearScale),
                NEAR);
            double farMargin = worldMarginAlongX(
                EARTHWORK_PAD,
                EarthworkSiteBoundaryUtils.resolveCaptureBoundary(site, farScale),
                FAR);

            assertEquals(6.0, nearMargin, 0.5);
            assertEquals(6.0, farMargin, 0.5);
        }

        @Test
        void exteriorSlopeTargetMatchesAtEquivalentWorldDistance() {
            ZoneEdgeSettings settings = new ZoneEdgeSettings();
            settings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
            settings.setCutSlopePitchRatio(1);
            settings.setMaximumReachBlocks(4);

            EarthworkCanvasScale nearScale = EarthworkCanvasScale.capture(NEAR, EARTHWORK_PAD);
            EarthworkCanvasScale farScale = EarthworkCanvasScale.capture(FAR, EARTHWORK_PAD);

            int nearTarget = ZoneBoundarySlopeApplicator.resolveLegacyTargetY(
                canvasEastOf(new Vec2d(4, 5), -0.5, NEAR), 70, 64, EARTHWORK_PAD, settings, nearScale);
            int farTarget = ZoneBoundarySlopeApplicator.resolveLegacyTargetY(
                canvasEastOf(new Vec2d(4, 5), -0.5, FAR), 70, 64, EARTHWORK_PAD, settings, farScale);

            assertEquals(nearTarget, farTarget);
        }
    }

    @Nested
    @DisplayName("Road")
    class RoadScaleInvariance {

        @Test
        void crossSectionUnitsPerBlockMatchesCoreProjectionMath() {
            RoadGenerationPipelineHost nearHost = host(NEAR);
            RoadGenerationPipelineHost farHost = host(FAR);
            List<Vec2d> path = List.of(new Vec2d(0, 0), new Vec2d(100, 0));
            Vec2d origin = path.getFirst();
            Vec2d normal = new Vec2d(0, 1);

            assertEquals(
                WorldProjectionMath.canvasUnitsPerWorldBlock(NEAR, origin, normal),
                nearHost.estimateCanvasUnitsPerBlock(path, List.of()),
                1e-6);
            assertEquals(
                WorldProjectionMath.canvasUnitsPerWorldBlock(FAR, origin, normal),
                farHost.estimateCanvasUnitsPerBlock(path, List.of()),
                1e-6);
            assertEquals(1.0 / FAR_BLOCKS_PER_CANVAS_UNIT,
                farHost.estimateCanvasUnitsPerBlock(path, List.of()),
                1e-6);
        }
    }

    private static RoadGenerationPipelineHost host(ICoordinateService coordinates) {
        return new RoadGenerationPipelineHost(
            new RoadSystemConfig("scale-invariance"),
            coordinates,
            noopProjection());
    }

    private static IBlockProjectionService noopProjection() {
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

    private static void assertTypicalSpan(List<PowerPoleSite> sites, double expectedBlocks) {
        for (int i = 0; i < sites.size() - 1; i++) {
            double span = PowerPoleLayoutUtils.worldSpanBlocks(sites.get(i), sites.get(i + 1));
            assertEquals(expectedBlocks, span, 1.0);
        }
    }

    private static double minWorldX(List<Vec2d> points, ICoordinateService coordinates) {
        return points.stream()
            .mapToDouble(p -> coordinates.canvasToMinecraftWorld(p).x)
            .min()
            .orElse(0.0);
    }

    private static BuildingGenerationResult generateWallVoxels(ICoordinateService coordinates) {
        BuildingFootprint footprint = new BuildingFootprint(BUILDING_SQUARE, true);
        footprint.setWallThickness(2);
        footprint.setFloors(1);
        footprint.setFloorHeight(3);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, coordinates, noopProjection(), result);
        new BuildingGenerationPipeline(List.of(new WallGenerationStage())).generate(context);
        return result;
    }

    /** 南缘（min Z）沿 +Z 向内连续墙列数 = 世界方块墙厚。 */
    private static double southWallThickness(BuildingGenerationResult result) {
        Set<BlockPos> walls = result.placementRecords.keySet();
        if (walls.isEmpty()) {
            return 0.0;
        }
        int baseY = walls.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int southZ = walls.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        List<Integer> southXs = walls.stream()
            .filter(pos -> pos.getY() == baseY && pos.getZ() == southZ)
            .map(BlockPos::getX)
            .sorted()
            .toList();
        if (southXs.isEmpty()) {
            return 0.0;
        }
        int midX = southXs.get(southXs.size() / 2);
        int thickness = 0;
        for (int z = southZ; walls.contains(new BlockPos(midX, baseY, z)); z++) {
            thickness++;
        }
        return thickness;
    }

    private static double worldMarginAlongX(
            List<Vec2d> inner,
            List<Vec2d> outer,
            ICoordinateService coordinates) {
        double innerMinX = Double.POSITIVE_INFINITY;
        double outerMinX = Double.POSITIVE_INFINITY;
        for (Vec2d point : inner) {
            innerMinX = Math.min(innerMinX, coordinates.canvasToMinecraftWorld(point).x);
        }
        for (Vec2d point : outer) {
            outerMinX = Math.min(outerMinX, coordinates.canvasToMinecraftWorld(point).x);
        }
        return innerMinX - outerMinX;
    }
}
