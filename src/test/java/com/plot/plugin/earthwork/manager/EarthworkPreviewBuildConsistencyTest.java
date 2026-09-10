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
import com.plot.core.command.commands.EarthworkGenerateCommand;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkQuickEdge;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.AIR;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.DIRT;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.STONE;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preview ≡ Build：落地必须写入与 Site 预览管线完全相同的 {@link BlockRecord} 集。
 */
class EarthworkPreviewBuildConsistencyTest {

    @Test
    void buildEnqueuesExactPreviewPlacementRecordsForVerticalPad() {
        EarthworkProject project = verticalPadProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        TerrainSnapshot terrain = rectangleTerrain(0, 3, 0, 3, 64);

        EarthworkGenerationResult preview = runSitePreviewPipeline(project.getActiveSite(), region, terrain);
        assertFalse(preview.placementRecords.isEmpty());

        List<BlockRecord> buildRecords = captureBuildEnqueue(preview, project, region.getId());
        assertPlacementRecordsEqual(preview.placementRecords, buildRecords);
        assertEquals(
            preview.volumeReport.totalChangedBlocks(),
            buildRecords.size(),
            "build enqueue count must match preview volume report");
    }

    @Test
    void buildEnqueuesExactPreviewPlacementRecordsForNaturalSlopePad() {
        EarthworkProject project = naturalSlopePadProject();
        GradingRegion region = project.getRegions().values().iterator().next();
        TerrainSnapshot terrain = rectangleTerrain(-6, 15, -6, 15, 60);

        EarthworkGenerationResult preview = runSitePreviewPipeline(project.getActiveSite(), region, terrain);
        assertTrue(preview.volumeReport.geometricFillVolume() > 400L,
            "slope preview should include band fill beyond 10x10 pad interior");
        assertTrue(hasPlacementOutsidePad(preview, 0, 9, 0, 9));

        List<BlockRecord> buildRecords = captureBuildEnqueue(preview, project, region.getId());
        assertPlacementRecordsEqual(preview.placementRecords, buildRecords);
    }

    @Test
    void buildAppliesPreviewRecordsToWorldWithoutMutation() {
        TerrainSnapshot terrain = rectangleTerrain(0, 3, 0, 3, 64);
        GradingRegion region = levelPadRegion(0, 3, 0, 3, 65, false);
        EarthworkProject project = new EarthworkProject();
        project.addRegion(region);

        EarthworkGenerationResult preview = runSitePreviewPipeline(project.getActiveSite(), region, terrain);
        List<BlockRecord> buildRecords = captureBuildEnqueue(preview, project, region.getId());
        assertPlacementRecordsEqual(preview.placementRecords, buildRecords);

        PreviewBlockWorld world = previewWorldFromTerrain(terrain);
        EarthworkGenerateCommand command = new EarthworkGenerateCommand(buildRecords, world);
        command.execute();

        for (BlockRecord record : buildRecords) {
            assertEquals(record.newBlockId, world.get(record.pos));
        }
        command.undo();
        for (BlockRecord record : buildRecords) {
            assertEquals(record.previousBlockId, world.get(record.pos));
        }
    }

    private static EarthworkGenerationResult runSitePreviewPipeline(
            EarthworkSite site,
            GradingRegion region,
            TerrainSnapshot terrain) {
        EarthworkPipelines.Bundle pipelines = EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE, solidColumnSampler(terrain, STONE));
        return pipelines.site().execute(EarthworkPipelineContext.of(
            site,
            null,
            terrain,
            region,
            BuildingFootprintLookup.NONE,
            RoadSurfaceLookup.NONE));
    }

    private static List<BlockRecord> captureBuildEnqueue(
            EarthworkGenerationResult preview,
            EarthworkProject project,
            String regionId) {
        List<BlockRecord> enqueued = new ArrayList<>();
        PluginContext host = capturingHost(enqueued);
        EarthworkPreviewManager previewManager = EarthworkPreviewManager.withGenerationResult(
            host,
            EarthworkPipelines.create(com.plot.test.world.IdentityCoordinateService.INSTANCE).site(),
            new TerrainSnapshotCache(),
            msg -> {},
            preview);
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            host, new TerrainSnapshotCache(), previewManager, msg -> {});

        buildManager.buildInWorld(project, regionId);
        return enqueued;
    }

    private static EarthworkProject verticalPadProject() {
        EarthworkProject project = new EarthworkProject();
        project.addRegion(levelPadRegion(0, 3, 0, 3, 65, false));
        return project;
    }

    private static EarthworkProject naturalSlopePadProject() {
        GradingRegion region = new GradingRegion("pad", rectangleOutline(0, 9, 0, 9));
        region.setSurfaceMode(GradingSurfaceMode.LEVEL_PAD);
        region.setAutoBalance(false);
        region.setManualTargetElevation(64);
        region.setPreviewGridSize(1);
        region.setFillMaterial(DIRT);

        EarthworkProject project = new EarthworkProject();
        project.addRegion(region);
        GradingZone pad = project.getActiveSite().getZone("pad");
        EarthworkQuickEdge.NATURAL.applyTo(pad.getEdgeSettings());
        pad.getEdgeSettings().setMaximumReachBlocks(6);
        pad.syncDesignSurfaceToRegion();
        project.getActiveSite().recomputeSiteBoundaryFromZones();
        return project;
    }

    private static PreviewBlockWorld previewWorldFromTerrain(TerrainSnapshot terrain) {
        PreviewBlockWorld world = new PreviewBlockWorld();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            for (int y = 1; y <= column.groundY(); y++) {
                world.seed(new BlockPos(column.worldX(), y, column.worldZ()), STONE);
            }
        }
        return world;
    }

    private static boolean hasPlacementOutsidePad(
            EarthworkGenerationResult preview,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        for (BlockPos pos : preview.placementRecords.keySet()) {
            if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) {
                return true;
            }
        }
        return false;
    }

    private static void assertPlacementRecordsEqual(
            Map<BlockPos, BlockRecord> expected,
            List<BlockRecord> actualRecords) {
        Map<BlockPos, BlockRecord> actual = new LinkedHashMap<>();
        for (BlockRecord record : actualRecords) {
            actual.put(record.pos, record);
        }
        assertEquals(expected.size(), actual.size(), "placement record count");
        for (Map.Entry<BlockPos, BlockRecord> entry : expected.entrySet()) {
            BlockRecord built = actual.get(entry.getKey());
            assertNotNull(built, () -> "build missing preview block at " + entry.getKey());
            assertEquals(entry.getValue().newBlockId, built.newBlockId);
        }
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

    private static final class PreviewBlockWorld implements EarthworkGenerateCommand.BlockWriter {
        private final Map<BlockPos, String> blocks = new LinkedHashMap<>();

        void seed(BlockPos pos, String blockId) {
            blocks.put(pos, blockId);
        }

        String get(BlockPos pos) {
            return blocks.getOrDefault(pos, AIR);
        }

        @Override
        public boolean setBlockAt(BlockPos pos, String blockId) {
            if (AIR.equals(blockId)) {
                blocks.remove(pos);
            } else {
                blocks.put(pos, blockId);
            }
            return true;
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
