package com.plot.plugin.powerline.design.parametric;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.TerrainTestFixtures;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerParametricSitePlacementTest {

    @Test
    void adaptiveHeightUsesMoreHeadroomOnLowGroundSite() {
        TowerBuildEnvelope mountain = TowerBuildEnvelope.forSite(-64, 320, 200, 4);
        TowerBuildEnvelope valley = TowerBuildEnvelope.forSite(-64, 320, 64, 4);
        TowerLineBuildEnvelope line = TowerLineBuildEnvelope.fromSiteEnvelopes(List.of(mountain, valley));

        TowerParameterProfile profile = TowerParameterProfiles.classicDoubleArm();
        TowerParameterSet template = TowerParameterSet.classicDefaults();

        double mountainHeight = TowerParametricSitePlacement.resolveAdaptiveHeight(
            profile, template, mountain, line);
        double valleyHeight = TowerParametricSitePlacement.resolveAdaptiveHeight(
            profile, template, valley, line);

        assertTrue(valleyHeight > mountainHeight);
    }

    @Test
    void perSitePlacementProducesDifferentHeightsAcrossLine() {
        TowerBuildEnvelope mountain = TowerBuildEnvelope.forSite(-64, 320, 200, 4);
        TowerBuildEnvelope valley = TowerBuildEnvelope.forSite(-64, 320, 64, 4);
        TowerLineBuildEnvelope line = TowerLineBuildEnvelope.fromSiteEnvelopes(List.of(mountain, valley));

        PoleDesign design = new PoleDesign("adaptive", "Adaptive");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());

        double mountainHeight = TowerParametricSitePlacement.prepareForSite(
            design,
            line,
            0).design().getGeneratorConfig().parameters().height();
        double valleyHeight = TowerParametricSitePlacement.prepareForSite(
            design,
            line,
            1).design().getGeneratorConfig().parameters().height();

        assertTrue(valleyHeight > mountainHeight);
    }

    @Test
    void lineWideModeStillUsesMinimumEnvelope() {
        TowerBuildEnvelope mountain = TowerBuildEnvelope.forSite(-64, 320, 200, 4);
        TowerBuildEnvelope valley = TowerBuildEnvelope.forSite(-64, 320, 64, 4);
        TowerLineBuildEnvelope line = TowerLineBuildEnvelope.fromSiteEnvelopes(List.of(mountain, valley));

        PoleDesign design = new PoleDesign("line", "Line");
        TowerParametricEditor.enableParametricClassic(
            design,
            new TowerParameterSet(52.0, 13.0, 24.0, 1.0, 1.0, null, StructureDensity.MEDIUM));

        double lineHeight = TowerParametricLinePlacement.prepare(design, line)
            .design()
            .getGeneratorConfig()
            .parameters()
            .height();
        double mountainHeight = TowerParametricSitePlacement.prepareForSite(
            design.copy(),
            line,
            0).design().getGeneratorConfig().parameters().height();
        double valleyHeight = TowerParametricSitePlacement.prepareForSite(
            design.copy(),
            line,
            1).design().getGeneratorConfig().parameters().height();

        assertEquals(lineHeight, 52.0, 0.01);
        assertTrue(mountainHeight < lineHeight);
        assertTrue(valleyHeight >= lineHeight);
    }

    @Test
    void smartTowersPresetKeepsExperimentalPerSiteHeightDisabled() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(120, 0)));
        PowerLineStylePresetCatalog.smartTowers().apply(line);

        assertFalse(line.isPerSiteParametricHeightEnabled());
        assertTrue(line.hasParametricTowerConfig());
        assertEquals(TowerFamily.GRADED_LATTICE_3_PHASE_ID, line.getTowerFamilyId());
    }

    @Test
    void experimentalPerSiteHeightProducesDifferentTowerHeightsWhenEnabled() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(120, 0)));
        PowerLineStylePresetCatalog.smartTowers().apply(line);
        line.setPerSiteParametricHeightEnabled(true);
        line.setMaxPoleSpacing(150);

        TerrainSampler terrain = varyingTerrain(200, 64);
        PowerLineGenerationResult result = TerrainTestFixtures.generate(line, terrain);

        assertEquals(2, result.poleCount);
        double mountainHeight = result.polePlacements.get(0).design().getTowerStructure().maxHeight();
        double valleyHeight = result.polePlacements.get(1).design().getTowerStructure().maxHeight();
        assertTrue(valleyHeight > mountainHeight + 1.0);
    }

    private static TerrainSampler varyingTerrain(int mountainY, int valleyY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint.x < 60 ? mountainY : valleyY;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }
}
