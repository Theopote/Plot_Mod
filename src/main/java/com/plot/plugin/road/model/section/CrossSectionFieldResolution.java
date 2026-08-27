package com.plot.plugin.road.model.section;

/**
 * 横断面字段的三层语义：显式覆盖、继承默认、解析有效值。
 *
 * <p>组件字段约定：{@code null} 表示继承 {@link com.plot.plugin.config.RoadSystemConfig}；
 * 非 {@code null} 表示该道路的显式覆盖。{@link ResolvedCrossSection} 负责合并出最终有效值。
 */
public record CrossSectionFieldResolution<T>(
        T resolved,
        T explicitOverride,
        T inheritedDefault,
        boolean inherited) {

    public static <T> CrossSectionFieldResolution<T> of(
            T explicitOverride,
            T inheritedDefault,
            T resolved) {
        return new CrossSectionFieldResolution<>(resolved, explicitOverride, inheritedDefault, explicitOverride == null);
    }
}
