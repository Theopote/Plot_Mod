package com.plot.plugin.powerline.ui;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 预览缓存版本键：记录计算预览时线路参数、投影尺度与杆塔设计工程的状态指纹。
 */
public record PowerLinePreviewKey(
        String footprintId,
        int footprintFingerprint,
        int designProjectFingerprint,
        int projectionFingerprint) {

    public static PowerLinePreviewKey capture(
            PowerLineFootprint footprint,
            PowerLineDesignProject designProject) {
        return capture(footprint, designProject, null);
    }

    public static PowerLinePreviewKey capture(
            PowerLineFootprint footprint,
            PowerLineDesignProject designProject,
            ICoordinateService coordinates) {
        if (footprint == null) {
            throw new IllegalArgumentException("footprint cannot be null");
        }
        int projectionFingerprint = coordinates != null
            ? coordinates.captureProjection().fingerprint()
            : 0;
        return new PowerLinePreviewKey(
            footprint.getId(),
            footprint.geometryFingerprint(),
            designProjectFingerprint(designProject),
            projectionFingerprint);
    }

    public boolean matches(
            PowerLineFootprint footprint,
            PowerLineDesignProject designProject,
            ICoordinateService coordinates) {
        if (footprint == null || footprintId == null || !footprintId.equals(footprint.getId())) {
            return false;
        }
        int currentProjection = coordinates != null
            ? coordinates.captureProjection().fingerprint()
            : 0;
        return footprintFingerprint == footprint.geometryFingerprint()
            && designProjectFingerprint == designProjectFingerprint(designProject)
            && projectionFingerprint == currentProjection;
    }

    public boolean matches(PowerLineFootprint footprint, PowerLineDesignProject designProject) {
        if (footprint == null || footprintId == null || !footprintId.equals(footprint.getId())) {
            return false;
        }
        return footprintFingerprint == footprint.geometryFingerprint()
            && designProjectFingerprint == designProjectFingerprint(designProject)
            && projectionFingerprint == 0;
    }

    private static int designProjectFingerprint(PowerLineDesignProject designProject) {
        if (designProject == null) {
            return 0;
        }
        return designProject.toJson().hashCode();
    }
}
