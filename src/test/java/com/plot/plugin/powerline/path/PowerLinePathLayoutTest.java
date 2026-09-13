package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PowerLinePathLayoutTest {

    @Test
    void commitLayoutRejectsInsufficientSitesBeforeMutatingFootprint() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(40, 0));
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineSourcePath sourcePath = PowerLinePathAdapters.from(line);
        PowerLineSourceDescriptor descriptor = PowerLineSourceDescriptor.capture(line);

        assertThrows(
            ClosedLoopLayoutException.class,
            () -> PowerLinePathLayout.commitLayout(
                footprint,
                descriptor,
                sourcePath,
                List.of()));

        assertNull(footprint.getSourceDescriptor());
    }

    @Test
    void commitLayoutPreservesExistingDescriptorWhenNewSitesAreInsufficient() {
        LineShape original = new LineShape(new Vec2d(0, 0), new Vec2d(40, 0));
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(original, com.plot.test.world.IdentityCoordinateService.INSTANCE);
        int fingerprint = footprint.getSourceDescriptor().fingerprint();
        int towerCount = footprint.getPathPoints().size();

        LineShape replacement = new LineShape(new Vec2d(80, 0), new Vec2d(120, 0));
        PowerLineSourceDescriptor replacementDescriptor = PowerLineSourceDescriptor.capture(replacement);

        assertThrows(
            ClosedLoopLayoutException.class,
            () -> PowerLinePathLayout.commitLayout(
                footprint,
                replacementDescriptor,
                PowerLinePathAdapters.from(replacement),
                List.of()));

        assertEquals(fingerprint, footprint.getSourceDescriptor().fingerprint());
        assertEquals(towerCount, footprint.getPathPoints().size());
        assertEquals(original.getId(), footprint.getSourceShapeId());
    }
}
