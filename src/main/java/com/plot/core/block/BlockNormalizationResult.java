package com.plot.core.block;

/**
 * 方块 ID 规范化结果。未知或无法解析的方块不得静默回退为空气。
 */
public record BlockNormalizationResult(boolean valid, String normalized, String error) {
    public static BlockNormalizationResult valid(String normalized) {
        return new BlockNormalizationResult(true, normalized, null);
    }

    public static BlockNormalizationResult invalid(String error) {
        return new BlockNormalizationResult(false, null, error);
    }
}
