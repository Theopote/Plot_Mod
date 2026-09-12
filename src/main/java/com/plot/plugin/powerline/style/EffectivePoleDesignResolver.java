package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 解析线路级 effective pole design：在已解析的几何之上应用 Style / Quick Tune override。
 * <p>
 * 预览与世界生成必须消费同一 effective design，避免 Preview 与 Minecraft 语义分叉。
 */
public final class EffectivePoleDesignResolver {
    private EffectivePoleDesignResolver() {
    }

    /**
     * @return 深拷贝并应用杆材 override；{@code source} 为 null 时返回 null
     */
    public static PoleDesign applyLineOverrides(PoleDesign source, PowerLineFootprint line) {
        if (source == null) {
            return null;
        }
        PoleDesign effective = source.copy();
        if (line != null) {
            applyPoleMaterialOverride(effective, line.getPoleMaterial());
        }
        return effective;
    }

    /** COLUMN（及塔体主材）跟随线路当前杆材 override。 */
    public static void applyPoleMaterialOverride(PoleDesign design, MaterialMix poleMaterial) {
        if (design == null || poleMaterial == null) {
            return;
        }
        MaterialMix mix = poleMaterial.copy();
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.COLUMN) {
                layer.setMaterial(mix.copy());
            }
        }
        if (design.hasTowerStructure()) {
            design.getTowerStructure().setPrimaryMaterial(mix.copy());
        }
    }
}
