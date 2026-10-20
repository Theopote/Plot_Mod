package com.plot.plugin.earthwork.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.IGhostBlockService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkQuickEdge;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.AIR;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.DIRT;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预览生命周期与施工身份校验。
 */
class EarthworkPreviewLifecycleTest {

    @Test
    void markDirtyRevokesBuildEligibilityButKeepsCachedResult() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        EarthworkGenerationResult preview = seededPreview(project, region);

        EarthworkPreviewManager manager = EarthworkPreviewManager.withGenerationResult(
            stubHost(),
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);

        assertTrue(manager.hasValidPreview());
        assertEquals(EarthworkPreviewState.VALID, manager.getPreviewState());

        manager.markDirty();

        assertEquals(EarthworkPreviewState.DIRTY, manager.getPreviewState());
        assertTrue(manager.hasCachedPreview());
        assertFalse(manager.hasValidPreview());
        assertNotNull(manager.getLastGenerationResult());
    }

    @Test
    void buildRejectsPreviewForDifferentRegion() {
        EarthworkProject project = twoRegionProject();
        GradingRegion regionA = project.getRegion("a");
        GradingRegion regionB = project.getRegion("b");
        EarthworkGenerationResult preview = seededPreview(project, regionA);

        List<BlockRecord> enqueued = new ArrayList<>();
        PluginContext host = capturingHost(enqueued);
        EarthworkPreviewManager manager = EarthworkPreviewManager.withGenerationResult(
            host,
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            host, new TerrainSnapshotCache(), manager, msg -> {});

        assertNotNull(buildManager.validateBuildEligibility(
            project, regionB.getId(), null, false, null));
        buildManager.buildInWorld(project, regionB.getId(), false);
        assertTrue(enqueued.isEmpty(), "mismatched region must not enqueue placements");
    }

    @Test
    void buildRejectsAfterDesignRevisionChange() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        EarthworkGenerationResult preview = seededPreview(project, region);

        EarthworkPreviewManager manager = EarthworkPreviewManager.withGenerationResult(
            stubHost(),
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            stubHost(), new TerrainSnapshotCache(), manager, msg -> {});

        assertNull(buildManager.validateBuildEligibility(
            project, region.getId(), null, false, null));

        region.setManualTargetElevation(70);
        assertNotNull(buildManager.validateBuildEligibility(
            project, region.getId(), null, false, null));
    }

    @Test
    void buildRejectsWhenPreviewMarkedDirty() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        EarthworkGenerationResult preview = seededPreview(project, region);

        List<BlockRecord> enqueued = new ArrayList<>();
        PluginContext host = capturingHost(enqueued);
        EarthworkPreviewManager manager = EarthworkPreviewManager.withGenerationResult(
            host,
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            host, new TerrainSnapshotCache(), manager, msg -> {});

        manager.markDirty();
        assertNotNull(buildManager.validateBuildEligibility(
            project, region.getId(), null, false, null));
        buildManager.buildInWorld(project, region.getId(), false);
        assertTrue(enqueued.isEmpty());
    }

    @Test
    void formalPathRejectsWhenWorldUnavailable() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        EarthworkGenerationResult preview = seededPreview(project, region);

        EarthworkPreviewManager manager = EarthworkPreviewManager.withGenerationResult(
            stubHost(),
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            stubHost(), new TerrainSnapshotCache(), manager, msg -> {});

        assertNotNull(buildManager.validateBuildEligibility(project, region.getId()));
        assertFalse(EarthworkPreviewIdentity.matchesCurrent(
            preview, project, region.getId(), null));
    }

    @Test
    void buildRejectsWhenTerrainChanged() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        EarthworkGenerationResult preview = seededPreview(project, region);

        EarthworkPreviewManager manager = EarthworkPreviewManager.withGenerationResult(
            stubHost(),
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            stubHost(), new TerrainSnapshotCache(), manager, msg -> {});

        TerrainSnapshot.ComparisonResult stale = new TerrainSnapshot.ComparisonResult(false, 4, 10);
        assertNotNull(buildManager.validateBuildEligibility(
            project, region.getId(), null, false, stale));
        assertNull(buildManager.validateBuildEligibility(
            project, region.getId(), null, false, new TerrainSnapshot.ComparisonResult(true, 0, 10)));
    }

    @Test
    void buildRejectsWhenAlreadyInFlight() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        EarthworkGenerationResult preview = seededPreview(project, region);

        EarthworkPreviewManager manager = EarthworkPreviewManager.withGenerationResult(
            stubHost(),
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            stubHost(), new TerrainSnapshotCache(), manager, msg -> {});

        buildManager.setBuildInFlightForTest(true);
        assertNotNull(buildManager.validateBuildEligibility(
            project, region.getId(), null, false, null));
    }

    @Test
    void calculatePreviewWithoutWorldFailsAndClearsResult() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        EarthworkGenerationResult preview = seededPreview(project, region);

        EarthworkPreviewManager manager = EarthworkPreviewManager.withGenerationResult(
            stubHost(),
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);

        assertTrue(manager.hasValidPreview());
        boolean ok = manager.calculatePreview(
            project, region, BuildingFootprintLookup.NONE, RoadSurfaceLookup.NONE);
        assertFalse(ok);
        assertEquals(EarthworkPreviewState.FAILED, manager.getPreviewState());
        assertFalse(manager.hasValidPreview());
        assertNull(manager.getLastGenerationResult());
    }

    @Test
    void designRevisionChangesWithEdgeSettings() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        long before = EarthworkPreviewIdentity.designRevision(project, region);

        GradingZone zone = project.getZone(region.getId());
        EarthworkQuickEdge.NATURAL.applyTo(zone.getEdgeSettings());
        long after = EarthworkPreviewIdentity.designRevision(project, region);
        assertTrue(before != after);
    }

    @Test
    void designRevisionChangesWithOutlineAndHoles() {
        EarthworkProject project = singleRegionProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        long before = EarthworkPreviewIdentity.designRevision(project, region);

        region.setOuterPoints(rectangleOutline(0, 5, 0, 5));
        assertTrue(before != EarthworkPreviewIdentity.designRevision(project, region));

        long mid = EarthworkPreviewIdentity.designRevision(project, region);
        region.setHoles(List.of(rectangleOutline(1, 2, 1, 2)));
        assertTrue(mid != EarthworkPreviewIdentity.designRevision(project, region));
    }

    @Test
    void normalizedOutlineFingerprintIgnoresRotationAndWinding() {
        List<Vec2d> base = rectangleOutline(0, 3, 0, 3);
        List<Vec2d> rotated = List.of(base.get(1), base.get(2), base.get(3), base.get(0));
        List<Vec2d> reversed = List.of(base.get(0), base.get(3), base.get(2), base.get(1));

        long a = EarthworkPreviewIdentity.normalizedOutlineFingerprint(base);
        long b = EarthworkPreviewIdentity.normalizedOutlineFingerprint(rotated);
        long c = EarthworkPreviewIdentity.normalizedOutlineFingerprint(reversed);
        assertEquals(a, b);
        assertEquals(a, c);
    }

    private static EarthworkGenerationResult seededPreview(EarthworkProject project, GradingRegion region) {
        EarthworkGenerationResult preview = new EarthworkGenerationResult();
        preview.placementRecords.put(
            new BlockPos(0, 65, 0),
            new BlockRecord(new BlockPos(0, 65, 0), STONE, DIRT));
        EarthworkPreviewIdentity.stamp(preview, project, region, null, null);
        return preview;
    }

    private static EarthworkProject singleRegionProject() {
        EarthworkProject project = new EarthworkProject();
        project.addRegion(levelPadRegion(0, 3, 0, 3, 65, false));
        return project;
    }

    private static EarthworkProject twoRegionProject() {
        EarthworkProject project = new EarthworkProject();
        GradingRegion a = new GradingRegion("a", rectangleOutline(0, 3, 0, 3));
        a.setSurfaceMode(GradingSurfaceMode.LEVEL_PAD);
        a.setAutoBalance(false);
        a.setManualTargetElevation(65);
        GradingRegion b = new GradingRegion("b", rectangleOutline(10, 13, 10, 13));
        b.setSurfaceMode(GradingSurfaceMode.LEVEL_PAD);
        b.setAutoBalance(false);
        b.setManualTargetElevation(65);
        project.addRegion(a);
        project.addRegion(b);
        return project;
    }

    private static PluginContext stubHost() {
        return capturingHost(new ArrayList<>());
    }

    private static PluginContext capturingHost(List<BlockRecord> enqueued) {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        ICoordinateServiceStub coordinates = new ICoordinateServiceStub();
        CapturingPlacementService placement = new CapturingPlacementService(enqueued);
        IBlockProjectionService projection = new IBlockProjectionService() {
            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(BlockPos pos) {
                return STONE;
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }
        };
        IGhostBlockService ghosts = new IGhostBlockService() {
            @Override
            public void clearAllGhostBlocks() {
            }

            @Override
            public void addGhostBlock(BlockPos position, String blockType) {
            }

            @Override
            public void addGhostBlock(Vec2d position, double height, String blockType) {
            }

            @Override
            public int getVisibleGhostBlockCount() {
                return 0;
            }
        };
        return new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            coordinates,
            ghosts,
            placement,
            projection);
    }

    private static final class CapturingPlacementService implements IBlockPlacementService {
        private final List<BlockRecord> enqueued;

        CapturingPlacementService(List<BlockRecord> enqueued) {
            this.enqueued = enqueued;
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public ProgressSnapshot getProgressSnapshot() {
            return new ProgressSnapshot(0, 0, 0, 0);
        }

        @Override
        public boolean cancelAll() {
            return false;
        }

        @Override
        public void enqueue(List<BlockWrite> writes, Consumer<ExecutionResult> onComplete) {
            for (BlockWrite write : writes) {
                enqueued.add(new BlockRecord(write.pos().toImmutable(), AIR, write.blockId()));
            }
            onComplete.accept(new ExecutionResult(writes.size(), 0, writes.size()));
        }
    }

    private static final class ICoordinateServiceStub implements com.plot.api.world.ICoordinateService {
        @Override
        public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
            return canvasPos;
        }

        @Override
        public WorldViewBounds getMinecraftWorldViewBounds() {
            return new WorldViewBounds(-512, 512, -512, 512);
        }
    }
}
