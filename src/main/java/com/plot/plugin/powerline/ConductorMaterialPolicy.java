package com.plot.plugin.powerline;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 按挂点角色选择导线材质。 */
public final class ConductorMaterialPolicy {
    private ConductorMaterialPolicy() {
    }

    public static MaterialMix materialFor(AttachmentRole role, PowerLineFootprint footprint) {
        if (footprint == null) {
            return MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL);
        }
        if (role == AttachmentRole.GROUND_WIRE) {
            return footprint.getGroundWireMaterial();
        }
        return footprint.getWireMaterial();
    }
}
