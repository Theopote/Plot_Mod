package com.plot.ui.tools.helper;

import com.plot.ui.canvas.CanvasAccess;

/**
 * 请求画布重绘工具预览层（键盘数值输入等无鼠标移动的场景）。
 */
public final class ToolPreviewRefresh {

    private ToolPreviewRefresh() {
    }

    public static void request() {
        try {
            if (CanvasAccess.isPresent()) {
                CanvasAccess.get().markToolPreviewDirty();
            }
        } catch (Throwable ignored) {
            // 预览刷新为尽力而为，不影响工具逻辑
        }
    }
}
