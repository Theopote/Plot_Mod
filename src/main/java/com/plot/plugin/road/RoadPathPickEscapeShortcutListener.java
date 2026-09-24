package com.plot.plugin.road;

import com.plot.api.shortcut.IShortcutListener;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.utils.PlotI18n;

/**
 * 路径拾取进行中时优先消费 Esc，避免全局 Escape 仅清空选择而拾取会话仍保持激活。
 */
public final class RoadPathPickEscapeShortcutListener implements IShortcutListener {
    private static final String ESCAPE = "escape";

    private final RoadToolManager toolManager;

    public RoadPathPickEscapeShortcutListener(RoadToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public boolean onShortcutTriggered(String shortcut) {
        if (!ESCAPE.equalsIgnoreCase(shortcut)) {
            return false;
        }
        if (!toolManager.getPathPickSession().isActive()) {
            return false;
        }
        toolManager.cancelPathPick();
        return true;
    }

    @Override
    public int getPriority() {
        return 250;
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("plugin.road.path.cancel_pick_escape");
    }
}
