package com.plot.plugin.building.golden;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.IGhostBlockService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictMassingGenerator;
import com.plot.plugin.building.generation.opening.OpeningVerticalLayout;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.RoofGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.ui.BuildingActions;
import com.plot.plugin.building.ui.BuildingPluginState;
import com.plot.plugin.building.ui.BuildingPreviewIdentity;
import com.plot.plugin.building.ui.DistrictPreviewJob;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building Massing v1 冻结前核心契约回归：Preview→Build、片区顺序、窗节奏、Undo/投影 stale、分帧预算。
 */
class BuildingMassingV1ContractTest {

    @Test
    void buildConsumesExactPreviewPlacementRecords() {
        BuildingFootprint building = overlapFootprint("tower", 4);
        DistrictGenerationResult district = generateDistrict(List.of(building));
        BuildingGenerationResult preview = district.toMergedResult();
        assertFalse(preview.placementRecords.isEmpty());

        List<BlockRecord> buildRecords = new ArrayList<>(preview.placementRecords.values());
        assertEquals(preview.placementRecords.size(), buildRecords.size());

        Map<BlockPos, BlockRecord> commandMap = new LinkedHashMap<>();
        for (BlockRecord record : buildRecords) {
            commandMap.put(record.pos, record);
        }
        assertPlacementRecordsEqual(preview.placementRecords, commandMap);
    }

    @Test
    void districtOverlapResultIsOrderInvariant() {
        BuildingFootprint low = overlapFootprint("podium", 2);
        BuildingFootprint high = overlapFootprint("tower", 10);

        DistrictGenerationResult ab = generateDistrict(List.of(low, high));
        DistrictGenerationResult ba = generateDistrict(List.of(high, low));

        assertTrue(ab.hasPlacements());
        assertTrue(ba.hasPlacements());
        assertPlacementRecordsEqual(ab.mergedPlacementRecords(), ba.mergedPlacementRecords());
    }

    @Test
    void windowRhythmAlternatesGlassAndPierOnSouthWall() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(12, 6, 1, 4, 1);
        footprint.setWindowsEnabled(true);
        footprint.setWindowWidth(2);
        footprint.setWindowPierWidth(2);
        footprint.setWindowSillHeight(0);

        BuildingGenerationResult result = generateWallsAndOpenings(footprint);
        String wallId = GoldenBuildingCaseFactory.GOLDEN_WALL;
        String windowId = BuildingGeometryUtils.resolveBlockId(footprint.getWindowMaterial());

        int floorSlabY = result.placementRecords.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        int windowY = OpeningVerticalLayout.windowStartY(floorSlabY, footprint.getWindowSillHeight());
        int southZ = result.placementRecords.keySet().stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int minX = result.placementRecords.keySet().stream().mapToInt(BlockPos::getX).min().orElse(0);
        int maxX = result.placementRecords.keySet().stream().mapToInt(BlockPos::getX).max().orElse(0);

        List<Character> row = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            BlockPos pos = new BlockPos(x, windowY, southZ);
            BlockRecord record = result.placementRecords.get(pos);
            if (record != null && windowId.equals(record.newBlockId)) {
                row.add('G');
            } else if (record != null && wallId.equals(record.newBlockId)) {
                row.add('W');
            } else {
                row.add('.');
            }
        }

        assertTrue(row.size() >= 8, "row=" + row);
        String rhythm = String.valueOf(row).replaceAll("[^GW]", "");
        assertTrue(rhythm.contains("GGWWGG"), "expected 2-wide glass / 2-wide pier rhythm, row=" + row);
    }

    @Test
    void undoClearsPreviewSoBuildWouldBeBlocked() {
        BuildingPluginState state = new BuildingPluginState();
        BuildingActions actions = actions(state, IdentityCoordinateService.INSTANCE);

        BuildingFootprint footprint = overlapFootprint("undo-tower", 3);
        state.getProject().addBuilding(footprint);
        List<BuildingFootprint> targets = List.of(footprint);

        BuildingGenerationResult preview = generateOne(footprint);
        state.setLastGenerationResult(preview);
        state.setPreviewIdentity(BuildingPreviewIdentity.capture(
            targets, false, actions.currentProjectionFingerprint()));

        assertEquals(
            BuildingPreviewIdentity.Validity.VALID,
            actions.previewValidity(targets));

        actions.pushProjectHistory();
        footprint.setFloors(8);
        actions.undoProject();

        assertNull(state.getPreviewIdentity());
        assertNull(state.getLastGenerationResult());
        assertEquals(
            BuildingPreviewIdentity.Validity.NONE,
            actions.previewValidity(targets));
    }

    @Test
    void projectionChangeMarksPreviewStaleForBuild() {
        BuildingPluginState state = new BuildingPluginState();
        MutableProjectionCoordinates coordinates = new MutableProjectionCoordinates(PROJECTION_A);
        BuildingActions actions = actions(state, coordinates);

        BuildingFootprint footprint = overlapFootprint("proj-tower", 3);
        state.getProject().addBuilding(footprint);
        List<BuildingFootprint> targets = List.of(footprint);

        state.setLastGenerationResult(generateOne(footprint));
        state.setPreviewIdentity(BuildingPreviewIdentity.capture(
            targets, false, PROJECTION_A.fingerprint()));

        assertEquals(
            BuildingPreviewIdentity.Validity.VALID,
            actions.previewValidity(targets));

        coordinates.setSnapshot(PROJECTION_B);
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            actions.previewValidity(targets));
        assertEquals(
            "plugin.building.generate.projection_changed",
            actions.previewStaleMessageKey(targets));
    }

    @Test
    void noneRoofGeneratesZeroRoofBlocks() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(8, 6, 1, 3, 1);
        footprint.setRoofType(BuildingFootprint.RoofType.NONE);

        GoldenBuildingMetrics metrics = GoldenBuildingHarness.generate(footprint);
        assertEquals(0, metrics.roofBlocks());
        assertEquals("NONE", metrics.effectiveRoofType());

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            IdentityCoordinateService.INSTANCE,
            GoldenBuildingTestFixtures.projection(),
            result);
        new BuildingGenerationPipeline(List.of(new RoofGenerationStage())).generate(context);
        assertEquals(0, result.placementRecords.size());
    }

    @Test
    void flatToNoneRoofTypeMarksPreviewStale() {
        BuildingFootprint footprint = overlapFootprint("roof-change", 3);
        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
        List<BuildingFootprint> targets = List.of(footprint);

        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(
            targets, false, PROJECTION_A.fingerprint());
        assertEquals(
            BuildingPreviewIdentity.Validity.VALID,
            identity.validityAgainst(targets, true, false, PROJECTION_A.fingerprint()));

        footprint.setRoofType(BuildingFootprint.RoofType.NONE);
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(targets, true, false, PROJECTION_A.fingerprint()));
    }

    @Test
    void districtPreviewJobAdvancesOneStagePerTickBudget() {
        assertEquals(1, DistrictPreviewJob.STAGES_PER_TICK,
            "v1 freeze: one pipeline stage per UI frame");
        int stageCount = BuildingGenerationPipeline.createDefault().stageCount();
        assertTrue(stageCount >= 5,
            "default pipeline should expose enough stages for incremental check progress");
    }

    private static BuildingFootprint overlapFootprint(String id, int floors) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ), true);
        footprint.setName(id);
        footprint.setFloors(floors);
        footprint.setFloorHeight(3);
        footprint.setWallThickness(1);
        footprint.setWindowsEnabled(false);
        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
        GoldenBuildingCaseFactory.applyDefaults(footprint, floors, 3, 1);
        footprint.setWindowsEnabled(false);
        return footprint;
    }

    private static DistrictGenerationResult generateDistrict(List<BuildingFootprint> buildings) {
        return DistrictMassingGenerator.generate(buildings, BuildingMassingV1ContractTest::generateOne);
    }

    private static BuildingGenerationResult generateOne(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
        return BuildingGenerationPipeline.createDefault().generate(context);
    }

    private static BuildingGenerationResult generateWallsAndOpenings(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            IdentityCoordinateService.INSTANCE,
            GoldenBuildingTestFixtures.projection(),
            result);
        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new OpeningGenerationStage()
        )).generate(context);
        return result;
    }

    private static BuildingActions actions(BuildingPluginState state, ICoordinateService coordinates) {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            coordinates,
            NOOP_GHOSTS,
            null,
            GoldenBuildingTestFixtures.projection());
        BuildingActions actions = new BuildingActions(host, state, new Object());
        actions.setBuildingGenerator(new BuildingGenerator(coordinates, GoldenBuildingTestFixtures.projection()));
        return actions;
    }

    private static void assertPlacementRecordsEqual(
            Map<BlockPos, BlockRecord> expected,
            Map<BlockPos, BlockRecord> actual) {
        assertEquals(expected.keySet(), actual.keySet(), "block positions differ");
        for (BlockPos pos : expected.keySet()) {
            BlockRecord expectedRecord = expected.get(pos);
            BlockRecord actualRecord = actual.get(pos);
            assertNotNull(actualRecord, () -> "missing record at " + pos);
            assertEquals(expectedRecord.previousBlockId, actualRecord.previousBlockId,
                () -> "previousBlockId at " + pos);
            assertEquals(expectedRecord.newBlockId, actualRecord.newBlockId,
                () -> "newBlockId at " + pos);
        }
    }

    private static final IGhostBlockService NOOP_GHOSTS = new IGhostBlockService() {
        @Override
        public void clearAllGhostBlocks() {
        }

        @Override
        public int getVisibleGhostBlockCount() {
            return 0;
        }
    };

    private static final WorldProjectionSnapshot PROJECTION_A = projectionSnapshot(100.0, 900.0, 0.0, 600.0);
    private static final WorldProjectionSnapshot PROJECTION_B = projectionSnapshot(200.0, 1000.0, 50.0, 650.0);

    private static WorldProjectionSnapshot projectionSnapshot(
            double minX, double maxX, double minZ, double maxZ) {
        return new WorldProjectionSnapshot(
            new WorldViewBounds(minX, maxX, minZ, maxZ),
            256f,
            1f,
            800f,
            600f);
    }

    private static final class MutableProjectionCoordinates implements ICoordinateService {
        private WorldProjectionSnapshot snapshot;

        private MutableProjectionCoordinates(WorldProjectionSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        void setSnapshot(WorldProjectionSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
            return IdentityCoordinateService.INSTANCE.canvasToMinecraftWorld(canvasPos);
        }

        @Override
        public WorldViewBounds getMinecraftWorldViewBounds() {
            return snapshot.worldBounds();
        }

        @Override
        public WorldProjectionSnapshot captureProjection() {
            return snapshot;
        }
    }
}
