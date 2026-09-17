package com.plot.plugin.pattern;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.pattern.model.PatternProject;

import java.util.Objects;

/** 图案预览快照：结果 + 来源身份，供建造前校验。 */
public record PatternGenerationSnapshot(
        PatternPreviewKey key,
        PatternGenerationResult result) {

    public PatternGenerationSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(result, "result");
    }

    public boolean matches(
            PatternProject project,
            PatternSelectionSet selection,
            long projectRevision,
            ICoordinateService coordinates) {
        return key.matches(project, selection, projectRevision, coordinates);
    }

    public boolean hasPlacements() {
        return result != null && result.hasPlacements();
    }

    public int blockCount() {
        return result != null ? result.getBlockCount() : 0;
    }
}
