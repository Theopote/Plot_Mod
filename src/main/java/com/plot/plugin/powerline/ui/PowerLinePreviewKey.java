package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 预览缓存版本键：记录计算预览时线路参数与杆塔设计工程的状态指纹。
 */
public record PowerLinePreviewKey(
        String footprintId,
        int footprintFingerprint,
        int designProjectFingerprint) {

    public static PowerLinePreviewKey capture(
            PowerLineFootprint footprint,
            PowerLineDesignProject designProject) {
        if (footprint == null) {
            throw new IllegalArgumentException("footprint cannot be null");
        }
        return new PowerLinePreviewKey(
            footprint.getId(),
            footprint.generationFingerprint(),
            designProjectFingerprint(designProject));
    }

    public boolean matches(PowerLineFootprint footprint, PowerLineDesignProject designProject) {
        if (footprint == null || footprintId == null || !footprintId.equals(footprint.getId())) {
            return false;
        }
        return footprintFingerprint == footprint.generationFingerprint()
            && designProjectFingerprint == designProjectFingerprint(designProject);
    }

    private static int designProjectFingerprint(PowerLineDesignProject designProject) {
        if (designProject == null) {
            return 0;
        }
        return designProject.toJson().hashCode();
    }
}
