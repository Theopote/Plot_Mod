package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;

/**
 * 立面参数：默认窗型 + 分墙段覆盖 + 显式开洞列表。
 */
public final class FacadeSpec {
    private final WindowPatternSpec defaultWindowPattern;
    private final List<WallFacadeSpec> wallFacades;
    private final List<OpeningSpec> openings;

    public FacadeSpec(
            WindowPatternSpec defaultWindowPattern,
            List<WallFacadeSpec> wallFacades,
            List<OpeningSpec> openings) {
        this.defaultWindowPattern = defaultWindowPattern != null
            ? defaultWindowPattern
            : new WindowPatternSpec(4, 1, 2, 1);
        this.wallFacades = wallFacades != null
            ? List.copyOf(wallFacades)
            : List.of();
        this.openings = openings != null
            ? List.copyOf(openings)
            : List.of();
    }

    public static FacadeSpec from(BuildingFootprint footprint) {
        return new FacadeSpec(
            WindowPatternSpec.from(footprint),
            footprint.getWallFacades(),
            footprint.getOpenings()
        );
    }

    public WindowPatternSpec defaultWindowPattern() {
        return defaultWindowPattern;
    }

    public List<WallFacadeSpec> wallFacades() {
        return wallFacades;
    }

    public List<OpeningSpec> openings() {
        return openings;
    }

    /** 显式门洞（{@link OpeningKind#DOOR}）。 */
    public List<OpeningSpec> doorOpenings() {
        return openings.stream()
            .filter(opening -> opening.kind() == OpeningKind.DOOR)
            .toList();
    }

    /** @deprecated 请使用 {@link #doorOpenings()}。 */
    @Deprecated
    public List<DoorOpeningSpec> doors() {
        List<DoorOpeningSpec> doors = new ArrayList<>();
        for (OpeningSpec opening : openings) {
            if (opening.kind() == OpeningKind.DOOR) {
                doors.add(opening.toDoorOpeningSpec());
            }
        }
        return List.copyOf(doors);
    }

    public boolean hasCustomWallFacades() {
        return !wallFacades.isEmpty();
    }

    public WindowPatternSpec windowPatternForSegment(int segmentIndex, int segmentCount) {
        if (segmentCount <= 0) {
            return defaultWindowPattern;
        }
        int index = Math.floorMod(segmentIndex, segmentCount);
        for (int i = wallFacades.size() - 1; i >= 0; i--) {
            WallFacadeSpec facade = wallFacades.get(i);
            if (facade.wallSegmentIndex() == index) {
                return facade.windowPattern();
            }
        }
        return defaultWindowPattern;
    }
}
