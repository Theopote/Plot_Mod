package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.IGhostBlockService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoGradeSeparationRecommendationCacheTest {

    @Test
    void returnsCachedInstanceWhenContextUnchanged() {
        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        fixture.network().setNodeGradeSeparation(fixture.junction().getId(), true, null, 3.0);

        RoadSystemConfig config = new RoadSystemConfig("test");
        PluginContext host = testHost();
        AutoGradeSeparationRecommendationCache cache = new AutoGradeSeparationRecommendationCache();
        long configVersion = config.generationInputsFingerprint();
        long worldVersion = AutoGradeSeparationRecommendationCache.worldVersion(null, 0L);

        AutoGradeSeparationRecommendation first = cache.resolve(
            fixture.junction(),
            fixture.network(),
            config,
            host,
            1L,
            configVersion,
            worldVersion);
        AutoGradeSeparationRecommendation second = cache.resolve(
            fixture.junction(),
            fixture.network(),
            config,
            host,
            1L,
            configVersion,
            worldVersion);

        assertTrue(first.hasRecommendation());
        assertSame(first, second);
    }

    @Test
    void recomputesWhenNetworkRevisionChanges() {
        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        fixture.network().setNodeGradeSeparation(fixture.junction().getId(), true, null, 3.0);

        RoadSystemConfig config = new RoadSystemConfig("test");
        PluginContext host = testHost();
        AutoGradeSeparationRecommendationCache cache = new AutoGradeSeparationRecommendationCache();
        long configVersion = config.generationInputsFingerprint();
        long worldVersion = AutoGradeSeparationRecommendationCache.worldVersion(null, 0L);

        AutoGradeSeparationRecommendation first = cache.resolve(
            fixture.junction(), fixture.network(), config, host, 1L, configVersion, worldVersion);
        AutoGradeSeparationRecommendation second = cache.resolve(
            fixture.junction(), fixture.network(), config, host, 2L, configVersion, worldVersion);

        assertTrue(first.hasRecommendation());
        assertTrue(second.hasRecommendation());
        assertEquals(first.elevatedRoadId(), second.elevatedRoadId());
        assertNotSame(first, second);
    }

    @Test
    void skipsRecommendationWhenNotAutoMode() {
        SimpleCrossFixture fixture = SimpleCrossFixture.create();
        fixture.network().setNodeGradeSeparation(
            fixture.junction().getId(), true, fixture.roadA().getId(), 3.0);

        AutoGradeSeparationRecommendationCache cache = new AutoGradeSeparationRecommendationCache();
        AutoGradeSeparationRecommendation recommendation = cache.resolve(
            fixture.junction(),
            fixture.network(),
            new RoadSystemConfig("test"),
            testHost(),
            1L,
            1L,
            1L);

        assertFalse(recommendation.hasRecommendation());
    }

    @Test
    void configFingerprintChangesWhenGenerationInputsChange() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        long before = config.generationInputsFingerprint();
        config.setMaxSlope(15.0f);
        assertNotEquals(before, config.generationInputsFingerprint());
    }

    @Test
    void networkRevisionIncrementsOnHistoryPush() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadNetworkManager manager = new RoadNetworkManager(config, new RoadProjectStatus());
        long before = manager.getNetworkRevision();
        manager.pushHistory();
        assertEquals(before + 1, manager.getNetworkRevision());
    }

    @Test
    void previewTerrainRevisionIncrementsOnInvalidate() {
        PluginContext host = PluginContext.from(ApplicationContext.getInstance());
        RoadPreviewManager previewManager = new RoadPreviewManager(new RoadProjectStatus(), host);
        long before = previewManager.getTerrainRevision();
        previewManager.invalidatePreview();
        assertEquals(before + 1, previewManager.getTerrainRevision());
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

    private record SimpleCrossFixture(
            RoadNetwork network,
            RoadNode junction,
            Road roadA,
            Road roadB) {
        static SimpleCrossFixture create() {
            RoadNetwork network = new RoadNetwork();
            RoadNode junction = network.createNode(new Vec2d(0, 0));
            RoadNode north = network.createNode(new Vec2d(0, 12));
            RoadNode south = network.createNode(new Vec2d(0, -12));
            RoadNode east = network.createNode(new Vec2d(12, 0));
            RoadNode west = network.createNode(new Vec2d(-12, 0));
            Road roadA = network.createRoad("road-a");
            Road roadB = network.createRoad("road-b");
            network.createEdge(
                junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 12)), roadA.getId());
            network.createEdge(
                junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -12)), roadA.getId());
            network.createEdge(
                junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(12, 0)), roadB.getId());
            network.createEdge(
                junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-12, 0)), roadB.getId());
            return new SimpleCrossFixture(network, junction, roadA, roadB);
        }
    }
}
