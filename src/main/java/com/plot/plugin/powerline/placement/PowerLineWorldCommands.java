package com.plot.plugin.powerline.placement;

import com.plot.core.command.Command;
import com.plot.core.command.commands.PowerLineGenerateCommand;

/** 电力插件写入世界的可撤销命令识别。 */
public final class PowerLineWorldCommands {
    private PowerLineWorldCommands() {
    }

    public static boolean isPowerLineWorldCommand(Command command) {
        return command instanceof PowerLineGenerateCommand
            || command instanceof SingleTowerPlaceCommand;
    }
}
