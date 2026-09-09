package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.BundleVisual;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConductorSpanGeneratorBundleVisualTest {

    @Test
    void twinBundleVisualDoublesWireBlocksAlongSpan() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        footprint.setWireMaterial(MaterialMix.single("minecraft:iron_bars"));

        PowerLineGenerationResult singleResult = new PowerLineGenerationResult(footprint);
        ConductorSpanGenerator.generateConductorSpan(
            attachment("a", BundleVisual.SINGLE),
            attachment("b", BundleVisual.SINGLE),
            0,
            1,
            "s0",
            "s1",
            footprint,
            null,
            singleResult,
            null);

        PowerLineGenerationResult twinResult = new PowerLineGenerationResult(footprint);
        ConductorSpanGenerator.generateConductorSpan(
            attachment("a", BundleVisual.TWIN),
            attachment("b", BundleVisual.TWIN),
            0,
            1,
            "s0",
            "s1",
            footprint,
            null,
            twinResult,
            null);

        long singleBlocks = singleResult.placementRecords.size();
        long twinBlocks = twinResult.placementRecords.size();
        assertTrue(singleBlocks > 0);
        assertTrue(twinBlocks > singleBlocks);
        assertTrue(twinBlocks >= singleBlocks * 2 - 4);
    }

    private static ResolvedAttachment attachment(String suffix, BundleVisual bundleVisual) {
        return new ResolvedAttachment(
            "phase_a_" + suffix,
            "A" + suffix,
            AttachmentRole.PHASE_A,
            new Vec2d(suffix.equals("a") ? 0 : 40, 0),
            suffix.equals("a") ? 0.5 : 40.5,
            64.0,
            0.5,
            63.0,
            MaterialMix.single("minecraft:iron_bars"),
            1,
            com.plot.plugin.powerline.equipment.InsulatorType.SUSPENSION,
            ResolvedAttachment.mountStyleFor(com.plot.plugin.powerline.equipment.InsulatorType.SUSPENSION),
            bundleVisual);
    }
}
