package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.parametric.StructureDensity;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineGeneratorParametricTest {

    @Test
    void parametricConstraintFailureIsDetected() {
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

        assertTrue(PowerLineGenerator.isParametricBlocked(design, envelope));
        assertTrue(TowerParametricEditor.hasBlockingErrors(design, envelope));
    }

    @Test
    void invalidPlacementSkipsSpanGeneration() {
        PolePlacement valid = new PolePlacement(
            new Vec2d(0, 0),
            PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64),
            null,
            List.of(),
            74,
            false,
            TowerRole.SUSPENSION,
            null,
            0.0,
            true);
        PolePlacement invalid = new PolePlacement(
            new Vec2d(40, 0),
            PoleFrame.fromPole(new Vec2d(40, 0), new Vec2d(1, 0), 64),
            null,
            List.of(),
            74,
            false,
            TowerRole.SUSPENSION,
            null,
            40.0,
            false);

        assertFalse(PowerLineGenerator.isSpanGenerable(List.of(valid, invalid), 0));
        assertFalse(PowerLineGenerator.isSpanGenerable(List.of(invalid, valid), 0));
        assertTrue(PowerLineGenerator.isSpanGenerable(List.of(valid, valid), 0));
    }

    @Test
    void invalidPlacementCarriesNoAttachments() {
        PolePlacement invalid = PowerLineGenerator.invalidPolePlacement(
            new Vec2d(10, 0),
            PoleFrame.fromPole(new Vec2d(10, 0), new Vec2d(1, 0), 64),
            PoleDesignCatalog.findBuiltin(PoleDesignCatalog.LATTICE_STEEL_TOWER_ID),
            64,
            new com.plot.plugin.powerline.model.PowerPoleSite(new Vec2d(10, 0)),
            "design-id");

        assertFalse(invalid.isValid());
        assertTrue(invalid.attachments().isEmpty());
        assertFalse(invalid.usesAttachmentConductors());
    }
}
