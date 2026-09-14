package com.plot.plugin.powerline.manager;

import com.plot.api.world.IGhostBlockService;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.ui.PowerLinePluginState;

import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 电力线路世界 Ghost 预览统一入口（对标 {@link com.plot.plugin.earthwork.manager.EarthworkPreviewManager}）。
 * 仅本类调用 {@link IGhostBlockService}。
 */
public final class PowerLinePreviewManager {
    public enum Mode {
        NONE,
        LINE_CACHED
    }

    private final PluginContext host;
    private final PowerLinePluginState state;
    private Mode mode = Mode.NONE;

    public PowerLinePreviewManager(PluginContext host, PowerLinePluginState state) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isLineCached() {
        return mode == Mode.LINE_CACHED;
    }

    public void showLinePreview(PowerLineGenerationResult result) {
        if (result == null) {
            clearLineCachedPreview();
            return;
        }
        mode = Mode.LINE_CACHED;
        projectGhosts(result);
    }

    public void clearLineCachedPreview() {
        clearLineCachedMetadata();
        if (mode == Mode.LINE_CACHED) {
            clearGhostsOnly();
            mode = Mode.NONE;
        }
    }

    public void clearGhostsOnly() {
        IGhostBlockService ghosts = host.ghosts();
        if (ghosts != null) {
            ghosts.clearAllGhostBlocks();
        }
    }

    private void clearLineCachedMetadata() {
        state.setLastGenerationResult(null);
        state.setPreviewKey(null);
        state.getValidationState().clearAnalysisReports();
    }

    private void projectGhosts(PowerLineGenerationResult result) {
        IGhostBlockService ghosts = host.ghosts();
        if (ghosts == null || result == null) {
            return;
        }
        ghosts.clearAllGhostBlocks();
        Map<BlockPos, String> blocks = new LinkedHashMap<>();
        for (BlockRecord record : result.placementRecords.values()) {
            blocks.put(record.pos, record.newBlockId);
        }
        if (!blocks.isEmpty()) {
            ghosts.addGhostBlocks(blocks);
        }
    }
}
