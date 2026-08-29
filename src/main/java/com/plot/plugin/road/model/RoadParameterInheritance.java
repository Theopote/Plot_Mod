package com.plot.plugin.road.model;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CrossSectionFieldResolution;
import com.plot.plugin.road.model.section.RoadCrossSection;

/**
 * 道路工程参数的继承语义（全局默认 ↔ 单条道路覆盖）。
 *
 * <h3>三层模型</h3>
 * <ul>
 *   <li><b>显式覆盖</b> — {@link Road} / {@link RoadCrossSection} 字段 {@code != null}，写入 JSON</li>
 *   <li><b>继承默认</b> — 字段为 {@code null}，解析时取自 {@link RoadSystemConfig}</li>
 *   <li><b>有效值</b> — {@link Road#getEffectiveWidth(RoadSystemConfig)}、
 *       {@link RoadCrossSection#resolve(RoadSystemConfig)}、{@link CrossSectionFieldResolution}</li>
 * </ul>
 *
 * <h3>何时写入哪种状态</h3>
 * <ul>
 *   <li>{@link RoadNetwork#createRoad()} — 空道路，全部字段继承（动态跟随全局默认）</li>
 *   <li>{@link #snapshotGlobalDefaults(Road, RoadSystemConfig)} / 认领 — 将认领面板当前默认<strong>快照</strong>为显式值</li>
 *   <li>{@link Road#copyEngineeringFrom(Road)} — 拆分道路时复制父路显式工程属性</li>
 *   <li>单条编辑 / 批量应用 — 用户改动写入显式覆盖</li>
 *   <li>{@link Road#inheritAllDefaults()} — 清空覆盖，恢复动态继承</li>
 * </ul>
 *
 * <p>解析生成、预览、校验一律使用有效值 API，不要直接读可能为 {@code null} 的存储字段。
 */
public final class RoadParameterInheritance {

    private RoadParameterInheritance() {
    }

    /**
     * 将当前全局默认（及已选道路类型预设）快照到道路，写入显式值。
     * 认领新道路时应调用；之后修改认领默认参数不会影响已认领道路。
     */
    public static void snapshotGlobalDefaults(Road road, RoadSystemConfig defaults) {
        if (road == null || defaults == null) {
            return;
        }
        road.applyDefaults(defaults);
        String styleId = defaults.getSelectedPreset();
        if (styleId != null && !styleId.isBlank()) {
            road.applyStyle(styleId, defaults);
        }
    }

    public static boolean inheritsMaxSlope(Road road) {
        return road != null && road.getMaxSlope() == null;
    }

    public static boolean inheritsWidth(Road road) {
        return road != null && road.getWidth() == null;
    }

    public static CrossSectionFieldResolution<Integer> resolveWidth(Road road, RoadSystemConfig defaults) {
        if (defaults == null) {
            return CrossSectionFieldResolution.of(null, null, 0);
        }
        Integer explicit = road != null ? road.getWidth() : null;
        int inherited = defaults.getRoadWidth();
        int resolved = road != null ? road.getEffectiveWidth(defaults) : inherited;
        return CrossSectionFieldResolution.of(explicit, inherited, resolved);
    }

    public static CrossSectionFieldResolution<Float> resolveMaxSlope(Road road, RoadSystemConfig defaults) {
        if (defaults == null) {
            return CrossSectionFieldResolution.of(null, null, 0f);
        }
        Float explicit = road != null ? road.getMaxSlope() : null;
        float inherited = defaults.getMaxSlope();
        float resolved = road != null ? road.getEffectiveMaxSlope(defaults) : inherited;
        return CrossSectionFieldResolution.of(explicit, inherited, resolved);
    }
}
