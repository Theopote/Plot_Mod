package com.plot.plugin.powerline.placement;

import com.plot.core.command.Command;
import com.plot.plugin.powerline.ui.PowerLinePluginState;

/** 世界命令撤销/重做与插件状态同步。 */
public final class PowerLineWorldCommandSync {
    private PowerLineWorldCommandSync() {
    }

    public static void afterUndo(Command command, PowerLinePluginState state) {
        // 线路生成命令的撤销由全局命令历史处理，无需额外登记簿同步。
    }

    public static void afterRedo(Command command, PowerLinePluginState state) {
        // 线路生成命令的重做由全局命令历史处理，无需额外登记簿同步。
    }
}
