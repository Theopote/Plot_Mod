package com.plot.plugin.powerline.design;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.ConductorSpanGenerator;
import com.plot.plugin.powerline.PowerLineAttachmentResolver;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.design.family.TowerConductorArrangement;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.equipment.InsulatorType;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.TerrainTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledConductorArrangementTest {

    @Test
    void bundledThreePhaseUsesOneLogicalAttachmentPerPhaseWithBundleVisual() {
        List<ConductorAttachment> attachments = ConductorAttachmentPresets.bundledThreePhaseHorizontal(
            18, -9, 0, 9, 2, 0.8);

        assertEquals(3, attachments.size());
        assertTrue(attachments.stream().anyMatch(a -> ConductorAttachmentPresets.PHASE_A_ID.equals(a.getId())));
        assertTrue(attachments.stream().anyMatch(a -> ConductorAttachmentPresets.PHASE_B_ID.equals(a.getId())));
        assertTrue(attachments.stream().anyMatch(a -> ConductorAttachmentPresets.PHASE_C_ID.equals(a.getId())));
        assertTrue(attachments.stream().noneMatch(a -> a.getId().contains("_1")));
        assertTrue(attachments.stream().allMatch(a -> a.getBundleVisual() == BundleVisual.TWIN));
    }

    @Test
    void quadBundleUsesSingleAttachmentWithQuadVisual() {
        List<ConductorAttachment> attachments = ConductorAttachmentPresets.bundledThreePhaseHorizontal(
            18, -9, 0, 9, 4, 0.8);

        assertEquals(3, attachments.size());
        assertTrue(attachments.stream().allMatch(a -> a.getBundleVisual() == BundleVisual.QUAD));
    }

    @Test
    void megaIndustrialTowerHasThreePhaseAttachmentsPlusTwinTopWires() {
        List<ConductorAttachment> attachments = TowerConductorArrangement.megaIndustrial()
            .createAttachments(40, InsulatorType.SUSPENSION, 4);

        assertEquals(5, attachments.size());
        long phases = attachments.stream()
            .filter(a -> a.getRole() == AttachmentRole.PHASE_A
                || a.getRole() == AttachmentRole.PHASE_B
                || a.getRole() == AttachmentRole.PHASE_C)
            .count();
        assertEquals(3, phases);
        assertTrue(attachments.stream()
            .filter(a -> a.getRole() != AttachmentRole.TOP_WIRE)
            .allMatch(a -> a.getBundleVisual() == BundleVisual.TWIN));
    }

    @Test
    void megaLatticeDesignMatchesLogicalPhaseCount() {
        var design = TowerFamilyDesignPresets.megaLatticeSuspension();
        assertEquals(8, design.getAttachments().size());
        assertEquals(6, ConductorArrangement.megaThreeDeck().phaseConductorCount());
    }

    @Test
    void bundledMegaTowerSpanCountMatchesLogicalPhasesNotSubconductors() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        footprint.setWireMaterial(MaterialMix.single("minecraft:iron_bars"));

        var design = TowerFamilyDesignPresets.megaLatticeSuspension();
        PowerLineAttachmentResolver resolver = new PowerLineAttachmentResolver(null);
        PoleFrame startFrame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        PoleFrame endFrame = PoleFrame.fromPole(new Vec2d(40, 0), new Vec2d(1, 0), 64);
        List<ResolvedAttachment> startAttachments = resolver.resolve(design, startFrame);
        List<ResolvedAttachment> endAttachments = resolver.resolve(design, endFrame);

        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        PolePlacement start = new PolePlacement(
            new Vec2d(0, 0),
            startFrame,
            design,
            startAttachments,
            80,
            true,
            TowerRole.SUSPENSION,
            design.getId(),
            0.0);
        PolePlacement end = new PolePlacement(
            new Vec2d(40, 0),
            endFrame,
            design,
            endAttachments,
            80,
            true,
            TowerRole.SUSPENSION,
            design.getId(),
            40.0);

        ConductorSpanGenerator.generateBetween(
            start,
            end,
            0,
            1,
            "start",
            "end",
            footprint,
            TerrainTestFixtures.flatTerrain(64),
            result,
            TerrainTestFixtures.identityCoordinates(),
            TerrainTestFixtures.projection());

        assertEquals(8, result.conductorSpans.size());
        long phaseSpans = result.conductorSpans.stream()
            .filter(span -> span.getRole() == AttachmentRole.PHASE_A
                || span.getRole() == AttachmentRole.PHASE_B
                || span.getRole() == AttachmentRole.PHASE_C)
            .count();
        assertEquals(6, phaseSpans);
        assertTrue(result.conductorSpans.stream()
            .noneMatch(span -> span.getAttachmentId() != null && span.getAttachmentId().contains("_1")));
    }
}
