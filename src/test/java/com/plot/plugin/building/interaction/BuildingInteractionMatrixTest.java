package com.plot.plugin.building.interaction;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.FoundationElevationSource;
import com.plot.plugin.building.generation.resolve.GenerationSiteResolver;
import com.plot.plugin.building.generation.resolve.ResolvedBuildingDefinition;
import com.plot.plugin.building.golden.GoldenBuildingTestFixtures;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.OpeningSpec;
import com.plot.plugin.building.model.spec.WallFacadeSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature interaction 矩阵：组合场景比单测更容易暴露 Bug。
 * <p>
 * Case A–E 对应 Stabilization 阶段优先验证的交叉路径。
 */
class BuildingInteractionMatrixTest {

    private static final List<Vec2d> L_SHAPE = List.of(
        new Vec2d(0, 0),
        new Vec2d(14, 0),
        new Vec2d(14, 5),
        new Vec2d(5, 5),
        new Vec2d(5, 14),
        new Vec2d(0, 14)
    );

    private static final List<Vec2d> RECT = List.of(
        new Vec2d(0, 0),
        new Vec2d(12, 0),
        new Vec2d(12, 8),
        new Vec2d(0, 8)
    );

    private static final List<Vec2d> TOWER = List.of(
        new Vec2d(2, 2),
        new Vec2d(10, 2),
        new Vec2d(10, 6),
        new Vec2d(2, 6)
    );

    /** Case A: FloorPlate + L + Hip roof */
    @Test
    void caseA_floorPlateLShapeHipRoof() {
        BuildingFootprint fp = new BuildingFootprint(L_SHAPE, false);
        fp.setFloors(3);
        fp.setFloorHeight(3);
        fp.setFloorPlates(List.of(
            FloorPlateSpec.of(0, 1, L_SHAPE),
            FloorPlateSpec.of(2, 2, TOWER)
        ));
        fp.setRoofType(BuildingFootprint.RoofType.HIP);
        fp.setRoofPitchRatio(2);
        fp.setWindowSpacing(0);

        BuildingGenerationResult result = generate(fp);
        assertTrue(result.placementRecords.size() > 0);
        assertFalse(result.warnings.contains("plugin.building.warn.roof_downgrade"),
            "L+hip should remain eligible");
        assertEquals(BuildingFootprint.RoofType.HIP, result.effectiveRoofType);
    }

    /** Case B: FloorPlate topology change + custom facade + doors */
    @Test
    void caseB_topologyChangeFacadeAndDoors() {
        BuildingFootprint fp = new BuildingFootprint(L_SHAPE, false);
        fp.setFloors(4);
        fp.setFloorHeight(3);
        fp.setFloorPlates(List.of(
            FloorPlateSpec.of(0, 1, L_SHAPE),
            FloorPlateSpec.of(2, 3, RECT)
        ));
        fp.setWallFacades(List.of(
            WallFacadeSpec.of(0, new WindowPatternSpec(3, 1, 2, 1)),
            WallFacadeSpec.noWindows(1)
        ));
        fp.setOpenings(List.of(OpeningSpec.door(0, 0.5, 0, 2, 2)));
        fp.setRoofType(BuildingFootprint.RoofType.FLAT);

        BuildingGenerationResult result = generate(fp);
        BuildingDefinition def = BuildingDefinitionMapper.fromFootprint(fp);
        assertEquals(RECT.size(), def.massing().plateForFloor(3).outerPoints().size());
        assertEquals(L_SHAPE.size(), def.massing().plateForFloor(0).outerPoints().size());
        assertTrue(countAir(result) > 0, "door must carve openings");
        assertTrue(countWallLike(result, fp) > 0);
    }

    /** Case C: manual elevation overrides pad; ownership fields stay distinct */
    @Test
    void caseC_manualElevationOverridesPadOwnership() {
        BuildingFootprint fp = new BuildingFootprint(RECT, true);
        fp.setFloors(1);
        fp.setManualBaseElevation(80);

        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(fp);
        BuildingGenerationResult warnings = new BuildingGenerationResult();
        ResolvedBuildingDefinition resolved =
            com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory
                .resolveForTesting(definition, warnings);

        GenerationSiteResolver.ResolvedSiteElevation site = resolved.site();
        assertEquals(80, site.requestedBaseElevation());
        assertEquals(null, site.resolvedPadElevation());
        assertEquals(80, site.actualFoundationElevation());
        assertEquals(FoundationElevationSource.MANUAL, site.source());
        assertFalse(warnings.warnings.contains("plugin.building.warn.using_earthwork_pad_elevation"));

        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            fp, GoldenBuildingTestFixtures.coordinates(), GoldenBuildingTestFixtures.projection(),
            new BuildingGenerationResult());
        assertEquals(80, context.getActualFoundationElevation());
    }

    /** Case D: Balcony + window openings */
    @Test
    void caseD_balconyAndWindowOpenings() {
        BuildingFootprint fp = new BuildingFootprint(RECT, true);
        fp.setFloors(2);
        fp.setFloorHeight(3);
        fp.setWindowSpacing(3);
        fp.setWindowWidth(1);
        fp.setWindowHeight(2);
        fp.setWindowSillHeight(1);
        fp.addBalcony(new BuildingFootprint.Balcony(0, 0.5, 1, 3, 2, null, null));
        fp.setRoofType(BuildingFootprint.RoofType.FLAT);

        BuildingGenerationResult result = generate(fp);
        assertTrue(result.placementRecords.size() > 0);
        assertTrue(countAir(result) > 0, "pattern windows should carve air");
    }

    /** Case E: Narrow footprint（inner offset 失败）+ thick-wall 意图 + opening */
    @Test
    void caseE_thickWallNarrowFootprintOpening() {
        // depth=2 时 thickness=1 可靠触发 inner offset 失败；thickness=3 当前 offset
        // 仍可能吐出 4 点伪内轮廓（geometry 风险另案），故用已锁定的 B07 尺度。
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 2),
            new Vec2d(0, 2)
        ), false);
        fp.setFloors(1);
        fp.setFloorHeight(3);
        fp.setWallThickness(1);
        fp.setWindowSpacing(0);
        fp.setOpenings(List.of(OpeningSpec.door(0, 0.5, 0, 2, 2)));
        fp.setRoofType(BuildingFootprint.RoofType.GABLE);
        fp.setRoofPitchRatio(4);

        BuildingGenerationResult result = generate(fp);
        assertTrue(result.warnings.contains("plugin.building.warn.inner_offset_failed"));
        assertTrue(countWallLike(result, fp) > 0, "solid wall mass required");
        assertTrue(countAir(result) > 0, "opening still carves");
        assertEquals(BuildingFootprint.RoofType.FLAT, result.effectiveRoofType);
    }

    private static BuildingGenerationResult generate(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
        BuildingGenerationPipeline.createDefault().generate(context);
        return result;
    }

    private static int countAir(BuildingGenerationResult result) {
        int n = 0;
        for (var record : result.placementRecords.values()) {
            String id = record.newBlockId;
            if (id != null && id.contains("air")) {
                n++;
            }
        }
        return n;
    }

    private static int countWallLike(BuildingGenerationResult result, BuildingFootprint footprint) {
        String wall = footprint.getWallMaterial().getPrimaryMaterial();
        int n = 0;
        for (var record : result.placementRecords.values()) {
            if (wall.equals(record.newBlockId)) {
                n++;
            }
        }
        return n;
    }
}
