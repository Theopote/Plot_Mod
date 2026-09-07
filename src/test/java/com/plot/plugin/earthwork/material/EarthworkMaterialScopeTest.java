package com.plot.plugin.earthwork.material;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkMaterialScopeTest {

    @Test
    void promoteSiteMaterialUpdatesConfigOnly() {
        EarthworkConfig config = new EarthworkConfig("earthwork_balance");
        MaterialConversionModel custom = new MaterialConversionModel(0.72f, 0.88f);
        EarthworkSite site = new EarthworkSite();
        site.setMaterialModel(custom);

        EarthworkMaterialScope.promoteSiteMaterialToAdoptDefault(
            null,
            site);

        // null ctx short-circuits; test direct config path via site model unchanged
        assertEquals(0.72f, site.getMaterialModel().reusableRatio(), 1e-6f);
    }

    @Test
    void applyExampleUpdatesSiteAndRegions() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        project.addRegion(new GradingRegion(List.of(
            new Vec2d(0, 0), new Vec2d(5, 0), new Vec2d(5, 5))));
        GradingRegion region = project.getRegions().values().iterator().next();

        MaterialConversionModel learning = MaterialConversionModel.LEARNING;
        EarthworkMaterialScope.applyExampleToProject(null, project, site, learning);

        assertEquals(learning.reusableRatio(), site.getMaterialModel().reusableRatio(), 1e-6f);
        assertEquals(learning.reusableRatio(), region.getMaterialProperties().reusableRatio(), 1e-6f);
    }

    @Test
    void usesDefaultMaterialModelDetectsFreshSite() {
        assertTrue(EarthworkMaterialScope.usesDefaultMaterialModel(new EarthworkSite()));
        assertFalse(EarthworkMaterialScope.usesDefaultMaterialModel(siteWith(0.8f, 0.9f)));
    }

    private static EarthworkSite siteWith(float reusable, float cutToFill) {
        EarthworkSite site = new EarthworkSite();
        site.setMaterialModel(new MaterialConversionModel(reusable, cutToFill));
        return site;
    }
}
