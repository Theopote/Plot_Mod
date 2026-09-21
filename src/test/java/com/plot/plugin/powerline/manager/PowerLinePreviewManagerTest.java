package com.plot.plugin.powerline.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLinePluginState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerLinePreviewManagerTest {
    private PowerLinePluginState state;
    private PowerLinePreviewManager manager;

    @BeforeEach
    void setUp() {
        state = new PowerLinePluginState();
        manager = new PowerLinePreviewManager(state);
    }

    @Test
    void showLinePreviewSetsLineCachedMode() {
        PowerLineFootprint line = sampleLine();
        manager.showLinePreview(sampleResult(line));

        assertEquals(PowerLinePreviewManager.Mode.LINE_CACHED, manager.getMode());
    }

    @Test
    void clearLineCachedPreviewClearsCachedResultAndResetsMode() {
        PowerLineFootprint line = sampleLine();
        PowerLineGenerationResult result = sampleResult(line);
        state.setLastGenerationResult(result);
        manager.showLinePreview(result);

        manager.clearLineCachedPreview();

        assertEquals(PowerLinePreviewManager.Mode.NONE, manager.getMode());
        assertNull(state.getLastGenerationResult());
        assertNull(state.getPreviewKey());
        assertNull(state.getCanvasPreviewOverlay());
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
