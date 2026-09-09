package com.plot.plugin.powerline.design;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentRoleTest {

    @Test
    void parseRoleAcceptsTopWire() {
        assertEquals(AttachmentRole.TOP_WIRE, AttachmentRole.parseRole("TOP_WIRE"));
    }

    @Test
    void parseRoleMigratesLegacyGroundWire() {
        assertEquals(AttachmentRole.TOP_WIRE, AttachmentRole.parseRole("GROUND_WIRE"));
    }
}
