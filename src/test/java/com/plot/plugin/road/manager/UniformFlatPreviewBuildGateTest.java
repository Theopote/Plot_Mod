package com.plot.plugin.road.manager;

import com.plot.core.command.BlockRecord;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.NetworkElevationMode;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全网统一标高：预览 → 拖动 Y → 建造门禁 → 提交后失效 → 重建预览可建造。
 */
class UniformFlatPreviewBuildGateTest {

    private RoadNetworkManager networkManager;
    private RoadPreviewManager previewManager;
    private RoadProjectStatus status;

    @BeforeEach
    void setUp() throws Exception {
        PluginContext host = PluginContext.from(ApplicationContext.getInstance());
        status = new RoadProjectStatus();
        networkManager = new RoadNetworkManager(new RoadSystemConfig("uniform-gate"), status);
        previewManager = new RoadPreviewManager(status, host);
        previewManager.setPendingNetworkEditCheck(networkManager::hasPendingNetworkEdit);
        networkManager.setOnNetworkChanged(previewManager::handleNetworkChanged);
        networkManager.setNetworkElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        networkManager.setUniformElevationY(64);
        seedCurrentBuildPreview(previewManager, 64);
    }

    @Test
    void previewDragBuildGateFollowsUniformYLifecycle() throws Exception {
        assertEquals(64, networkManager.getNetwork().getUniformElevationY());
        assertTrue(previewManager.isBuildPreviewCurrent());
        assertTrue(previewManager.hasValidPreview());

        assertTrue(networkManager.updateUniformElevationYDuringDrag(75));
        assertTrue(networkManager.hasUniformElevationYDrag());
        assertEquals(75, networkManager.getNetwork().getUniformElevationY());

        assertFalse(previewManager.isBuildPreviewCurrent(), "pending drag must block build");
        assertFalse(
            previewManager.startNetworkPreview(networkManager.getNetwork()),
            "must not start formal preview during drag");
        assertFalse(previewManager.calculateProfileSamplingOnly(networkManager.getNetwork()));

        previewManager.buildRoadInWorld();
        assertTrue(
            status.get().contains("pending") || status.get().contains("build_blocked_pending"),
            "build entry must reject during drag: " + status.get());

        // 模拟拖动期间迟到的旧预览作业完成：不得复活为可建造。
        setField(previewManager, "previewJob",
            new RoadNetworkPreviewJob(networkManager.getNetwork(), false, null, previewManager));
        RoadNetworkPreviewJob lateJob =
            (RoadNetworkPreviewJob) getField(previewManager, "previewJob");
        previewManager.completePreviewJob(lateJob, seedPreviewResult(64), false);
        assertFalse(previewManager.isBuildPreviewCurrent());
        assertTrue(previewManager.needsPreviewRecalc());

        networkManager.commitUniformElevationYDrag();
        assertFalse(networkManager.hasPendingNetworkEdit());
        assertEquals(75, networkManager.getNetwork().getUniformElevationY());
        assertFalse(previewManager.isBuildPreviewCurrent(), "commit must leave old preview stale");
        assertTrue(previewManager.needsPreviewRecalc());

        seedCurrentBuildPreview(previewManager, 75);
        assertTrue(previewManager.isBuildPreviewCurrent());
        assertTrue(previewManager.hasValidPreview());
    }

    private static void seedCurrentBuildPreview(RoadPreviewManager previewManager, int pavementY)
            throws Exception {
        RoadGenerationResult aggregate = new RoadGenerationResult(40.0);
        BlockPos pos = new BlockPos(0, pavementY, 0);
        aggregate.placementRecords.put(
            pos, new BlockRecord(pos, "minecraft:air", "minecraft:black_concrete"));
        setField(previewManager, "lastGenerationResult", aggregate);
        setField(previewManager, "previewNeedsRecalc", false);
        setField(previewManager, "previewWorldKey", "");
        setField(previewManager, "previewNetwork", new RoadNetwork());
    }

    private static RoadNetworkGenerator.PreviewResult seedPreviewResult(int pavementY) {
        RoadGenerationResult aggregate = new RoadGenerationResult(40.0);
        BlockPos pos = new BlockPos(1, pavementY, 0);
        aggregate.placementRecords.put(
            pos, new BlockRecord(pos, "minecraft:air", "minecraft:black_concrete"));
        return new RoadNetworkGenerator.PreviewResult(
            new RoadNetwork(),
            aggregate,
            Map.of(),
            Map.of(),
            new RoadNetworkGenerator.NetworkGenerationResult(),
            Map.of());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
