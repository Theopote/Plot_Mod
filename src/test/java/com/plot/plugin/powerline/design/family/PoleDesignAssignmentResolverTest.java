package com.plot.plugin.powerline.design.family;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleDesignAssignmentResolverTest {
    private final PoleDesignResolver designResolver = new PoleDesignResolver(new PowerLineDesignProject());
    private final PoleDesignAssignmentResolver resolver =
        new PoleDesignAssignmentResolver(designResolver, new TowerFamilyResolver());

    @Test
    void explicitPoleDesignOverrideWins() {
        PowerLineFootprint footprint = lineWithFamily();
        PowerPoleSite site = site(TowerRole.SUSPENSION);
        site.setPoleDesignOverrideId(TowerFamilyDesignPresets.LATTICE_DEAD_END_ID);

        PoleDesignAssignmentResolver.AssignmentResult result = resolver.resolve(site, footprint);
        assertEquals(TowerFamilyDesignPresets.LATTICE_DEAD_END_ID, result.resolvedDesignId());
        assertNotNull(result.design());
    }

    @Test
    void towerFamilyWinsOverLineFallback() {
        PowerLineFootprint footprint = lineWithFamily();
        footprint.setPoleDesignId(TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID);
        PowerPoleSite site = site(TowerRole.ANGLE);

        PoleDesignAssignmentResolver.AssignmentResult result = resolver.resolve(site, footprint);
        assertEquals(TowerFamilyDesignPresets.LATTICE_ANGLE_ID, result.resolvedDesignId());
    }

    @Test
    void lineFallbackUsedWithoutFamily() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        footprint.setPoleDesignId(TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID);
        PowerPoleSite site = site(TowerRole.ANGLE);

        PoleDesignAssignmentResolver.AssignmentResult result = resolver.resolve(site, footprint);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID, result.resolvedDesignId());
    }

    @Test
    void defaultPoleUsedWhenNothingConfigured() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerPoleSite site = site(TowerRole.SUSPENSION);

        PoleDesignAssignmentResolver.AssignmentResult result = resolver.resolve(site, footprint);
        assertNull(result.design());
        assertNull(result.resolvedDesignId());
        assertTrue(result.warnings().isEmpty());
    }

    private static PowerLineFootprint lineWithFamily() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        footprint.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        return footprint;
    }

    private static PowerPoleSite site(TowerRole role) {
        PowerPoleSite site = new PowerPoleSite(new Vec2d(5, 0));
        site.setRole(role);
        site.setRoleAutoAssigned(false);
        return site;
    }
}
