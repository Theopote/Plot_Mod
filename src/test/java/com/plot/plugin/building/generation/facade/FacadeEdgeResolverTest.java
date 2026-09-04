package com.plot.plugin.building.generation.facade;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.spec.CardinalDirection;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FacadeEdgeResolverTest {

    /** CCW 矩形：边 0 南(+Y)、1 东(+X)、2 北(-Y)、3 西(-X) — 取决于 outwardNormal。 */
    private static final List<Vec2d> RECT = List.of(
        new Vec2d(0, 0),
        new Vec2d(10, 0),
        new Vec2d(10, 8),
        new Vec2d(0, 8)
    );

    /** 内缩矩形（同拓扑）。 */
    private static final List<Vec2d> SETBACK_RECT = List.of(
        new Vec2d(2, 2),
        new Vec2d(8, 2),
        new Vec2d(8, 6),
        new Vec2d(2, 6)
    );

    /** L 形：6 边（相对 RECT 拓扑变化）。 */
    private static final List<Vec2d> L_SHAPE = List.of(
        new Vec2d(0, 0),
        new Vec2d(10, 0),
        new Vec2d(10, 4),
        new Vec2d(4, 4),
        new Vec2d(4, 8),
        new Vec2d(0, 8)
    );

    @Test
    void sameTopologyKeepsRawIndex() {
        for (int i = 0; i < 4; i++) {
            assertEquals(i, FacadeEdgeResolver.resolveSegmentIndex(
                FacadeEdgeScope.BASE_FOOTPRINT, i, RECT, SETBACK_RECT));
        }
    }

    @Test
    void floorLocalIgnoresBaseTopology() {
        // L 有 6 边；raw index 2 直接取模，不做方向继承
        assertEquals(2, FacadeEdgeResolver.resolveSegmentIndex(
            FacadeEdgeScope.FLOOR_LOCAL, 2, RECT, L_SHAPE));
    }

    @Test
    void inheritByDirectionMapsRectEdgeToMatchingLEdge() {
        // RECT 边 0：(0,0)→(10,0)，外法向应朝南 (+Y) 或北，取决于 winding
        int mapped = FacadeEdgeResolver.inheritByDirection(0, RECT, L_SHAPE);
        assertEquals(
            FacadeEdgeResolver.directionOfSegment(RECT, 0),
            FacadeEdgeResolver.directionOfSegment(L_SHAPE, mapped)
        );

        int east = FacadeEdgeResolver.inheritByDirection(1, RECT, L_SHAPE);
        assertEquals(
            FacadeEdgeResolver.directionOfSegment(RECT, 1),
            FacadeEdgeResolver.directionOfSegment(L_SHAPE, east)
        );
    }

    @Test
    void baseFootprintResolveDiffersFromRawIndexOnTopologyChange() {
        // RECT index 2 在 L 上若直接 floorMod 仍是 2，但方向继承可能落到别的边
        int inherited = FacadeEdgeResolver.resolveSegmentIndex(
            FacadeEdgeScope.BASE_FOOTPRINT, 2, RECT, L_SHAPE);
        CardinalDirection expected = FacadeEdgeResolver.directionOfSegment(RECT, 2);
        assertEquals(expected, FacadeEdgeResolver.directionOfSegment(L_SHAPE, inherited));
        // 若 raw 边 2 方向与 base 边 2 不同，则证明不能共用整数索引
        if (FacadeEdgeResolver.directionOfSegment(L_SHAPE, 2) != expected) {
            assertNotEquals(2, inherited);
        }
    }

    @Test
    void patternSourceIndexReverseMapsPlateEdgeToBase() {
        int plateEast = -1;
        for (int i = 0; i < L_SHAPE.size(); i++) {
            if (FacadeEdgeResolver.directionOfSegment(L_SHAPE, i) == CardinalDirection.EAST) {
                plateEast = i;
                break;
            }
        }
        int baseIndex = FacadeEdgeResolver.patternSourceIndex(
            FacadeEdgeScope.BASE_FOOTPRINT, plateEast, RECT, L_SHAPE);
        assertEquals(CardinalDirection.EAST, FacadeEdgeResolver.directionOfSegment(RECT, baseIndex));
    }

    @Test
    void lToRectOpeningStaysOnSameCardinalFace() {
        // 开口标在 base 东立面；塔楼缩成矩形后仍应落在东立面
        int baseEast = -1;
        for (int i = 0; i < RECT.size(); i++) {
            if (FacadeEdgeResolver.directionOfSegment(RECT, i) == CardinalDirection.EAST) {
                baseEast = i;
                break;
            }
        }
        int onTower = FacadeEdgeResolver.resolveSegmentIndex(
            FacadeEdgeScope.BASE_FOOTPRINT, baseEast, L_SHAPE, RECT);
        assertEquals(CardinalDirection.EAST, FacadeEdgeResolver.directionOfSegment(RECT, onTower));
    }
}
