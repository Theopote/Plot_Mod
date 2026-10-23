package com.plot.plugin.earthwork.design;

/**
 * 无已加载地形样本或控制点不足，无法求解设计标高。
 */
public final class InsufficientTerrainSamplesException extends RuntimeException {
    public InsufficientTerrainSamplesException() {
        super("Insufficient loaded terrain samples for earthwork resolution");
    }
}
