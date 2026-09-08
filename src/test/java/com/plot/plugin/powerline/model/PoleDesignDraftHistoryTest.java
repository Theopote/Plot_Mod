package com.plot.plugin.powerline.model;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoleDesignDraftHistoryTest {

    @Test
    void undoRestoresSnapshotTakenBeforeMutation() {
        PoleDesignDraftHistory history = new PoleDesignDraftHistory();
        PoleDesign draft = new PoleDesign("Original");
        draft.getLayers().add(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            4,
            MaterialMix.single("minecraft:oak_planks")));

        history.push(draft);
        draft.setName("Edited");
        draft.getLayers().get(0).setHeight(8);

        PoleDesign restored = history.undo(draft);

        assertEquals("Original", restored.getName());
        assertEquals(4, restored.getLayers().get(0).getHeight());
    }
}
