package com.plot.plugin.powerline.path;

/** 认领线路与画布参考路径的同步状态。 */
public enum SourceSyncStatus {
    NOT_LINKED,
    OK,
    STALE,
    MISSING,
    UNSUPPORTED,
    DEGENERATE
}
