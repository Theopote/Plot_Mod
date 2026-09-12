package com.plot.plugin.powerline.design.parametric;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerParametricBuildPolicyTest {

    @Test
    void validParametricLineIsNotBlocked() {
        PowerLineFootprint line = parametricClassicLine();
        assertFalse(TowerParametricBuildPolicy.hasBlockingIssues(line, null));
    }

    @Test
    void impossibleHeightBlocksResolvedDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleDesignId(PoleDesignCatalog.LATTICE_STEEL_TOWER_ID);
        line.setParametricTowerConfig(TowerGeneratorConfig.parametricClassic(
            new TowerParameterSet(52.0, 13.0, 24.0, 1.0, 1.0, null, StructureDensity.MEDIUM)));

        TowerBuildEnvelope envelope = new TowerBuildEnvelope(-64, 320, 280.0, 4);
        PoleDesign base = PoleDesignCatalog.findBuiltin(line.getPoleDesignId());
        PoleDesign design = ParametricStyleTowerApplicator.apply(
            base,
            line.getParametricTowerConfig(),
            envelope);

        assertNotNull(design);
        assertTrue(TowerParametricEditor.hasBlockingErrors(design, envelope));
    }

    @Test
    void nonParametricLineIsNotBlocked() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setPoleDesignId(PoleDesignCatalog.LATTICE_STEEL_TOWER_ID);
        assertFalse(TowerParametricBuildPolicy.hasBlockingIssues(line, null));
    }

    private static PowerLineFootprint parametricClassicLine() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.classicLattice().apply(line);
        return line;
    }
}
