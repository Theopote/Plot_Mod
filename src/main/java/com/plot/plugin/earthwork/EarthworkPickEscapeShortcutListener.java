package com.plot.plugin.earthwork;

import com.plot.api.shortcut.IShortcutListener;
import com.plot.utils.PlotI18n;

/**
 * 区域 / 三点 / 孔洞拾取进行中时优先消费 Esc，避免全局 Escape 仅清空选择而拾取会话仍保持激活。
 */
public final class EarthworkPickEscapeShortcutListener implements IShortcutListener {
    private static final String ESCAPE = "escape";

    private final EarthworkRegionPickSession regionPickSession;
    private final EarthworkThreePointPickSession threePointPickSession;
    private final EarthworkHolePickSession holePickSession;

    public EarthworkPickEscapeShortcutListener(
            EarthworkRegionPickSession regionPickSession,
            EarthworkThreePointPickSession threePointPickSession,
            EarthworkHolePickSession holePickSession) {
        this.regionPickSession = regionPickSession;
        this.threePointPickSession = threePointPickSession;
        this.holePickSession = holePickSession;
    }

    @Override
    public boolean onShortcutTriggered(String shortcut) {
        if (!ESCAPE.equalsIgnoreCase(shortcut)) {
            return false;
        }
        if (holePickSession.isActive()) {
            holePickSession.cancel();
            return true;
        }
        if (threePointPickSession.isActive()) {
            threePointPickSession.cancel();
            return true;
        }
        if (regionPickSession.isActive()) {
            regionPickSession.cancel();
            return true;
        }
        return false;
    }

    @Override
    public int getPriority() {
        return 250;
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("plugin.earthwork.pick.cancel_escape");
    }
}
