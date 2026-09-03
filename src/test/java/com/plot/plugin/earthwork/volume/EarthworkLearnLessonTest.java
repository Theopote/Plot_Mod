package com.plot.plugin.earthwork.volume;

import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkLearnLessonTest {

    @Test
    void oneToOneStoryMatchesDocumentedCutFillExample() {
        EarthworkLearnLesson.ConversionStory story = EarthworkLearnLesson.fromGeometry(500L, 430L);
        assertEquals(500L, story.dug());
        assertEquals(430L, story.fillNeeded());
        assertEquals(70L, story.leftoverIfOneToOne());
        assertEquals(0L, story.missingIfOneToOne());
        assertTrue(story.realityDiffersFromOneToOne());
        assertEquals(450L, story.realityReusable());
        assertEquals(414L, story.realityUsableFill());
        assertEquals(0L, story.realityExport());
        assertEquals(16L, story.realityImport());
        assertEquals(90.0f, story.reusablePercent(), 1e-3f);
        assertEquals(92.0f, story.compactedPercent(), 1e-3f);
    }

    @Test
    void usesGeometricVolumesFromReport() {
        EarthworkVolumeReport report = EarthworkVolumeReport.fromMetrics(
            100L, 40L, MaterialConversionModel.MINECRAFT, 0L, 0L);
        EarthworkLearnLesson.ConversionStory story = EarthworkLearnLesson.from(report);
        assertEquals(100L, story.dug());
        assertEquals(40L, story.fillNeeded());
        assertEquals(60L, story.leftoverIfOneToOne());
    }
}
