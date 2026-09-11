package com.plot.plugin.powerline.design.family;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 查找内置与用户自定义塔型族。 */
public final class TowerFamilyResolver {
    public TowerFamily find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return TowerFamilyCatalog.findBuiltin(id);
    }

    public List<TowerFamily> listAll() {
        List<TowerFamily> families = new ArrayList<>(TowerFamilyCatalog.defaultFamilies());
        families.sort(Comparator.comparing(TowerFamily::getName, String.CASE_INSENSITIVE_ORDER));
        return families;
    }
}
