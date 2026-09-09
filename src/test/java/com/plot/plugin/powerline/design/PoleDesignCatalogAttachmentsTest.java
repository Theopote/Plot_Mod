package com.plot.plugin.powerline.design;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleDesignCatalogAttachmentsTest {

    @Test
    void allBuiltinDesignsHaveEnabledAttachments() {
        for (PoleDesign design : PoleDesignCatalog.defaultDesigns()) {
            assertTrue(
                design.hasEnabledAttachments(),
                () -> design.getId() + " missing conductor attachments");
        }
    }
}
