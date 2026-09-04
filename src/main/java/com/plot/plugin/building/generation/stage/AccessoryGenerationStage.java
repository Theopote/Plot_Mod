package com.plot.plugin.building.generation.stage;

import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.component.BalconyGenerator;
import com.plot.plugin.building.generation.component.CanopyGenerator;
import com.plot.plugin.building.generation.component.ParapetGenerator;
import com.plot.plugin.building.model.spec.AccessorySpec;
import com.plot.plugin.building.model.spec.BalconySpec;
import com.plot.plugin.building.model.spec.CanopySpec;

/**
 * 建筑附属构件阶段：女儿墙、雨篷、阳台等。
 * <p>
 * 位于 Roof 之后、Opening 之前，以便开洞可穿透外挑构件。
 */
public final class AccessoryGenerationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "accessory";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        AccessorySpec accessory = context.getDefinition().accessory();
        if (!accessory.hasAnyEnabled()) {
            return;
        }
        ParapetGenerator.generate(context, accessory.parapet());
        for (CanopySpec canopy : accessory.canopies()) {
            CanopyGenerator.generate(context, canopy);
        }
        for (BalconySpec balcony : accessory.balconies()) {
            BalconyGenerator.generate(context, balcony);
        }
    }
}
