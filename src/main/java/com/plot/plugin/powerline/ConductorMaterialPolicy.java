package com.plot.plugin.powerline;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 按挂点角色选择导线材质。主导线默认 {@link PowerLineFootprint#DEFAULT_WIRE_MATERIAL}（iron_bars）；地线/顶线用 {@link PowerLineFootprint#getTopWireMaterial()}。 */
public final class ConductorMaterialPolicy {
    private ConductorMaterialPolicy() {
    }

    public static MaterialMix materialFor(AttachmentRole role, PowerLineFootprint footprint) {
        if (footprint == null) {
            return MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL);
        }
        if (role == AttachmentRole.TOP_WIRE) {
            return footprint.getTopWireMaterial();
        }
        return footprint.getWireMaterial();
    }
}
