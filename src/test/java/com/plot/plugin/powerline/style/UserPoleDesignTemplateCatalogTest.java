package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPoleDesignTemplateCatalogTest {

    @Test
    void listTemplatesExcludesLineInstanceDesigns() {
        PowerLineDesignProject project = new PowerLineDesignProject();
        PoleDesign template = new PoleDesign("Custom Tower");
        project.addDesign(template);
        PoleDesign instance = new PoleDesign(
            LinePoleDesignOverrides.ID_PREFIX + "line-1",
            "Line Fork");
        project.addDesign(instance);

        List<PoleDesign> templates = UserPoleDesignTemplateCatalog.listTemplates(project);
        assertEquals(1, templates.size());
        assertEquals(template.getId(), templates.getFirst().getId());
    }

    @Test
    void selectUserTemplateSetsPresetAndDesignId() {
        PowerLineDesignProject project = new PowerLineDesignProject();
        PoleDesign template = new PoleDesign("My Template");
        project.addDesign(template);

        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStyleEditor.selectUserTemplate(line, template, project);

        assertEquals(UserPoleDesignTemplateCatalog.presetIdFor(template.getId()), line.getStylePresetId());
        assertEquals(template.getId(), line.getPoleDesignId());
        assertNotNull(PowerLineStyleEditor.resolveBasePreset(line, project));
        assertTrue(UserPoleDesignTemplateCatalog.isTemplateSelected(line, template));
    }

    @Test
    void resolvePresetReturnsNullForBuiltinPresetId() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.classicWood().apply(line);
        assertNotNull(PowerLineStyleEditor.basePreset(line));
        assertEquals(
            PowerLineStyleEditor.basePreset(line),
            PowerLineStyleEditor.resolveBasePreset(line, new PowerLineDesignProject()));
    }

    @Test
    void presetIdRoundTrip() {
        String designId = "abc-123";
        String presetId = UserPoleDesignTemplateCatalog.presetIdFor(designId);
        assertTrue(UserPoleDesignTemplateCatalog.isUserTemplatePresetId(presetId));
        assertEquals(designId, UserPoleDesignTemplateCatalog.designIdFromPresetId(presetId));
        assertFalse(UserPoleDesignTemplateCatalog.isUserTemplatePresetId("pack/rustic_wood"));
    }
}
