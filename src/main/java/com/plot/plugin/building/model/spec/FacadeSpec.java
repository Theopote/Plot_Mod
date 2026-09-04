package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.List;

/**
 * 立面参数：默认窗型 + 分墙段覆盖 + 显式开洞列表。
 * <p>
 * 边索引作用域默认 {@link FacadeEdgeScope#BASE_FOOTPRINT}：索引相对基础 footprint，
 * FloorPlate 拓扑变化时由 {@link com.plot.plugin.building.generation.facade.FacadeEdgeResolver}
 * 按外法向继承到当前层边。
 */
public final class FacadeSpec {
    private final WindowPatternSpec defaultWindowPattern;
    private final List<WallFacadeSpec> wallFacades;
    private final List<OpeningSpec> openings;
    private final FacadeEdgeScope edgeScope;

    public FacadeSpec(
            WindowPatternSpec defaultWindowPattern,
            List<WallFacadeSpec> wallFacades,
            List<OpeningSpec> openings) {
        this(defaultWindowPattern, wallFacades, openings, FacadeEdgeScope.BASE_FOOTPRINT);
    }

    public FacadeSpec(
            WindowPatternSpec defaultWindowPattern,
            List<WallFacadeSpec> wallFacades,
            List<OpeningSpec> openings,
            FacadeEdgeScope edgeScope) {
        this.defaultWindowPattern = defaultWindowPattern != null
            ? defaultWindowPattern
            : new WindowPatternSpec(4, 1, 2, 1);
        this.wallFacades = wallFacades != null
            ? List.copyOf(wallFacades)
            : List.of();
        this.openings = openings != null
            ? List.copyOf(openings)
            : List.of();
        this.edgeScope = edgeScope != null ? edgeScope : FacadeEdgeScope.BASE_FOOTPRINT;
    }

    public static FacadeSpec from(BuildingFootprint footprint) {
        return new FacadeSpec(
            WindowPatternSpec.from(footprint),
            footprint.getWallFacades(),
            footprint.getOpenings(),
            footprint.getFacadeEdgeScope()
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

    public FacadeEdgeScope edgeScope() {
        return edgeScope;
    }

    /** 显式门洞（{@link OpeningKind#DOOR}）。 */
    public List<OpeningSpec> doorOpenings() {
        return openings.stream()
            .filter(opening -> opening.kind() == OpeningKind.DOOR)
            .toList();
    }

    public boolean hasCustomWallFacades() {
        return !wallFacades.isEmpty();
    }

    /**
     * 按<strong>作用域内</strong>的边索引查窗型（BASE_FOOTPRINT 时为 base 边索引）。
     */
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
