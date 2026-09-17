package com.plot.plugin.pattern;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;

import java.util.List;
import java.util.Objects;

/**
 * 图案预览缓存版本键：记录计算预览时的区域选择、项目修订与配置指纹。
 */
public record PatternPreviewKey(
        List<String> footprintIds,
        long projectRevision,
        int configFingerprint,
        int projectionFingerprint) {

    public static PatternPreviewKey capture(
            List<PatternFootprint> footprints,
            long projectRevision,
            ICoordinateService coordinates) {
        List<String> ids = footprints.stream()
            .map(PatternFootprint::getId)
            .toList();
        int projectionFingerprint = coordinates != null
            ? coordinates.captureProjection().fingerprint()
            : 0;
        return new PatternPreviewKey(
            List.copyOf(ids),
            projectRevision,
            PatternPreviewFingerprint.configFingerprint(footprints),
            projectionFingerprint);
    }

    public boolean matches(
            PatternProject project,
            PatternSelectionSet selection,
            long currentProjectRevision,
            ICoordinateService coordinates) {
        if (project == null || selection == null) {
            return false;
        }
        List<PatternFootprint> currentFootprints = selection.resolve(project);
        List<String> currentIds = currentFootprints.stream()
            .map(PatternFootprint::getId)
            .toList();
        if (!Objects.equals(footprintIds, currentIds)) {
            return false;
        }
        if (projectRevision != currentProjectRevision) {
            return false;
        }
        if (configFingerprint != PatternPreviewFingerprint.configFingerprint(currentFootprints)) {
            return false;
        }
        int currentProjection = coordinates != null
            ? coordinates.captureProjection().fingerprint()
            : 0;
        return projectionFingerprint == currentProjection;
    }
}
