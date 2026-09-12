package com.plot.plugin.powerline.design.parametric;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.TowerStructureGenerator;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase C: 11 profiles × MIN/DEFAULT/MAX structural regression (pre–in-game visual QA). */
class TowerProfileVisualMatrixTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("allSamples")
    void sampleCompilesWithoutConstraintErrors(TowerProfileParameterMatrix.Sample sample) {
        TowerConstraintResult result = TowerProfileParameterMatrix.resolve(sample, null);
        assertFalse(result.hasErrors(), constraintSummary(sample, result));
        assertNotNull(TowerProfileParameterMatrix.compile(sample));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileLabels")
    void silhouetteStableAcrossRange(String profileLabel) {
        PoleDesign min = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.MIN);
        PoleDesign defaults = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.DEFAULT);
        PoleDesign max = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.MAX);

        TowerSilhouette silhouette = defaults.getTowerStructure().getSilhouette();
        assertEquals(silhouette, min.getTowerStructure().getSilhouette(), profileLabel + " MIN silhouette");
        assertEquals(silhouette, max.getTowerStructure().getSilhouette(), profileLabel + " MAX silhouette");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileLabels")
    void attachmentTopologyStableAcrossRange(String profileLabel) {
        PoleDesign min = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.MIN);
        PoleDesign defaults = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.DEFAULT);
        PoleDesign max = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.MAX);

        int expected = defaults.getAttachments().size();
        assertEquals(expected, min.getAttachments().size(), profileLabel + " MIN attachment count");
        assertEquals(expected, max.getAttachments().size(), profileLabel + " MAX attachment count");
        assertEquals(
            boundAttachmentCount(defaults),
            boundAttachmentCount(min),
            profileLabel + " MIN bound attachments");
        assertEquals(
            boundAttachmentCount(defaults),
            boundAttachmentCount(max),
            profileLabel + " MAX bound attachments");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileLabels")
    void heightOrdersFromMinToMax(String profileLabel) {
        PoleDesign min = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.MIN);
        PoleDesign defaults = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.DEFAULT);
        PoleDesign max = compileKind(profileLabel, TowerProfileParameterMatrix.SampleKind.MAX);

        double minTop = min.getTowerStructure().maxHeight();
        double defaultTop = defaults.getTowerStructure().maxHeight();
        double maxTop = max.getTowerStructure().maxHeight();
        assertTrue(minTop <= defaultTop + 0.5, profileLabel + " MIN height <= DEFAULT");
        assertTrue(defaultTop <= maxTop + 0.5, profileLabel + " DEFAULT height <= MAX");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allSamples")
    void sampleGeneratesStructuralBlocks(TowerProfileParameterMatrix.Sample sample) {
        PoleDesign design = TowerProfileParameterMatrix.compile(sample);
        PowerLineGenerationResult result = generateStructure(design.getTowerStructure());
        assertTrue(result.structureBlockCount > 0, sample.displayName() + " produced no structure blocks");
    }

    static Stream<TowerProfileParameterMatrix.Sample> allSamples() {
        return TowerProfileParameterMatrix.allSamples().stream();
    }

    static Stream<String> profileLabels() {
        return TowerProfileParameterMatrix.profiles().stream().map(TowerProfileParameterMatrix.ProfileEntry::label);
    }

    private static PoleDesign compileKind(String profileLabel, TowerProfileParameterMatrix.SampleKind kind) {
        return TowerProfileParameterMatrix.allSamples().stream()
            .filter(sample -> sample.label().equals(profileLabel) && sample.kind() == kind)
            .findFirst()
            .map(TowerProfileParameterMatrix::compile)
            .orElseThrow();
    }

    private static long boundAttachmentCount(PoleDesign design) {
        return design.getAttachments().stream().filter(ConductorAttachment::isBound).count();
    }

    private static String constraintSummary(
            TowerProfileParameterMatrix.Sample sample,
            TowerConstraintResult result) {
        if (result == null || !result.hasErrors()) {
            return sample.displayName();
        }
        return sample.displayName() + ": "
            + result.issues().stream()
                .filter(issue -> issue.severity() == ConstraintSeverity.ERROR)
                .map(ConstraintIssue::code)
                .reduce((a, b) -> a + ", " + b)
                .orElse("UNKNOWN");
    }

    private static PowerLineGenerationResult generateStructure(TowerStructureDesign structure) {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
        TowerStructureGenerator.generate(
            structure,
            frame,
            footprint,
            result,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            projection(),
            flatTerrain(64));
        return result;
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

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int groundY, int worldZ) {
                return false;
            }
        };
    }
}
