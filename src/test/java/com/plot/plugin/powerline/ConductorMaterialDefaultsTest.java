package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConductorMaterialDefaultsTest {

    @Test
    void newLineDefaultsToIronBarsConductors() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        assertEquals("minecraft:iron_bars", line.getWireMaterial().getPrimaryMaterial());
    }

    @Test
    void newAttachmentDefaultsToChainInsulator() {
        ConductorAttachment attachment = new ConductorAttachment("a", "A");
        assertEquals("minecraft:chain", attachment.getInsulatorMaterial().getPrimaryMaterial());
    }
}
