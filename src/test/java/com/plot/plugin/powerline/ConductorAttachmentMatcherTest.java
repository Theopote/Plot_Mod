package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConductorAttachmentMatcherTest {

    @Test
    void prefersExactIdMatchBeforeRoleFallback() {
        ResolvedAttachment startA = attachment("phase_a", AttachmentRole.PHASE_A, -3);
        ResolvedAttachment startLegacy = attachment("legacy_a", AttachmentRole.PHASE_A, -2);
        ResolvedAttachment endA = attachment("phase_a", AttachmentRole.PHASE_A, -3);
        ResolvedAttachment endLegacy = attachment("legacy_a", AttachmentRole.PHASE_A, -2);

        List<ConductorAttachmentMatcher.Pair> pairs = ConductorAttachmentMatcher.match(
            List.of(startA, startLegacy),
            List.of(endA, endLegacy),
            new PowerLineGenerationResult(null),
            new Vec2d(0, 0),
            new Vec2d(10, 0));

        assertEquals(2, pairs.size());
        assertEquals("phase_a", pairs.get(0).start().id());
        assertEquals("phase_a", pairs.get(0).end().id());
        assertEquals("legacy_a", pairs.get(1).start().id());
        assertEquals("legacy_a", pairs.get(1).end().id());
    }

    @Test
    void fallsBackToRoleOrderWhenIdsDiffer() {
        ResolvedAttachment startA = attachment("arm1_phase_a", AttachmentRole.PHASE_A, -3);
        ResolvedAttachment startB = attachment("arm1_phase_b", AttachmentRole.PHASE_B, 0);
        ResolvedAttachment endA = attachment("phase_a", AttachmentRole.PHASE_A, -3);
        ResolvedAttachment endC = attachment("phase_c", AttachmentRole.PHASE_C, 3);

        PowerLineGenerationResult result = new PowerLineGenerationResult(null);
        List<ConductorAttachmentMatcher.Pair> pairs = ConductorAttachmentMatcher.match(
            List.of(startA, startB),
            List.of(endA, endC),
            result,
            new Vec2d(0, 0),
            new Vec2d(10, 0));

        assertEquals(1, pairs.size());
        assertEquals("arm1_phase_a", pairs.get(0).start().id());
        assertEquals("phase_a", pairs.get(0).end().id());
        assertTrue(result.warnings.stream().anyMatch(
            w -> w.contains("plugin.powerline.warn.attachment_role_fallback")
                && w.contains("arm1_phase_a")
                && w.contains("phase_a")));
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("arm1_phase_b")));
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("phase_c")));
    }

    @Test
    void unmatchedTopWireDoesNotPairAcrossDifferentRoles() {
        ResolvedAttachment startA = attachment("phase_a", AttachmentRole.PHASE_A, -3);
        ResolvedAttachment startTop = attachment("top_wire", AttachmentRole.TOP_WIRE, 0);
        ResolvedAttachment endA = attachment("phase_a", AttachmentRole.PHASE_A, -3);

        PowerLineGenerationResult result = new PowerLineGenerationResult(null);
        List<ConductorAttachmentMatcher.Pair> pairs = ConductorAttachmentMatcher.match(
            List.of(startA, startTop),
            List.of(endA),
            result,
            new Vec2d(0, 0),
            new Vec2d(10, 0));

        assertEquals(1, pairs.size());
        assertEquals(AttachmentRole.PHASE_A, pairs.get(0).start().role());
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("top_wire")));
    }

    private static ResolvedAttachment attachment(String id, AttachmentRole role, double lateral) {
        Vec2d plan = new Vec2d(lateral, 0);
        return new ResolvedAttachment(
            id,
            id,
            role,
            plan,
            plan.x,
            74.0,
            plan.y,
            74.0,
            MaterialMix.single("minecraft:iron_bars"),
            0);
    }
}
