package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;

/** 常识性线路检查的只读输入。 */
public record LineValidationContext(
        PowerLineGeometryModel geometry,
        TerrainSampler terrain,
        PowerLineFootprint footprint,
        EngineeringRuleProfile profile,
        ValidationLimits limits) {
}
