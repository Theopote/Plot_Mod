package com.plot.plugin.powerline.manager;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.ui.PowerLinePluginState;

import java.util.Objects;

/**
 * 电力线路生成预览缓存（结果保存在 {@link PowerLinePluginState}，画布/UI 展示用）。
 */
public final class PowerLinePreviewManager {
    public enum Mode {
        NONE,
        LINE_CACHED
    }

    private final PowerLinePluginState state;
    private Mode mode = Mode.NONE;

    public PowerLinePreviewManager(PowerLinePluginState state) {
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
    }

    public void clearLineCachedPreview() {
        state.setLastGenerationResult(null);
        state.setPreviewKey(null);
        state.setCanvasPreviewOverlay(null);
        state.setSelectedCanvasPoleSiteId("");
        mode = Mode.NONE;
    }
}
