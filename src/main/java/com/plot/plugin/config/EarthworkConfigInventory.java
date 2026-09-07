package com.plot.plugin.config;

/**
 * {@link EarthworkConfig} 字段分类清单：区分插件偏好、认领默认值与误放的运行时状态。
 * <p>
 * 运行时预览/方量/解析标高属于 {@link com.plot.plugin.earthwork.model.EarthworkProject}、
 * {@link com.plot.plugin.earthwork.model.GradingRegion}、
 * {@link com.plot.plugin.earthwork.model.EarthworkSite}，不应写入全局 plugin config。
 */
public final class EarthworkConfigInventory {

    public enum FieldKind {
        /** 用户 UI 偏好，与当前工程无关。 */
        PLUGIN_PREFERENCE,
        /** 认领新区块时的初始默认值（写入 GradingRegion / Site 后由工程文件持有）。 */
        ADOPT_DEFAULT,
        /** 历史误放的全局运行时缓存，加载后丢弃、保存时不写出。 */
        RUNTIME_LEGACY
    }

    public record FieldSpec(String jsonKey, FieldKind kind, String owner, String notes) {
    }

    public static final FieldSpec WORK_MODE =
        new FieldSpec("workMode", FieldKind.PLUGIN_PREFERENCE, "EarthworkConfig", "Quick / Builder / Learn");
    public static final FieldSpec SHOW_GRID =
        new FieldSpec("showGrid", FieldKind.PLUGIN_PREFERENCE, "EarthworkConfig", "预览网格显示");
    public static final FieldSpec SHOW_EDGE_OVERLAY =
        new FieldSpec("showEdgeTreatmentOverlay", FieldKind.PLUGIN_PREFERENCE, "EarthworkConfig", "边坡处理画布叠加");
    public static final FieldSpec PREVIEW_GRID_SIZE =
        new FieldSpec("previewGridSize", FieldKind.ADOPT_DEFAULT, "GradingRegion.previewGridSize", "认领时复制到新区块");
    public static final FieldSpec MATERIAL_DEFAULTS =
        new FieldSpec("reusableRatio / cutToCompactedFillRatio", FieldKind.ADOPT_DEFAULT,
            "GradingRegion.materialProperties / EarthworkSite.materialModel",
            "认领默认值；Learn 滑条也会更新插件默认");
    public static final FieldSpec AUTO_BALANCE =
        new FieldSpec("autoBalance", FieldKind.ADOPT_DEFAULT, "GradingRegion.autoBalance", "认领时复制到新区块");
    public static final FieldSpec TARGET_ELEVATION =
        new FieldSpec("targetElevation", FieldKind.RUNTIME_LEGACY, "GradingRegion.manualTargetElevation",
            "曾作为全局手动标高；默认 0 有 Y=0 风险，已改由认领时采样现状地面");
    public static final FieldSpec CUT_VOLUME =
        new FieldSpec("cutVolume", FieldKind.RUNTIME_LEGACY, "EarthworkVolumeReport / preview",
            "预览运行时方量，不应持久化");
    public static final FieldSpec FILL_VOLUME =
        new FieldSpec("fillVolume", FieldKind.RUNTIME_LEGACY, "EarthworkVolumeReport / preview",
            "预览运行时方量，不应持久化");

    private EarthworkConfigInventory() {
    }

    public static FieldSpec[] allFields() {
        return new FieldSpec[] {
            WORK_MODE,
            SHOW_GRID,
            SHOW_EDGE_OVERLAY,
            PREVIEW_GRID_SIZE,
            MATERIAL_DEFAULTS,
            AUTO_BALANCE,
            TARGET_ELEVATION,
            CUT_VOLUME,
            FILL_VOLUME
        };
    }
}
