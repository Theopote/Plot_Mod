package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.model.PowerLineDesignProject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 合并内置预设与用户自定义杆塔设计的查找表。
 */
public final class PoleDesignResolver {
    private final PowerLineDesignProject userDesigns;

    public PoleDesignResolver(PowerLineDesignProject userDesigns) {
        this.userDesigns = userDesigns != null ? userDesigns : new PowerLineDesignProject();
    }

    public PoleDesign find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        PoleDesign user = userDesigns.getDesign(id);
        if (user != null) {
            return user;
        }
        PoleDesign builtin = PoleDesignCatalog.findBuiltin(id);
        if (builtin != null) {
            return builtin;
        }
        return TowerFamilyCatalog.familyDesigns().stream()
            .filter(design -> design.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    public List<PoleDesign> listAll() {
        List<PoleDesign> designs = new ArrayList<>(PoleDesignCatalog.defaultDesigns());
        designs.addAll(TowerFamilyCatalog.familyDesigns());
        designs.addAll(userDesigns.getDesigns().values());
        designs.sort(Comparator.comparing(PoleDesign::getName, String.CASE_INSENSITIVE_ORDER));
        return designs;
    }

    public PowerLineDesignProject userDesigns() {
        return userDesigns;
    }
}
