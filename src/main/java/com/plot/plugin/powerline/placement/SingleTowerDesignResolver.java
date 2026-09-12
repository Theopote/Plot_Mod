package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;

/** 单塔放置：按选定 {@link TowerRole} 从线路样式解析杆塔设计。 */
public final class SingleTowerDesignResolver {
    private SingleTowerDesignResolver() {
    }

    public static PoleDesign resolve(
            PowerLineFootprint line,
            TowerRole role,
            PoleDesignResolver designResolver) {
        if (line == null) {
            return null;
        }
        PoleDesignResolver resolver = designResolver != null
            ? designResolver
            : new PoleDesignResolver(new com.plot.plugin.powerline.model.PowerLineDesignProject());
        PowerPoleSite site = new PowerPoleSite(new Vec2d(0, 0));
        site.setRole(role != null ? role : TowerRole.SUSPENSION);
        PoleDesignAssignmentResolver assignment = new PoleDesignAssignmentResolver(
            resolver,
            new TowerFamilyResolver());
        PoleDesign design = assignment.resolve(site, line).design();
        if (design != null) {
            return design;
        }
        if (line.hasPoleDesign()) {
            return resolver.find(line.getPoleDesignId());
        }
        return null;
    }
}
