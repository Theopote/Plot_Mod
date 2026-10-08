package com.plot.plugin.road.tunnel;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TunnelStyleTest {

    @Test
    void clampEnforcesBounds() {
        TunnelStyle style = new TunnelStyle();
        style.setClearHeight(99);
        style.setSideClearance(-1);
        style.setLiningThickness(0);
        style.setLightSpacing(1);
        style.setAccentSpacing(99);

        style.clamp();

        assertEquals(TunnelStyle.MAX_CLEAR_HEIGHT, style.getClearHeight());
        assertEquals(TunnelStyle.MIN_SIDE_CLEARANCE, style.getSideClearance());
        assertEquals(TunnelStyle.MIN_LINING_THICKNESS, style.getLiningThickness());
        assertEquals(TunnelStyle.MIN_LIGHT_SPACING, style.getLightSpacing());
        assertEquals(TunnelStyle.MAX_ACCENT_SPACING, style.getAccentSpacing());
    }

    @Test
    void resolveUsesRoadOverrideBeforeConfig() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.getTunnelStyle().setClearHeight(5);
        Road road = new Road();
        TunnelStyle override = new TunnelStyle();
        override.setClearHeight(8);
        road.setTunnelStyle(override);

        assertEquals(8, TunnelStyle.resolve(road, config).clearHeight());
    }

    @Test
    void resolveFallsBackToConfigWhenRoadInherits() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.getTunnelStyle().setShape(TunnelShape.HORSESHOE);
        Road road = new Road();

        assertEquals(TunnelShape.HORSESHOE, TunnelStyle.resolve(road, config).shape());
        assertNull(road.getStoredTunnelStyle());
    }

    @Test
    void fieldLevelOverrideMergesWithConfigDefaults() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.getTunnelStyle().setShape(TunnelShape.ARCH);
        config.getTunnelStyle().setClearHeight(5);
        Road road = new Road();
        TunnelStyle override = new TunnelStyle();
        override.setClearHeight(8);
        road.setTunnelStyle(override);

        ResolvedTunnelStyle resolved = TunnelStyle.resolve(road, config);
        assertEquals(TunnelShape.ARCH, resolved.shape());
        assertEquals(8, resolved.clearHeight());
    }

    @Test
    void copyProducesIndependentInstance() {
        TunnelStyle original = new TunnelStyle();
        original.setClearHeight(7);
        TunnelStyle copy = original.copy();
        copy.setClearHeight(9);

        assertEquals(7, original.getClearHeight());
        assertEquals(9, copy.getClearHeight());
    }
}
