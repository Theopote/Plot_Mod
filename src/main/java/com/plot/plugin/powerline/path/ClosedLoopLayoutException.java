package com.plot.plugin.powerline.path;

/** 闭合线路布塔失败（塔数不足或塔位退化共线）。 */
public final class ClosedLoopLayoutException extends IllegalStateException {

    public ClosedLoopLayoutException(String message) {
        super(message);
    }
}
