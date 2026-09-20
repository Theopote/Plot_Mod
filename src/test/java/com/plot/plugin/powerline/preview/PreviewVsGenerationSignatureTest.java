package com.plot.plugin.powerline.preview;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.design.family.VisualTowerResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.style.EffectiveStylePreview;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.plot.plugin.powerline.preview.PresetPreviewTestSupport.assertSignaturesClose;
import static com.plot.plugin.powerline.preview.PresetPreviewTestSupport.signature;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** PL-PRESET-S6：Effective 预览与 Generator applicator 路径签名一致。 */
class PreviewVsGenerationSignatureTest {
    private final PoleDesignResolver designResolver = new PoleDesignResolver(new PowerLineDesignProject());
    private final PoleDesignAssignmentResolver assignmentResolver = new PoleDesignAssignmentResolver(
        designResolver,
        new TowerFamilyResolver());

    static Stream<PowerLineStylePreset> towerFamilyPresets() {
        return PowerLineStylePresetCatalog.defaultPresets().stream()
            .filter(preset -> preset.getTowerFamilyId() != null && !preset.getTowerFamilyId().isBlank());
    }

    @ParameterizedTest
    @MethodSource("towerFamilyPresets")
    void effectivePreviewMatchesGeneratorApplicatorForSuspensionRole(PowerLineStylePreset preset) {
        PowerLineFootprint line = sampleLine();
        preset.apply(line);

        EffectiveStylePreview effective = EffectiveStylePreviewResolver.resolve(line, preset, designResolver);
        assertNotNull(effective);
        assertNotNull(effective.previewDesign());

        PoleDesign assigned = resolveRole(line, TowerRole.SUSPENSION);
        PoleDesign applied = ParametricStyleTowerApplicator.apply(
            assigned,
            line.getParametricTowerConfig(),
            null,
            line);

        assertSignaturesClose(
            preset.getId() + " suspension",
            signature(applied),
            signature(effective.previewDesign()));
    }

    private PoleDesign resolveRole(PowerLineFootprint line, TowerRole role) {
        PowerPoleSite site = new PowerPoleSite(new Vec2d(10, 0));
        site.setRole(role);
        site.setRoleAutoAssigned(false);
        if (TowerFamily.GRADED_LATTICE_3_PHASE_ID.equals(line.getTowerFamilyId())) {
            PoleDesign tall = designResolver.find(TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID);
            assertNotNull(tall, "missing graded tall suspension design");
            return tall;
        }
        List<PowerPoleSite> sites = List.of(
            new PowerPoleSite(line.getPathPoints().getFirst()),
            new PowerPoleSite(line.getPathPoints().getLast()));
        double spanHint = VisualTowerResolver.maxAdjacentSpanBlocks(sites, 0, line.isClosedLoop());
        PoleDesignAssignmentResolver.AssignmentResult result = assignmentResolver.resolve(site, line, spanHint);
        assertNotNull(result.design(), "missing design for role " + role + " on " + line.getTowerFamilyId());
        return result.design();
    }

    private static PowerLineFootprint sampleLine() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        line.setSagRatio(0.0);
        line.setMaxPoleSpacing(100.0);
        return line;
    }
}
