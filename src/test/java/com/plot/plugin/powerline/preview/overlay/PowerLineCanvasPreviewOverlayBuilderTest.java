package com.plot.plugin.powerline.preview.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PowerLineCanvasPreviewOverlayBuilderTest {

    @Test
    void buildsMarkersFromGenerationResultSites() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(line);
        PowerPoleSite start = new PowerPoleSite(new Vec2d(0, 0));
        start.setStationing(0.0);
        start.setRole(TowerRole.TERMINAL);
        PowerPoleSite mid = new PowerPoleSite(new Vec2d(20, 0));
        mid.setStationing(20.0);
        mid.setRole(TowerRole.SUSPENSION);
        PowerPoleSite end = new PowerPoleSite(new Vec2d(40, 0));
        end.setStationing(40.0);
        end.setRole(TowerRole.TERMINAL);
        result.poleSites.addAll(List.of(start, mid, end));
        result.polePlacements.add(placement(start, TowerRole.TERMINAL));
        result.polePlacements.add(placement(mid, TowerRole.SUSPENSION));
        result.polePlacements.add(placement(end, TowerRole.TERMINAL));

        line.getDerivedLayout().addAutoLayoutConstraint(
            new PoleLayoutConstraint(20.0, "plugin.powerline.route.auto_pole.reason.terrain"));
        mid.setStationing(20.0);

        PowerLineCanvasPreviewOverlay overlay =
            PowerLineCanvasPreviewOverlayBuilder.build(result, line, null);

        assertNotNull(overlay);
        assertEquals(3, overlay.markers().size());
        assertEquals(3, overlay.spanPathPoints().size());
        assertEquals(2, overlay.stats().spanCount());
        assertEquals(1, overlay.markers().stream()
            .filter(marker -> marker.source() == MarkerSource.TERRAIN_AUTO_INSERT)
            .count());
        assertEquals(2, overlay.markers().stream()
            .filter(marker -> marker.source() == MarkerSource.ENDPOINT)
            .count());
    }

    private static PolePlacement placement(PowerPoleSite site, TowerRole role) {
        PoleDesign design = new PoleDesign("test-pole", "Test Pole");
        return new PolePlacement(
            site.getPlanPosition(),
            PoleFrame.fromPole(site.getPlanPosition(), new Vec2d(1, 0), 64),
            design,
            List.of(),
            70,
            false,
            role,
            design.getId(),
            site.getStationing());
    }
}
