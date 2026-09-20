package com.plot.plugin.powerline.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.GhostBlockOwners;
import com.plot.api.world.IGhostBlockService;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLinePluginState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePreviewManagerTest {
    private final List<Map<BlockPos, String>> ghostBatches = new ArrayList<>();
    private final AtomicInteger clearOwnerCount = new AtomicInteger();
    private PowerLinePluginState state;
    private PowerLinePreviewManager manager;

    @BeforeEach
    void setUp() {
        ghostBatches.clear();
        clearOwnerCount.set(0);
        state = new PowerLinePluginState();
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        IGhostBlockService ghosts = new IGhostBlockService() {
            @Override
            public void clearAllGhostBlocks() {
            }

            @Override
            public void clearGhostBlocks(String ownerId) {
                if (GhostBlockOwners.POWER_LINE.equals(ownerId)) {
                    clearOwnerCount.incrementAndGet();
                }
            }

            @Override
            public void replaceGhostBlocks(String ownerId, Map<BlockPos, String> blocks) {
                if (GhostBlockOwners.POWER_LINE.equals(ownerId)) {
                    ghostBatches.add(new LinkedHashMap<>(blocks));
                }
            }

            @Override
            public void addGhostBlock(String ownerId, BlockPos position, String blockType) {
            }

            @Override
            public void addGhostBlock(String ownerId, Vec2d position, double height, String blockType) {
            }

            @Override
            public int getVisibleGhostBlockCount() {
                return 0;
            }
        };
        PluginContext host = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            null,
            ghosts,
            null,
            null);
        manager = new PowerLinePreviewManager(host, state);
    }

    @Test
    void showLinePreviewSetsLineCachedMode() {
        PowerLineFootprint line = sampleLine();
        manager.showLinePreview(sampleResult(line));

        assertEquals(PowerLinePreviewManager.Mode.LINE_CACHED, manager.getMode());
        assertEquals(1, ghostBatches.size());
    }

    @Test
    void clearLineCachedPreviewClearsGhostsAndResetsMode() {
        PowerLineFootprint line = sampleLine();
        manager.showLinePreview(sampleResult(line));

        manager.clearLineCachedPreview();

        assertEquals(PowerLinePreviewManager.Mode.NONE, manager.getMode());
        assertTrue(clearOwnerCount.get() >= 1);
    }

    private static PowerLineFootprint sampleLine() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
    }

    private static PowerLineGenerationResult sampleResult(PowerLineFootprint line) {
        PowerLineGenerationResult result = new PowerLineGenerationResult(line);
        result.placementRecords.put(
            new BlockPos(1, 64, 2),
            new BlockRecord(new BlockPos(1, 64, 2), "minecraft:air", "minecraft:oak_fence"));
        return result;
    }
}
