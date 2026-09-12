package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 工程/地形分析缓存版本键：绑定到某次预览的几何指纹与分析开关。
 */
public record PowerLineAnalysisKey(
        String footprintId,
        int geometryFingerprint,
        int analysisFingerprint,
        int designProjectFingerprint,
        int projectionFingerprint) {

    public static PowerLineAnalysisKey capture(
            PowerLinePreviewKey previewKey,
            PowerLineFootprint footprint) {
        if (previewKey == null || footprint == null) {
            return null;
        }
        return new PowerLineAnalysisKey(
            previewKey.footprintId(),
            previewKey.footprintFingerprint(),
            footprint.analysisFingerprint(),
            previewKey.designProjectFingerprint(),
            previewKey.projectionFingerprint());
    }

    public boolean matches(
            PowerLineFootprint footprint,
            PowerLineDesignProject designProject,
            PowerLinePreviewKey currentPreviewKey) {
        if (footprint == null || currentPreviewKey == null || footprintId == null) {
            return false;
        }
        if (!footprintId.equals(footprint.getId())) {
            return false;
        }
        return geometryFingerprint == currentPreviewKey.footprintFingerprint()
            && analysisFingerprint == footprint.analysisFingerprint()
            && designProjectFingerprint == currentPreviewKey.designProjectFingerprint()
            && projectionFingerprint == currentPreviewKey.projectionFingerprint();
    }
}
