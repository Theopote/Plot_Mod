package com.plot.plugin.earthwork.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.IGhostBlockService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkManagerTest {

    @Test
    void calculatePreviewWithoutWorldReturnsFalse() {
        List<String> status = new ArrayList<>();
        EarthworkPreviewManager previewManager = newPreviewManager(status);
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = levelPadRegion(0, 3, 0, 3, 65, false);
        project.addRegion(region);

        boolean ok = previewManager.calculatePreview(
            project, region, BuildingFootprintLookup.NONE, RoadSurfaceLookup.NONE);

        assertFalse(ok);
        assertFalse(previewManager.hasValidPreview());
        assertNull(previewManager.getLastGenerationResult());
        assertFalse(status.isEmpty());
    }

    @Test
    void invalidatePreviewClearsCachedResultAndRegionReports() {
        List<String> status = new ArrayList<>();
        EarthworkPreviewManager previewManager = newPreviewManager(status);
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = levelPadRegion(0, 3, 0, 3, 65, false);
        region.setLastVolumeReport(new EarthworkVolumeReport(3, 4, 1, 2, 0, 0, 5, 2, 7));
        project.addRegion(region);
        project.getActiveSite().setLastReport(new EarthworkVolumeReport(1, 1, 0, 0, 0, 0, 1, 0, 1));

        previewManager.invalidatePreview(project);

        assertNull(previewManager.getLastGenerationResult());
        assertEquals(EarthworkVolumeReport.empty(), region.getLastVolumeReport());
        assertEquals(EarthworkVolumeReport.empty(), project.getActiveSite().getLastReport());
    }

    @Test
    void buildWithoutPreviewReportsStatus() {
        List<String> status = new ArrayList<>();
        TerrainSnapshotCache cache = new TerrainSnapshotCache();
        EarthworkPreviewManager previewManager = newPreviewManager(status);
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            testHost(), cache, previewManager, status::add);

        buildManager.buildInWorld(new EarthworkProject(), "region-1");

        assertFalse(status.isEmpty());
        assertFalse(previewManager.hasValidPreview());
    }

    private static EarthworkPreviewManager newPreviewManager(List<String> status) {
        return new EarthworkPreviewManager(
            testHost(), EarthworkPipelines.create(null), new TerrainSnapshotCache(), status::add);
    }

    private static PluginContext testHost() {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        ICoordinateService coordinates = new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(-512, 512, -512, 512);
            }
        };
        IBlockProjectionService projection = new IBlockProjectionService() {
            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:stone";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return false;
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
        IBlockPlacementService placement = new IBlockPlacementService() {
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
            public void enqueue(List<BlockWrite> writes, java.util.function.Consumer<ExecutionResult> onComplete) {
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
}
