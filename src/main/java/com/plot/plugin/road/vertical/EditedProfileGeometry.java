package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.ProfileCompileInputsFingerprint;

/** 编辑曲线与道路平面几何的关联签名。 */
public final class EditedProfileGeometry {

    /** 未绑定几何（迁移遗留）；几何变更前视为有效。 */
    public static final int UNBOUND_SIGNATURE = 0;

    private EditedProfileGeometry() {
    }

    public static int signature(RoadNetwork network, Road road) {
        return ProfileCompileInputsFingerprint.geometryHash(network, road);
    }

    public static boolean matches(RoadNetwork network, Road road, int storedSignature) {
        if (storedSignature == UNBOUND_SIGNATURE) {
            return true;
        }
        return storedSignature == signature(network, road);
    }
}
