package com.plot.api.world;

/**
 * 幽灵方块预览的 owner 标识，与插件 {@code getId()} 对齐，避免互相清除预览。
 */
public final class GhostBlockOwners {
    public static final String LEGACY = "legacy";
    public static final String LINE_TO_BLOCK = "line_to_block";
    public static final String BUILDING = "building";
    public static final String PATTERN = "pattern";
    public static final String ROAD = "road_system";
    public static final String EARTHWORK = "earthwork_balance";
    public static final String POWER_LINE = "power_line";

    private GhostBlockOwners() {
    }
}
