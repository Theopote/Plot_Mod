package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;

/**
 * 立面参数（Phase 2：单一全局立面；Phase 5 将拆分为分立面 Facade）。
 */
public final class FacadeSpec {
    private final WindowPatternSpec defaultWindowPattern;
    private final List<DoorOpeningSpec> doors;

    public FacadeSpec(WindowPatternSpec defaultWindowPattern, List<DoorOpeningSpec> doors) {
        this.defaultWindowPattern = defaultWindowPattern != null
            ? defaultWindowPattern
            : new WindowPatternSpec(4, 1, 2, 1);
        this.doors = doors != null
            ? List.copyOf(doors)
            : List.of();
    }

    public static FacadeSpec from(BuildingFootprint footprint) {
        List<DoorOpeningSpec> doorSpecs = new ArrayList<>();
        for (BuildingFootprint.DoorOpening door : footprint.getDoors()) {
            doorSpecs.add(DoorOpeningSpec.from(door));
        }
        return new FacadeSpec(WindowPatternSpec.from(footprint), doorSpecs);
    }

    public WindowPatternSpec defaultWindowPattern() {
        return defaultWindowPattern;
    }

    public List<DoorOpeningSpec> doors() {
        return doors;
    }
}
