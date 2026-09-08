package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 工程/地形分析缓存版本键：绑定到某次预览的线路与设计工程指纹。
 */
public record PowerLineAnalysisKey(
        String footprintId,
        int footprintFingerprint,
        int designProjectFingerprint) {

    public static PowerLineAnalysisKey capture(PowerLinePreviewKey previewKey) {
        if (previewKey == null) {
            return null;
        }
        return new PowerLineAnalysisKey(
            previewKey.footprintId(),
            previewKey.footprintFingerprint(),
            previewKey.designProjectFingerprint());
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
        return footprintFingerprint == currentPreviewKey.footprintFingerprint()
            && designProjectFingerprint == currentPreviewKey.designProjectFingerprint()
            && currentPreviewKey.matches(footprint, designProject);
    }
}
