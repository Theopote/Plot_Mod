package com.plot.plugin.road;

import com.plot.core.material.MaterialMix;

/**
 * {@link MaterialMix} 与道路材质 key 的互转。
 */
public final class RoadMaterialMixUtils {
    private RoadMaterialMixUtils() {
    }

    public static MaterialMix normalizeStored(MaterialMix mix) {
        if (mix == null) {
            return null;
        }
        MaterialMix normalized = mix.copy();
        normalized.setPrimaryMaterial(RoadMaterialUtils.normalizeStoredMaterial(mix.getPrimaryMaterial()));
        normalized.setAccentMaterial(RoadMaterialUtils.normalizeStoredMaterial(mix.getAccentMaterial()));
        return normalized;
    }
}
