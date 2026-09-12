package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SingleTowerDesignResolverTest {

    @Test
    void resolvesRoleSpecificDesignFromTowerFamily() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setTowerFamilyId(TowerFamily.MONSTER_PYLON_ID);
        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());

        PoleDesign suspension = SingleTowerDesignResolver.resolve(line, TowerRole.SUSPENSION, resolver);
        PoleDesign angle = SingleTowerDesignResolver.resolve(line, TowerRole.ANGLE, resolver);

        assertNotNull(suspension);
        assertNotNull(angle);
        assertNotEquals(suspension.getId(), angle.getId());
    }

    @Test
    void defaultsToSuspensionWhenRoleIsNull() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setTowerFamilyId(TowerFamily.MONSTER_PYLON_ID);
        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());

        PoleDesign explicit = SingleTowerDesignResolver.resolve(line, TowerRole.SUSPENSION, resolver);
        PoleDesign defaulted = SingleTowerDesignResolver.resolve(line, null, resolver);

        assertNotNull(explicit);
        assertEquals(explicit.getId(), defaulted.getId());
    }
}
