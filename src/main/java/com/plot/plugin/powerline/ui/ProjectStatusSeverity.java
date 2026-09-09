package com.plot.plugin.powerline.ui;

import com.plot.plugin.ui.PluginUiColors;

/** 顶部工具栏项目状态颜色等级。 */
public enum ProjectStatusSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR;

    public int color() {
        return switch (this) {
            case SUCCESS -> PluginUiColors.STATUS_OK;
            case WARNING -> PluginUiColors.WARNING;
            case ERROR -> PluginUiColors.ERROR_SOFT;
            case INFO -> PluginUiColors.STATUS_INFO;
        };
    }
}
