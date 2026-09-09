package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;

/**
 * 线路当前生效的风格预览快照（设计几何 + 材质 overrides）。
 */
public record EffectiveStylePreview(
        PoleDesign previewDesign,
        MaterialMix poleMaterial,
        MaterialMix wireMaterial,
        MaterialMix topWireMaterial,
        PowerLineStylePreset basePreset) {
}
