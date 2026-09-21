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
        return matchesParameters(footprint, designProject) && projectionFingerprint == 0;
    }

    /**
     * 线路参数与杆塔设计是否仍与预览计算时一致（不含世界投影尺度）。
     * 画布预览有效性应使用此方法，避免因视角/玩家移动导致每帧重算。
     */
    public boolean matchesParameters(PowerLineFootprint footprint, PowerLineDesignProject designProject) {
        if (footprint == null || footprintId == null || !footprintId.equals(footprint.getId())) {
            return false;
        }
        return footprintFingerprint == footprint.geometryFingerprint()
            && designProjectFingerprint == designProjectFingerprint(designProject);
    }

    private static int designProjectFingerprint(PowerLineDesignProject designProject) {
        if (designProject == null) {
            return 0;
        }
        return designProject.toJson().hashCode();
    }
}
