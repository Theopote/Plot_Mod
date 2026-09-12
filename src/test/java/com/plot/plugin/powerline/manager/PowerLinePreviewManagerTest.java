package com.plot.plugin.powerline.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IGhostBlockService;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLinePluginState;
import com.plot.plugin.powerline.ui.PowerLinePreviewKey;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePreviewManagerTest {
    private final List<Map<BlockPos, String>> ghostBatches = new ArrayList<>();
    private final AtomicInteger clearCount = new AtomicInteger();
    private PowerLinePluginState state;
    private PowerLinePreviewManager manager;

    @BeforeEach
    void setUp() {
        ghostBatches.clear();
        clearCount.set(0);
        state = new PowerLinePluginState();
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        IGhostBlockService ghosts = new IGhostBlockService() {
            @Override
            public void clearAllGhostBlocks() {
                clearCount.incrementAndGet();
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

            @Override
            public void addGhostBlocks(Map<BlockPos, String> blocks) {
                ghostBatches.add(new LinkedHashMap<>(blocks));
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
    void enterSingleTowerModeClearsLinePreviewMetadata() {
        PowerLineFootprint line = sampleLine();
        state.setPreviewKey(PowerLinePreviewKey.capture(line, state.getDesignProject()));
        state.setLastGenerationResult(sampleResult(line));

        manager.enterSingleTowerMode();

        assertEquals(PowerLinePreviewManager.Mode.SINGLE_TOWER_INTERACTIVE, manager.getMode());
        assertNull(state.getPreviewKey());
        assertNull(state.getLastGenerationResult());
        assertTrue(clearCount.get() >= 1);
    }

    @Test
    void showLinePreviewSetsLineCachedMode() {
        PowerLineFootprint line = sampleLine();
        manager.showLinePreview(sampleResult(line));

        assertEquals(PowerLinePreviewManager.Mode.LINE_CACHED, manager.getMode());
        assertEquals(1, ghostBatches.size());
    }

    @Test
    void invalidateLineMetadataDuringSingleTowerKeepsInteractiveMode() {
        PowerLineFootprint line = sampleLine();
        manager.enterSingleTowerMode();
        manager.showSingleTowerPreview(sampleResult(line));

        manager.clearLineCachedPreview();

        assertEquals(PowerLinePreviewManager.Mode.SINGLE_TOWER_INTERACTIVE, manager.getMode());
        assertTrue(clearCount.get() >= 1);
    }

    @Test
    void exitSingleTowerModeClearsGhostsAndResetsMode() {
        PowerLineFootprint line = sampleLine();
        manager.showSingleTowerPreview(sampleResult(line));

        manager.exitSingleTowerMode();

        assertEquals(PowerLinePreviewManager.Mode.NONE, manager.getMode());
        assertTrue(clearCount.get() >= 1);
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
