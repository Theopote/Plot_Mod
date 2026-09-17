package com.plot.plugin.pattern;

/**
 * 图案预览/生成失败或警告原因（供 UI 分条提示，避免共用 generate_empty_result）。
 */
public enum PatternGenerationIssue {
    NONE(""),
    IMAGE_PLUGIN_DATA_UNAVAILABLE("plugin.pattern.issue.image_plugin_data_unavailable"),
    IMAGE_NOT_IMPORTED("plugin.pattern.issue.image_not_imported"),
    IMAGE_FILE_MISSING("plugin.pattern.issue.image_file_missing"),
    EMPTY_PALETTE("plugin.pattern.issue.empty_palette"),
    NO_SAMPLE_POINTS("plugin.pattern.issue.no_sample_points"),
    ALL_PIXELS_TRANSPARENT("plugin.pattern.issue.all_pixels_transparent"),
    REGION_TOO_SMALL("plugin.pattern.issue.region_too_small");

    private final String statusKey;

    PatternGenerationIssue(String statusKey) {
        this.statusKey = statusKey;
    }

    public String statusKey() {
        return statusKey;
    }

    public boolean isError() {
        return this != NONE;
    }
}
