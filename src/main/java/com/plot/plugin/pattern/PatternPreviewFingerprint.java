package com.plot.plugin.pattern;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;

import java.util.Comparator;
import java.util.List;

/** 图案预览/建造用的配置指纹。 */
public final class PatternPreviewFingerprint {
    private PatternPreviewFingerprint() {
    }

    public static int configFingerprint(List<PatternFootprint> footprints) {
        if (footprints == null || footprints.isEmpty()) {
            return 0;
        }
        PatternProject temp = new PatternProject();
        footprints.stream()
            .sorted(Comparator.comparing(PatternFootprint::getId))
            .forEach(footprint -> temp.addFootprint(cloneForFingerprint(footprint)));
        return temp.toJson().hashCode();
    }

    private static PatternFootprint cloneForFingerprint(PatternFootprint source) {
        PatternFootprint copy = new PatternFootprint(source.getId(), source.getOuterPoints());
        copy.setHoles(source.getHoles());
        copy.setSource(source.getSource());
        copy.setPattern(source.getPattern());
        copy.setImagePattern(source.getImagePattern());
        copy.setBorderConfig(source.getBorderConfig());
        return copy;
    }
}
