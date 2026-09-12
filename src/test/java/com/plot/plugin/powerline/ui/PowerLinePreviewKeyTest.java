package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePreviewKeyTest {

    @Test
    void matchesWhileParametersUnchanged() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, designs);
        assertTrue(key.matches(line, designs));
    }

    @Test
    void mismatchesWhenGenerationParameterChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, designs);

        line.setPoleHeight(20.0);
        assertFalse(key.matches(line, designs));
    }

    @Test
    void mismatchesWhenFootprintIdChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, new PowerLineDesignProject());

        PowerLineFootprint other = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        assertFalse(key.matches(other, new PowerLineDesignProject()));
    }

    @Test
    void mismatchesWhenDesignProjectChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        PowerLineDesignProject designs = new PowerLineDesignProject();
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, designs);

        PoleDesign custom = PoleDesignCatalog.simpleWoodPole().copy();
        custom.setName("Edited");
        designs.addDesign(custom);
        assertFalse(key.matches(line, designs));
    }

    @Test
    void mismatchesWhenProjectionChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineDesignProject designs = new PowerLineDesignProject();
        ICoordinateService nearView = projectionService(100f, 1f);
        ICoordinateService farView = projectionService(400f, 1f);

        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, designs, nearView);
        assertTrue(key.matches(line, designs, nearView));
        assertFalse(key.matches(line, designs, farView));
    }

    @Test
    void mismatchesWhenAttachmentChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(PoleDesignCatalog.simpleWoodPole().copy());
        PowerLinePreviewKey key = PowerLinePreviewKey.capture(line, designs);

        PoleDesign edited = designs.getDesign(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        edited.addAttachment(
            com.plot.plugin.powerline.design.ConductorAttachmentPresets.singleConductor(12.0).getFirst());
        assertFalse(key.matches(line, designs));
    }

    private static ICoordinateService projectionService(float viewDistance, float viewScale) {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos != null ? canvasPos.copy() : new Vec2d(0, 0);
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(0, viewDistance, 0, viewDistance);
            }

            @Override
            public WorldProjectionSnapshot captureProjection() {
                return new WorldProjectionSnapshot(
                    getMinecraftWorldViewBounds(),
                    viewDistance,
                    viewScale,
                    800f,
                    600f);
            }
        };
    }
}
