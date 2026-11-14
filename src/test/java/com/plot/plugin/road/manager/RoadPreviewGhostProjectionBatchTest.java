package com.plot.plugin.road.manager;

import com.plot.api.world.GhostBlockOwners;
import com.plot.api.world.IGhostBlockService;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPreviewGhostProjectionBatchTest {

    private RoadPreviewManager previewManager;
    private RecordingGhosts ghosts;

    @BeforeEach
    void setUp() {
        ghosts = new RecordingGhosts();
        ApplicationContext app = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            app.getAppState(),
            app.getCommandService(),
            app.getEventBus(),
            app.getToolManager(),
            null,
            ghosts,
            null,
            null);
        previewManager = new RoadPreviewManager(new RoadProjectStatus(), host);
    }

    @Test
    void ghostProjectionUploadsInBatches() throws Exception {
        RoadGenerationResult result = new RoadGenerationResult(1.0);
        int total = RoadPreviewManager.GHOST_BLOCKS_PER_TICK + 250;
        for (int i = 0; i < total; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            result.placementRecords.put(
                pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));
        }

        Method begin = RoadPreviewManager.class.getDeclaredMethod(
            "beginGhostProjection", RoadGenerationResult.class);
        begin.setAccessible(true);
        begin.invoke(previewManager, result);

        assertTrue(previewManager.hasPendingGhostProjection());
        assertEquals(total, previewManager.ghostProjectionTotal());
        assertEquals(0, previewManager.ghostProjectionProcessed());

        // 首帧只清旧 ghost，避免与大批量上传叠在同一帧。
        assertTrue(previewManager.tickGhostProjection());
        assertEquals(0, ghosts.addedCount);
        assertEquals(1, ghosts.clearCount);
        assertEquals(0, previewManager.ghostProjectionProcessed());

        assertTrue(previewManager.tickGhostProjection());
        assertEquals(RoadPreviewManager.GHOST_BLOCKS_PER_TICK, ghosts.addedCount);
        assertEquals(RoadPreviewManager.GHOST_BLOCKS_PER_TICK, previewManager.ghostProjectionProcessed());

        assertFalse(previewManager.tickGhostProjection());
        assertEquals(total, ghosts.addedCount);
        assertFalse(previewManager.hasPendingGhostProjection());
    }

    private static final class RecordingGhosts implements IGhostBlockService {
        private final Map<BlockPos, String> blocks = new HashMap<>();
        int clearCount;
        int addedCount;

        @Override
        public void clearAllGhostBlocks() {
            blocks.clear();
        }

        @Override
        public void clearGhostBlocks(String ownerId) {
            if (GhostBlockOwners.ROAD.equals(ownerId)) {
                clearCount++;
                blocks.clear();
            }
        }

        @Override
        public void addGhostBlock(String ownerId, BlockPos position, String blockType) {
            if (GhostBlockOwners.ROAD.equals(ownerId) && position != null && blockType != null) {
                blocks.put(position, blockType);
                addedCount++;
            }
        }

        @Override
        public int getVisibleGhostBlockCount() {
            return blocks.size();
        }
    }
}
