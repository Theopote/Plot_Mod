package com.plot.plugin.powerline.design;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentRoleTest {

    @Test
    void parseRoleAcceptsTopWire() {
        assertEquals(AttachmentRole.TOP_WIRE, AttachmentRole.parseRole("TOP_WIRE"));
    }

    @Test
    void parseRoleFallsBackForUnknownValues() {
        assertEquals(AttachmentRole.AUXILIARY, AttachmentRole.parseRole("NOT_A_REAL_ROLE"));
        assertEquals(AttachmentRole.AUXILIARY, AttachmentRole.parseRole("GROUND_WIRE"));
    }
}
