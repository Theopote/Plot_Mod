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
 * 线路缓存预览与单塔交互预览互斥，仅本类调用 {@link IGhostBlockService}。
 */
public final class PowerLinePreviewManager {
    public enum Mode {
        NONE,
        LINE_CACHED,
        SINGLE_TOWER_INTERACTIVE
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

    public boolean isSingleTowerInteractive() {
        return mode == Mode.SINGLE_TOWER_INTERACTIVE;
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

    public void showSingleTowerPreview(PowerLineGenerationResult result) {
        if (result == null) {
            clearGhostsOnly();
            return;
        }
        mode = Mode.SINGLE_TOWER_INTERACTIVE;
        projectGhosts(result);
    }

    /** 进入单塔放置：清除线路缓存预览，Ghost 由后续 hover 刷新写入。 */
    public void enterSingleTowerMode() {
        clearLineCachedMetadata();
        mode = Mode.SINGLE_TOWER_INTERACTIVE;
        clearGhostsOnly();
    }

    /** 退出单塔放置并清除 Ghost。 */
    public void exitSingleTowerMode() {
        if (mode == Mode.SINGLE_TOWER_INTERACTIVE) {
            clearGhostsOnly();
            mode = Mode.NONE;
        }
    }

    /** 清除线路缓存预览（key/result/analysis）；单塔模式下保留交互 Ghost。 */
    public void clearLineCachedPreview() {
        clearLineCachedMetadata();
        if (mode == Mode.LINE_CACHED) {
            clearGhostsOnly();
            mode = Mode.NONE;
        }
    }

    /** 清除全部 Ghost，不改变模式（单塔 hover 无效时调用）。 */
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
