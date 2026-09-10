package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineAttachmentResolverTest {

    @Test
    void lateralOffsetUsesRightAxis() {
        PoleDesign design = new PoleDesign("test");
        design.addAttachment(ConductorAttachmentPresets.threePhaseHorizontal(12.0).getFirst());

        ICoordinateService coordinates = identityCoordinates();
        PowerLineAttachmentResolver resolver = new PowerLineAttachmentResolver(coordinates);
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(10, 0), 64);

        ResolvedAttachment resolved = resolver.resolve(design, frame).getFirst();
        assertEquals(-3.0, resolved.planPoint().y, 1e-6);
        assertEquals(76.0, resolved.conductorWorldY(), 1e-6);
    }

    @Test
    void longitudinalOffsetUsesForwardAxis() {
        PoleDesign design = new PoleDesign("test");
        ConductorAttachment attachment = new ConductorAttachment("long", "Long");
        attachment.setVerticalOffset(10.0);
        attachment.setLongitudinalOffset(2.0);
        design.addAttachment(attachment);

        PowerLineAttachmentResolver resolver = new PowerLineAttachmentResolver(identityCoordinates());
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);

        ResolvedAttachment resolved = resolver.resolve(design, frame).getFirst();
        assertEquals(2.0, resolved.planPoint().x, 1e-6);
        assertEquals(0.0, resolved.planPoint().y, 1e-6);
    }

    @Test
    void verticalOffsetUsesGroundY() {
        PoleDesign design = new PoleDesign("test");
        ConductorAttachment attachment = new ConductorAttachment("a", "A");
        attachment.setVerticalOffset(15.0);
        design.addAttachment(attachment);

        PowerLineAttachmentResolver resolver = new PowerLineAttachmentResolver(identityCoordinates());
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 70);

        ResolvedAttachment resolved = resolver.resolve(design, frame).getFirst();
        assertEquals(85.0, resolved.conductorWorldY(), 1e-6);
    }

    private static ICoordinateService identityCoordinates() {
        return com.plot.test.world.IdentityCoordinateService.INSTANCE;
    }
}
