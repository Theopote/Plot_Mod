package com.plot.plugin.road.model;

import java.util.Locale;

/**
 * 逻辑道路拓扑模式。
 *
 * @see docs/decisions/0004-road-topology-invariant.md
 */
public enum RoadTopologyMode {
    /** 连续 open chain：连通、节点度数 ≤ 2、恰有两个端点 */
    LINEAR,
    /** 闭合环：连通、节点度数均为 2（如 Ring Road） */
    LOOP;

    public static RoadTopologyMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return LINEAR;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LINEAR;
        }
    }
}
