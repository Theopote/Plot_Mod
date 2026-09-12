package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.model.PowerLineDesignProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PoleDesignResolverTest {

    @Test
    void prepareEditableCopyForksBuiltinPreset() {
        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());
        PoleDesign editable = resolver.prepareEditableCopy(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        assertNotNull(editable);
        assertNotEquals(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID, editable.getId());
        assertEquals(PoleDesignCatalog.simpleWoodPole().getName(), editable.getName());
    }

    @Test
    void prepareEditableCopyClonesUserDesign() {
        PowerLineDesignProject project = new PowerLineDesignProject();
        PoleDesign userDesign = new PoleDesign("user-tower");
        userDesign.addLayer(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            6,
            com.plot.core.material.MaterialMix.single("minecraft:oak_fence")));
        project.addDesign(userDesign);

        PoleDesignResolver resolver = new PoleDesignResolver(project);
        PoleDesign editable = resolver.prepareEditableCopy(userDesign.getId());
        assertEquals(userDesign.getId(), editable.getId());
        assertNotSame(userDesign, editable);
    }

    private static void assertNotSame(PoleDesign left, PoleDesign right) {
        org.junit.jupiter.api.Assertions.assertNotSame(left, right);
    }
}
