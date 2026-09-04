package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑附属构件：女儿墙、雨篷、阳台等（Phase 9）。
 */
public final class AccessorySpec {
    private final ParapetSpec parapet;
    private final List<CanopySpec> canopies;
    private final List<BalconySpec> balconies;

    public AccessorySpec(ParapetSpec parapet, List<CanopySpec> canopies, List<BalconySpec> balconies) {
        this.parapet = parapet != null ? parapet : ParapetSpec.disabled();
        this.canopies = canopies != null ? List.copyOf(canopies) : List.of();
        this.balconies = balconies != null ? List.copyOf(balconies) : List.of();
    }

    public static AccessorySpec none() {
        return new AccessorySpec(ParapetSpec.disabled(), List.of(), List.of());
    }

    public static AccessorySpec from(BuildingFootprint footprint) {
        if (footprint == null) {
            return none();
        }
        List<CanopySpec> canopies = new ArrayList<>();
        for (BuildingFootprint.Canopy canopy : footprint.getCanopies()) {
            canopies.add(CanopySpec.from(canopy));
        }
        List<BalconySpec> balconies = new ArrayList<>();
        for (BuildingFootprint.Balcony balcony : footprint.getBalconies()) {
            balconies.add(BalconySpec.from(balcony));
        }
        return new AccessorySpec(ParapetSpec.from(footprint), canopies, balconies);
    }

    public ParapetSpec parapet() {
        return parapet;
    }

    public List<CanopySpec> canopies() {
        return canopies;
    }

    public List<BalconySpec> balconies() {
        return balconies;
    }

    public boolean hasAnyEnabled() {
        return parapet.enabled() || !canopies.isEmpty() || !balconies.isEmpty();
    }
}
