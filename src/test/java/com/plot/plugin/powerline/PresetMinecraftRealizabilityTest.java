package com.plot.plugin.powerline;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerProfileParameterMatrix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.EffectiveStylePreview;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.QUICK_TUNE_POLE_MATERIAL;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.assertDesignResolvable;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.assertEffectiveMaterialApplied;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.assertQuickTunePoleMaterialInGeneration;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.assertGalleryMatchesEffective;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.assertMinecraftRealizable;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.compileProfileDefault;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.generate;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.lineForPoleDesign;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.lineForPreset;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.lineForProfile;
import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.resolveCatalogDesign;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PL-TOWER-S1 Wave B：25 presets + 17 catalog designs + 9×TowerRole + 11 profiles
 * 的 Minecraft 可实现性全矩阵回归。
 */
class PresetMinecraftRealizabilityTest {

    @ParameterizedTest(name = "preset:{0}")
    @MethodSource("allPresets")
    void stylePresetIsMinecraftRealizable(PowerLineStylePreset preset) {
        PowerLineFootprint line = lineForPreset(preset);
        PoleDesign expectedDesign = resolveExpectedDesign(preset, line);
        assertDesignResolvable(preset.getId(), expectedDesign);

        PowerLineGenerationResult result = generate(line);
        assertMinecraftRealizable(preset.getId(), result, expectedDesign);
    }

    @ParameterizedTest(name = "preset:{0}")
    @MethodSource("parametricPresets")
    void stylePresetGalleryMatchesEffectiveDesign(PowerLineStylePreset preset) {
        assertGalleryMatchesEffective(preset);
    }

    @ParameterizedTest(name = "preset:{0}")
    @MethodSource("allPresets")
    void stylePresetHonorsQuickTunePoleMaterial(PowerLineStylePreset preset) {
        PowerLineFootprint line = lineForPreset(preset);
        line.setPoleMaterial(MaterialMix.single(QUICK_TUNE_POLE_MATERIAL));

        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(
            line,
            preset,
            new com.plot.plugin.powerline.design.PoleDesignResolver(
                new com.plot.plugin.powerline.model.PowerLineDesignProject()));

        assertNotNull(preview);
        assertEffectiveMaterialApplied(line, preview.previewDesign());
    }

    @ParameterizedTest(name = "preset:{0}")
    @MethodSource("allPresets")
    void stylePresetQuickTunePoleMaterialAppearsInGenerator(PowerLineStylePreset preset) {
        PowerLineFootprint line = lineForPreset(preset);
        line.setPoleMaterial(MaterialMix.single(QUICK_TUNE_POLE_MATERIAL));

        PowerLineGenerationResult result = generate(line);
        assertQuickTunePoleMaterialInGeneration(preset.getId(), result, QUICK_TUNE_POLE_MATERIAL);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogPoleDesignIds")
    void catalogPoleDesignIsMinecraftRealizable(String designId) {
        PoleDesign design = resolveCatalogDesign(designId);
        assertDesignResolvable(designId, design);

        PowerLineGenerationResult result = generate(lineForPoleDesign(designId));
        assertMinecraftRealizable(designId, result, design);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("familyRoleCases")
    void familyRoleDesignIsMinecraftRealizable(PresetMinecraftRealizabilitySupport.FamilyRoleCase case_) {
        PoleDesign design = resolveCatalogDesign(case_.designId());
        assertDesignResolvable(case_.toString(), design);

        PowerLineGenerationResult result = generate(lineForPoleDesign(case_.designId()));
        assertMinecraftRealizable(case_.toString(), result, design);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parametricProfiles")
    void parametricProfileIsMinecraftRealizable(TowerProfileParameterMatrix.ProfileEntry entry) {
        PoleDesign compiled = compileProfileDefault(entry);
        assertDesignResolvable(entry.label(), compiled);
        assertTrue(
            compiled.getAttachments().stream().anyMatch(attachment -> attachment.getInsulatorLength() > 0),
            entry.label() + " should compile attachments with insulator length");

        PowerLineFootprint line = lineForProfile(entry);
        PowerLineGenerationResult result = generate(line);
        PoleDesign effective = result.polePlacements.isEmpty()
            ? compiled
            : result.polePlacements.getFirst().design();
        assertMinecraftRealizable(entry.label(), result, effective);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapeSignatures")
    void coreTowerShapeSignaturesHold(PresetMinecraftRealizabilitySupport.ShapeSignature signature) {
        PoleDesign design = resolveCatalogDesign(signature.designId());
        assertNotNull(design, signature.label());
        signature.assertMatches(design);

        PowerLineGenerationResult result = generate(lineForPoleDesign(signature.designId()));
        assertMinecraftRealizable(signature.label(), result, design);

        PresetMinecraftRealizabilitySupport.DesignMetrics blockMetrics =
            PresetMinecraftRealizabilitySupport.blockMetricsOf(result, PresetMinecraftRealizabilitySupport.GROUND_Y);
        assertTrue(blockMetrics.height() >= signature.minHeight() * 0.85,
            signature.label() + " generated height " + blockMetrics.height());
        assertTrue(blockMetrics.width() >= signature.minWidth() * 0.5,
            signature.label() + " generated width " + blockMetrics.width());
    }

    static Stream<PowerLineStylePreset> allPresets() {
        return PresetMinecraftRealizabilitySupport.allPresets().stream();
    }

    static Stream<PowerLineStylePreset> parametricPresets() {
        return allPresets().filter(preset -> preset.getDefinition().hasParametricConfig());
    }

    static Stream<String> catalogPoleDesignIds() {
        return PresetMinecraftRealizabilitySupport.catalogPoleDesignIds().stream();
    }

    static Stream<PresetMinecraftRealizabilitySupport.FamilyRoleCase> familyRoleCases() {
        return PresetMinecraftRealizabilitySupport.familyRoleCases().stream();
    }

    static Stream<TowerProfileParameterMatrix.ProfileEntry> parametricProfiles() {
        return PresetMinecraftRealizabilitySupport.parametricProfiles().stream();
    }

    static Stream<PresetMinecraftRealizabilitySupport.ShapeSignature> shapeSignatures() {
        return PresetMinecraftRealizabilitySupport.shapeSignatures().stream();
    }

    private static PoleDesign resolveExpectedDesign(PowerLineStylePreset preset, PowerLineFootprint line) {
        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(
            line,
            preset,
            new com.plot.plugin.powerline.design.PoleDesignResolver(
                new com.plot.plugin.powerline.model.PowerLineDesignProject()));
        if (preview != null && preview.previewDesign() != null) {
            return preview.previewDesign();
        }
        if (preset.getTowerFamilyId() != null) {
            return resolveCatalogDesign(
                com.plot.plugin.powerline.design.family.TowerFamilyCatalog.findBuiltin(preset.getTowerFamilyId())
                    .getDesignId(com.plot.plugin.powerline.model.TowerRole.SUSPENSION));
        }
        return resolveCatalogDesign(preset.getPoleDesignId());
    }
}
