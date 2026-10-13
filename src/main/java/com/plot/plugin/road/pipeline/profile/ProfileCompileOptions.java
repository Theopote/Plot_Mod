package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.vertical.VerticalProfileDesignRules;

/**
 * Options for voxel build-height compilation (preview and generation).
 */
public record ProfileCompileOptions(
        int minEffectiveRunBlocks,
        int algorithmVersion) {

    public static final int CURRENT_ALGORITHM_VERSION = 1;

    public ProfileCompileOptions {
        if (minEffectiveRunBlocks < 1) {
            minEffectiveRunBlocks = (int) Math.ceil(VerticalProfileDesignRules.MIN_VERTICAL_TRANSITION_LENGTH);
        }
        if (algorithmVersion < 1) {
            algorithmVersion = CURRENT_ALGORITHM_VERSION;
        }
    }

    public static ProfileCompileOptions defaults() {
        return new ProfileCompileOptions(
            (int) Math.ceil(VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH),
            CURRENT_ALGORITHM_VERSION);
    }
}
