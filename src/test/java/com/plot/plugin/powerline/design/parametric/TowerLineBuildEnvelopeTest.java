package com.plot.plugin.powerline.design.parametric;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.model.PowerPoleSite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerLineBuildEnvelopeTest {

    @Test
    void limitingSiteIsMinimumAvailableHeight() {
        TowerBuildEnvelope highGround = TowerBuildEnvelope.forSite(-64, 320, 64, 4);
        TowerBuildEnvelope lowGround = TowerBuildEnvelope.forSite(-64, 320, 200, 4);

        TowerLineBuildEnvelope line = TowerLineBuildEnvelope.fromSiteEnvelopes(List.of(highGround, lowGround));

        assertEquals(1, line.limitingSiteIndex());
        assertEquals(lowGround.availableLocalHeight(), line.limitingAvailableHeight(), 0.01);
    }

    @Test
    void constraintEnvelopeUsesLineMinimum() {
        TowerBuildEnvelope highGround = TowerBuildEnvelope.forSite(-64, 320, 64, 4);
        TowerBuildEnvelope lowGround = TowerBuildEnvelope.forSite(-64, 320, 200, 4);
        TowerLineBuildEnvelope line = TowerLineBuildEnvelope.fromSiteEnvelopes(List.of(highGround, lowGround));

        assertEquals(line.limitingAvailableHeight(), line.constraintEnvelope().availableLocalHeight(), 0.01);
    }

    @Test
    void resolverPicksMountainSiteAsLimiting() {
        TerrainSampler terrain = varyingTerrain(100, 64);
        List<PowerPoleSite> sites = List.of(
            new PowerPoleSite("mountain", new Vec2d(0, 0)),
            new PowerPoleSite("valley", new Vec2d(100, 0)));

        TowerLineBuildEnvelope line = TowerBuildEnvelopeResolver.fromPoleSites(sites, terrain);

        assertEquals(0, line.limitingSiteIndex());
        assertTrue(line.limitingAvailableHeight() < line.siteEnvelopes().get(1).availableLocalHeight());
    }

    @Test
    void prepareClampsParametricHeightToLineMinimum() {
        TowerBuildEnvelope highGround = TowerBuildEnvelope.forSite(-64, 320, 64, 4);
        TowerBuildEnvelope lowGround = TowerBuildEnvelope.forSite(-64, 320, 280, 4);
        TowerLineBuildEnvelope line = TowerLineBuildEnvelope.fromSiteEnvelopes(List.of(highGround, lowGround));

        PoleDesign design = new PoleDesign("line", "Line");
        TowerParametricEditor.enableParametricClassic(
            design,
            new TowerParameterSet(52.0, 13.0, 24.0, 1.0, 1.0, StructureDensity.MEDIUM));

        TowerParametricLinePlacement.PreparationResult prepared =
            TowerParametricLinePlacement.prepare(design, line);

        double maxHeight = TowerParametricHeightLimits.maxAllowedHeight(
            TowerParameterProfiles.classicDoubleArm(),
            prepared.design().getGeneratorConfig().parameters(),
            line.constraintEnvelope());

        assertTrue(prepared.design().getGeneratorConfig().parameters().height() <= maxHeight + 1e-6);
        assertFalse(prepared.warnings().isEmpty());
        assertTrue(prepared.warnings().stream().anyMatch(w -> w.startsWith("parametric.height_clamped_for_line:")));
    }

    private static TerrainSampler varyingTerrain(int mountainY, int valleyY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint.x < 50 ? mountainY : valleyY;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }
}
