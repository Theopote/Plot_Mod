package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;

/** 常识性线路检查的只读输入。 */
public record LineValidationContext(
        PowerLineGeometryModel geometry,
        TerrainSampler terrain,
        PowerLineFootprint footprint,
        ValidationLimits limits) {
}
