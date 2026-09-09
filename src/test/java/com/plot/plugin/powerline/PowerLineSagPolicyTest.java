package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfileCatalog;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineSagPolicyTest {

    private static PowerLineFootprint sampleLine() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
    }

    @Test
    void lineCapTakesPrecedenceOverProfile() {
        PowerLineFootprint line = sampleLine();
        line.setMaxSagDepth(8.0);
        var profile = EngineeringRuleProfileCatalog.genericPlanning();
        profile.getSag().setMaxSagDepth(16.0);
        assertEquals(8.0, PowerLineSagPolicy.resolveMaxSagDepth(line, profile));
    }

    @Test
    void fallsBackToProfileWhenLineUnlimited() {
        PowerLineFootprint line = sampleLine();
        line.setMaxSagDepth(0.0);
        var profile = EngineeringRuleProfileCatalog.genericPlanning();
        profile.getSag().setMaxSagDepth(10.0);
        assertEquals(10.0, PowerLineSagPolicy.resolveMaxSagDepth(line, profile));
    }

    @Test
    void unlimitedWhenNeitherCaps() {
        PowerLineFootprint line = sampleLine();
        line.setMaxSagDepth(0.0);
        var profile = EngineeringRuleProfileCatalog.genericPlanning();
        profile.getSag().setMaxSagDepth(0.0);
        assertEquals(0.0, PowerLineSagPolicy.resolveMaxSagDepth(line, profile));
    }
}
