package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.vertical.VerticalProfileDesignRules;

/**
 * Options for voxel build-height compilation (preview and generation).
 */
public record ProfileCompileOptions(
        int minEffectiveRunBlocks,
        int maxReversalSpanBlocks,
        int algorithmVersion) {

    public static final int CURRENT_ALGORITHM_VERSION = 2;

    /** Default span for eliminating grade-sign reversals on an otherwise flat design. */
    public static final int DEFAULT_MAX_REVERSAL_SPAN_BLOCKS = 48;

    public ProfileCompileOptions {
        if (minEffectiveRunBlocks < 1) {
            minEffectiveRunBlocks = (int) Math.ceil(VerticalProfileDesignRules.MIN_VERTICAL_TRANSITION_LENGTH);
        }
        if (maxReversalSpanBlocks < minEffectiveRunBlocks) {
            maxReversalSpanBlocks = DEFAULT_MAX_REVERSAL_SPAN_BLOCKS;
        }
        if (algorithmVersion < 1) {
            algorithmVersion = CURRENT_ALGORITHM_VERSION;
        }
    }

    public ProfileCompileOptions(int minEffectiveRunBlocks, int algorithmVersion) {
        this(minEffectiveRunBlocks, DEFAULT_MAX_REVERSAL_SPAN_BLOCKS, algorithmVersion);
    }

    public static ProfileCompileOptions defaults() {
        return new ProfileCompileOptions(
            (int) Math.ceil(VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH),
            DEFAULT_MAX_REVERSAL_SPAN_BLOCKS,
            CURRENT_ALGORITHM_VERSION);
    }
}
