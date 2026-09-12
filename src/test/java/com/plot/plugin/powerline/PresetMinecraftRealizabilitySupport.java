package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerProfileParameterMatrix;
import com.plot.plugin.powerline.design.structure.TowerArmShape;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.style.EffectivePoleDesignResolver;
import com.plot.plugin.powerline.style.EffectiveStylePreview;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.plugin.powerline.style.PowerLineStyleParametricCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import com.plot.core.terrain.TerrainSampler;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL-TOWER-S1 Wave B：预设 Minecraft 可实现性测试共用断言与生成工具。 */
final class PresetMinecraftRealizabilitySupport {
    static final int GROUND_Y = 64;

    private PresetMinecraftRealizabilitySupport() {
    }

    record DesignMetrics(
            double height,
            double width,
            double depth,
            int armCount,
            int enabledAttachmentCount) {
    }

    record FamilyRoleCase(String familyId, TowerRole role, String designId) {
        @Override
        public String toString() {
            return familyId + " / " + role;
        }
    }

    record ShapeSignature(
            String label,
            String designId,
            double minHeight,
            double maxHeight,
            double minWidth,
            double maxWidth,
            Integer minArms,
            Integer maxArms,
            TowerArmShape requiredArmShape) {

        void assertMatches(DesignMetrics metrics) {
            assertTrue(
                metrics.height() >= minHeight && metrics.height() <= maxHeight,
                label + " height " + metrics.height() + " outside [" + minHeight + ", " + maxHeight + "]");
            assertTrue(
                metrics.width() >= minWidth && metrics.width() <= maxWidth,
                label + " width " + metrics.width() + " outside [" + minWidth + ", " + maxWidth + "]");
            if (minArms != null) {
                assertTrue(metrics.armCount() >= minArms, label + " arms " + metrics.armCount() + " < " + minArms);
            }
            if (maxArms != null) {
                assertTrue(metrics.armCount() <= maxArms, label + " arms " + metrics.armCount() + " > " + maxArms);
            }
        }

        void assertMatches(PoleDesign design) {
            assertMatches(metricsOf(design));
            if (requiredArmShape != null && design.hasTowerStructure()) {
                assertTrue(
                    design.getTowerStructure().getArms().stream()
                        .anyMatch(arm -> arm.getShape() == requiredArmShape),
                    label + " missing arm shape " + requiredArmShape);
            }
        }
    }

    static List<PowerLineStylePreset> allPresets() {
        return PowerLineStylePresetCatalog.defaultPresets();
    }

    static List<String> catalogPoleDesignIds() {
        return PoleDesignCatalog.defaultDesigns().stream().map(PoleDesign::getId).toList();
    }

    static List<FamilyRoleCase> familyRoleCases() {
        return TowerFamilyCatalog.defaultFamilies().stream()
            .flatMap(family -> java.util.Arrays.stream(TowerRole.values())
                .map(role -> new FamilyRoleCase(
                    family.getId(),
                    role,
                    family.getDesignId(role))))
            .filter(case_ -> case_.designId() != null && !case_.designId().isBlank())
            .toList();
    }

    static List<TowerProfileParameterMatrix.ProfileEntry> parametricProfiles() {
        return TowerProfileParameterMatrix.profiles();
    }

    static List<ShapeSignature> shapeSignatures() {
        return List.of(
            familySignature("Classic Lattice", TowerFamily.STANDARD_LATTICE_3_PHASE_ID, TowerRole.SUSPENSION,
                30, 42, 10, 30, 2, 2, null),
            familySignature("Triple Arm", TowerFamily.TRIPLE_ARM_3_PHASE_ID, TowerRole.SUSPENSION,
                44, 58, 12, 34, 3, 3, null),
            familySignature("Cup Tower", TowerFamily.CUP_TOWER_ID, TowerRole.SUSPENSION,
                34, 48, 14, 36, 1, 1, TowerArmShape.UPSWEEP),
            familySignature("Monster Pylon", TowerFamily.MONSTER_PYLON_ID, TowerRole.SUSPENSION,
                68, 95, 24, 70, 3, 6, null));
    }

    static PowerLineFootprint lineForProfile(TowerProfileParameterMatrix.ProfileEntry entry) {
        PowerLineFootprint line = sampleLine();
        line.setParametricTowerConfig(configForProfile(entry.profileId()));
        line.setPoleDesignId(basePoleDesignForProfile(entry.profileId()));
        return line;
    }

    private static String basePoleDesignForProfile(String profileId) {
        if (com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.STEAMPUNK_ID.equals(profileId)) {
            return PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID;
        }
        if (com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.MODERN_HV_GLASS_ID.equals(profileId)) {
            return PoleDesignCatalog.MODERN_HV_GLASS_TOWER_ID;
        }
        if (com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.HEAVY_ID.equals(profileId)) {
            return PoleDesignCatalog.HEAVY_LATTICE_TOWER_ID;
        }
        return PoleDesignCatalog.LATTICE_STEEL_TOWER_ID;
    }

    private static ShapeSignature familySignature(
            String label,
            String familyId,
            TowerRole role,
            double minHeight,
            double maxHeight,
            double minWidth,
            double maxWidth,
            int minArms,
            int maxArms,
            TowerArmShape armShape) {
        TowerFamily family = TowerFamilyCatalog.findBuiltin(familyId);
        String designId = family != null ? family.getDesignId(role) : null;
        return new ShapeSignature(
            label,
            designId,
            minHeight,
            maxHeight,
            minWidth,
            maxWidth,
            minArms,
            maxArms,
            armShape);
    }

    static PowerLineFootprint sampleLine() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setSagRatio(0.0);
        line.setMaxPoleSpacing(50.0);
        return line;
    }

    static PowerLineFootprint lineForPoleDesign(String designId) {
        PowerLineFootprint line = sampleLine();
        line.setPoleDesignId(designId);
        return line;
    }

    static PowerLineFootprint lineForPreset(PowerLineStylePreset preset) {
        PowerLineFootprint line = sampleLine();
        preset.apply(line);
        if (preset.getSpacingProfile() != null) {
            line.setMaxPoleSpacing(Math.min(50.0, preset.getSpacingProfile().preferred()));
        }
        return line;
    }

    static PowerLineGenerationResult generate(PowerLineFootprint line) {
        return new PowerLineGenerator(identityCoordinates(), projection()).generate(
            line,
            flatTerrain(GROUND_Y),
            resolver());
    }

    static PoleDesign compileProfileDefault(TowerProfileParameterMatrix.ProfileEntry entry) {
        TowerGeneratorConfig config = configForProfile(entry.profileId());
        return PowerLineStyleParametricCatalog.compileRepresentative(config);
    }

    static TowerGeneratorConfig configForProfile(String profileId) {
        return switch (profileId) {
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.SMALL_LATTICE_ID ->
                TowerGeneratorConfig.parametricSmallLattice(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.smallLatticeDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID ->
                TowerGeneratorConfig.parametricClassic(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.classicDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.TRIPLE_ARM_ID ->
                TowerGeneratorConfig.parametricTripleArm(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.tripleArmDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.CUP_ID ->
                TowerGeneratorConfig.parametricCup(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.cupDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.HEAVY_ID ->
                TowerGeneratorConfig.parametricHeavy(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.heavyDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.MEGA_ID ->
                TowerGeneratorConfig.parametricMega(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.megaDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.PORTAL_ID ->
                TowerGeneratorConfig.parametricPortal(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.portalDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.DRUM_ID ->
                TowerGeneratorConfig.parametricDrum(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.drumDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.UHV_ID ->
                TowerGeneratorConfig.parametricUhv(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.uhvDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.STEAMPUNK_ID ->
                TowerGeneratorConfig.parametricSteampunk(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.steampunkDefaults());
            case com.plot.plugin.powerline.design.parametric.TowerParameterProfiles.MODERN_HV_GLASS_ID ->
                TowerGeneratorConfig.parametricModernHvGlass(
                    com.plot.plugin.powerline.design.parametric.TowerParameterSet.modernHvGlassDefaults());
            default -> throw new IllegalArgumentException("Unknown profile: " + profileId);
        };
    }

    static void assertDesignResolvable(String label, PoleDesign design) {
        assertNotNull(design, label + " design");
        assertTrue(hasGeometry(design), label + " should have layers or tower structure");
        assertTrue(countEnabledAttachments(design) > 0, label + " should expose conductor attachments");
    }

    static void assertMinecraftRealizable(String label, PowerLineGenerationResult result, PoleDesign design) {
        assertNotNull(result, label);
        assertFalse(
            result.warnings.stream().anyMatch(w -> w.contains("not_found")),
            label + " should not warn about missing designs: " + result.warnings);
        assertTrue(result.blockCount() > 0, label + " should place blocks");
        assertTrue(result.poleCount >= 1, label + " should place at least one pole");

        assertNoAirFallback(label, result);
        assertAttachmentsPresent(label, result, design);
        assertInsulatorsWhenRequired(label, result, design);
    }

    static void assertGalleryMatchesEffective(PowerLineStylePreset preset) {
        if (!preset.getDefinition().hasParametricConfig()) {
            return;
        }
        PowerLineFootprint line = lineForPreset(preset);
        PoleDesign gallery = PowerLineStylePreviewBinding.previewDesign(preset);
        EffectiveStylePreview effective = EffectiveStylePreviewResolver.resolve(line, preset, resolver());

        assertNotNull(gallery, preset.getId() + " gallery design");
        assertNotNull(effective, preset.getId() + " effective preview");
        PoleDesign effectiveDesign = effective.previewDesign();
        assertNotNull(effectiveDesign, preset.getId() + " effective design");

        if (gallery.hasTowerStructure() && effectiveDesign.hasTowerStructure()) {
            assertTrue(gallery.isParametricMode(), preset.getId() + " gallery should be parametric");
            assertEqualsClose(
                preset.getId() + " height",
                gallery.getTowerStructure().maxHeight(),
                effectiveDesign.getTowerStructure().maxHeight(),
                0.75);
            assertEquals(
                preset.getId() + " arm count",
                gallery.getTowerStructure().getArms().size(),
                effectiveDesign.getTowerStructure().getArms().size());
            assertEquals(
                preset.getId() + " profile",
                gallery.getGeneratorConfig().profileId(),
                effectiveDesign.getGeneratorConfig().profileId());
        }
    }

    static void assertEffectiveMaterialApplied(PowerLineFootprint line, PoleDesign effective) {
        MaterialMix poleMaterial = line.getPoleMaterial();
        if (poleMaterial == null) {
            return;
        }
        String expected = poleMaterial.getPrimaryMaterial();
        if (effective.hasTowerStructure()) {
            assertEquals(
                "tower primary material",
                expected,
                effective.getTowerStructure().getPrimaryMaterial().getPrimaryMaterial());
        } else {
            assertTrue(
                effective.getLayers().stream()
                    .filter(layer -> layer.getShape() == PoleLayer.Shape.COLUMN)
                    .anyMatch(layer -> expected.equals(layer.getMaterial().getPrimaryMaterial())),
                "effective design should apply pole material to columns");
        }
    }

    static DesignMetrics blockMetricsOf(PowerLineGenerationResult result, int groundY) {
        if (result.placementRecords.isEmpty()) {
            return new DesignMetrics(0, 0, 0, 0, 0);
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockRecord record : result.placementRecords.values()) {
            if ("minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            BlockPos pos = record.pos;
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return new DesignMetrics(0, 0, 0, 0, 0);
        }
        return new DesignMetrics(
            maxY - Math.max(groundY, minY) + 1,
            maxX - minX + 1,
            maxZ - minZ + 1,
            0,
            0);
    }

    static DesignMetrics metricsOf(PoleDesign design) {
        if (design.hasTowerStructure()) {
            TowerStructureDesign structure = design.getTowerStructure();
            return new DesignMetrics(
                structure.maxHeight(),
                structure.maxHalfWidth() * 2.0,
                structure.maxHalfDepth() * 2.0,
                structure.getArms().size(),
                countEnabledAttachments(design));
        }
        double width = design.getLayers().stream()
            .filter(layer -> layer.getShape() == PoleLayer.Shape.CROSSARM)
            .mapToDouble(PoleLayer::getCrossarmLength)
            .max()
            .orElse(1.0);
        return new DesignMetrics(
            design.totalHeight(),
            width,
            1.0,
            (int) design.getLayers().stream().filter(l -> l.getShape() == PoleLayer.Shape.CROSSARM).count(),
            countEnabledAttachments(design));
    }

    static PoleDesign resolveCatalogDesign(String designId) {
        PoleDesign design = PoleDesignCatalog.findBuiltin(designId);
        if (design == null) {
            design = resolver().find(designId);
        }
        return design;
    }

    private static boolean hasGeometry(PoleDesign design) {
        return design.hasTowerStructure() || !design.getLayers().isEmpty();
    }

    private static int countEnabledAttachments(PoleDesign design) {
        return (int) design.getAttachments().stream().filter(ConductorAttachment::isEnabled).count();
    }

    private static void assertNoAirFallback(String label, PowerLineGenerationResult result) {
        for (BlockRecord record : result.placementRecords.values()) {
            assertNotNull(record.newBlockId, label + " block id");
            assertFalse(record.newBlockId.isBlank(), label + " blank block id");
            assertFalse("minecraft:air".equals(record.newBlockId), label + " air fallback at " + record.pos);
            assertTrue(record.newBlockId.contains(":"), label + " invalid block id " + record.newBlockId);
        }
    }

    private static void assertAttachmentsPresent(
            String label,
            PowerLineGenerationResult result,
            PoleDesign design) {
        if (design != null && countEnabledAttachments(design) > 0) {
            boolean anyPlacementHasAttachments = result.polePlacements.stream()
                .anyMatch(placement -> placement.attachments() != null && !placement.attachments().isEmpty());
            assertTrue(anyPlacementHasAttachments, label + " should resolve conductor attachments");
        }
    }

    private static void assertInsulatorsWhenRequired(
            String label,
            PowerLineGenerationResult result,
            PoleDesign design) {
        if (design == null) {
            return;
        }
        boolean needsInsulators = design.getAttachments().stream()
            .anyMatch(attachment -> attachment.isEnabled() && attachment.getInsulatorLength() > 0);
        if (!needsInsulators) {
            return;
        }
        boolean hasResolvedInsulators = result.polePlacements.stream()
            .flatMap(placement -> placement.attachments().stream())
            .anyMatch(attachment -> attachment.insulatorLength() > 0);
        assertTrue(hasResolvedInsulators, label + " should resolve insulator-bearing attachments");
    }

    private static void assertEquals(String context, Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, context);
    }

    private static void assertEqualsClose(String context, double expected, double actual, double tolerance) {
        org.junit.jupiter.api.Assertions.assertEquals(
            expected,
            actual,
            tolerance,
            context);
    }

    private static PoleDesignResolver resolver() {
        return new PoleDesignResolver(new PowerLineDesignProject());
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }

    private static ICoordinateService identityCoordinates() {
        return IdentityCoordinateService.INSTANCE;
    }

    private static IBlockProjectionService projection() {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }
}
