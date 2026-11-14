package com.plot.plugin.config;

/**
 * {@link EarthworkConfig} 字段分类清单：区分插件偏好与认领默认值。
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
        ADOPT_DEFAULT
    }

    public record FieldSpec(String jsonKey, FieldKind kind, String owner, String notes) {
    }

    public static final FieldSpec WORK_MODE =
        new FieldSpec("workMode", FieldKind.PLUGIN_PREFERENCE, "EarthworkConfig", "Quick / Builder");
    public static final FieldSpec SHOW_GRID =
        new FieldSpec("showGrid", FieldKind.PLUGIN_PREFERENCE, "EarthworkConfig", "预览网格显示");
    public static final FieldSpec SHOW_EDGE_OVERLAY =
        new FieldSpec("showEdgeTreatmentOverlay", FieldKind.PLUGIN_PREFERENCE, "EarthworkConfig", "边坡处理画布叠加");
    public static final FieldSpec PREVIEW_GRID_SIZE =
        new FieldSpec("previewGridSize", FieldKind.ADOPT_DEFAULT, "GradingRegion.previewGridSize", "认领时复制到新区块");
    public static final FieldSpec MATERIAL_DEFAULTS =
        new FieldSpec("reusableRatio / cutToCompactedFillRatio", FieldKind.ADOPT_DEFAULT,
            "GradingRegion.materialProperties / EarthworkSite.materialModel",
            "认领默认值；建造模式材料滑条可提升为认领默认");
    public static final FieldSpec AUTO_BALANCE =
        new FieldSpec("autoBalance", FieldKind.ADOPT_DEFAULT, "GradingRegion.autoBalance", "认领时复制到新区块");

    private EarthworkConfigInventory() {
    }

    public static FieldSpec[] allFields() {
        return new FieldSpec[] {
            WORK_MODE,
            SHOW_GRID,
            SHOW_EDGE_OVERLAY,
            PREVIEW_GRID_SIZE,
            MATERIAL_DEFAULTS,
            AUTO_BALANCE
        };
    }
}
