package com.plot.plugin.road.pipeline;

/**
 * 单条道路边的生成结果分类（与「成功但 0 方块」和「抛异常」区分）。
 */
public enum EdgeGenerationOutcome {
    /** 正常生成（可能 0 方块，但流程完整走完） */
    SUCCESS,
    /** 前置条件不满足而跳过（如中心线过短、输入为空） */
    SKIPPED,
    /** 生成过程中抛错 */
    FAILED
}
