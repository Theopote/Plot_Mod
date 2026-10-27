package com.plot.plugin.road.profile;

/**
 * 纵断面高程读取语义：生成管线 vs 编辑器工作副本。
 */
public enum ProfileElevationReadMode {
    /** 体素/预览：edgeResults 优先，VA 仅在生成权威为 EDITED 时使用。 */
    GENERATION,
    /** 编辑器叠加：可读已保存的可求值 VA，不依赖生成权威。 */
    DESIGN_WORKING_COPY
}
