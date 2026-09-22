package com.plot.plugin.powerline.design.structure;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.TowerLocalPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerArmPlacementTest {

    @Test
    void taperedArmUsesLightweightSinglePlaneBracingWithoutBottomChord() {
        TowerArm arm = new TowerArm("arm_lower", 26, 12);
        arm.setShape(TowerArmShape.TAPERED);
        arm.setBracing(BracingPattern.X);
        arm.setVerticalDrop(4);
        arm.setLongitudinalHalfWidth(1.8);

        RecordingPlacer recording = new RecordingPlacer();
        TowerArmPlacement.placeArm(
            arm,
            MaterialMix.single("minecraft:oak_planks"),
            MaterialMix.single("minecraft:iron_bars"),
            recording::chord,
            recording::brace);

        assertTrue(recording.bottomChords.isEmpty(), "lightweight tapered arm should not place bottom chord");
        assertEquals(1, uniqueLongitudes(recording.braces), "brace lines should use a single longitudinal plane");
        assertTrue(recording.braces.size() >= 2, "X bracing should still place diagonal members");
    }

    @Test
    void trussArmKeepsFullBottomChordAndDualPlaneBracing() {
        TowerArm arm = new TowerArm("arm_truss", 26, 12);
        arm.setShape(TowerArmShape.TRUSS);
        arm.setBracing(BracingPattern.X);
        arm.setVerticalDrop(4);
        arm.setLongitudinalHalfWidth(1.8);

        RecordingPlacer recording = new RecordingPlacer();
        TowerArmPlacement.placeArm(
            arm,
            MaterialMix.single("minecraft:oak_planks"),
            MaterialMix.single("minecraft:iron_bars"),
            recording::chord,
            recording::brace);

        assertEquals(1, recording.bottomChords.size(), "truss arm should keep bottom chord");
        assertEquals(2, uniqueLongitudes(recording.braces), "truss arm should brace on both longitudinal planes");
    }

    private static int uniqueLongitudes(List<BraceSegment> braces) {
        Set<Long> longitudes = new HashSet<>();
        for (BraceSegment brace : braces) {
            longitudes.add(Math.round(brace.start().longitudinal() * 1000.0));
            longitudes.add(Math.round(brace.end().longitudinal() * 1000.0));
        }
        return longitudes.size();
    }

    private record BraceSegment(TowerLocalPoint start, TowerLocalPoint end) {
    }

    private static final class RecordingPlacer {
        private final List<String> bottomChords = new ArrayList<>();
        private final List<BraceSegment> braces = new ArrayList<>();

        void chord(double lateralStart, double lateralEnd, double height, double longHalf, MaterialMix material) {
            if (height < 25.5) {
                bottomChords.add(lateralStart + "," + lateralEnd + "@" + height);
            }
        }

        void brace(TowerLocalPoint start, TowerLocalPoint end, MaterialMix material) {
            braces.add(new BraceSegment(start, end));
        }
    }
}
