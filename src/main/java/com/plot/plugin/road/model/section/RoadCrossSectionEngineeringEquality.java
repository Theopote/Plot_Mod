package com.plot.plugin.road.model.section;

import com.plot.core.material.MaterialMix;

import java.util.List;
import java.util.Objects;

/**
 * 横断面工程语义相等：用于沿桩号区间合并、镜像等操作，避免仅比较行车道宽度。
 */
public final class RoadCrossSectionEngineeringEquality {

    private RoadCrossSectionEngineeringEquality() {
    }

    public static boolean equals(RoadCrossSection left, RoadCrossSection right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return equalsLaneGroup(left.getCarriageway(), right.getCarriageway())
            && equalsEnabledWidthMaterial(left.getMedian(), right.getMedian())
            && equalsMarkings(left.getMarkings(), right.getMarkings())
            && equalsEnabledWidthMaterial(left.getShoulder(), right.getShoulder())
            && equalsEnabledWidthMaterial(left.getBikeLane(), right.getBikeLane())
            && equalsEnabledWidthMaterial(left.getSidewalk(), right.getSidewalk())
            && equalsDrain(left.getDrain(), right.getDrain())
            && equalsSlopeBatter(left.getSlopeBatter(), right.getSlopeBatter())
            && equalsStreetFurniture(left.getStreetFurniture(), right.getStreetFurniture());
    }

    private static boolean equalsLaneGroup(LaneGroup left, LaneGroup right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (!Objects.equals(left.getLaneCount(), right.getLaneCount())
                || !Objects.equals(left.getWidth(), right.getWidth())
                || !equalsMaterial(left.getMaterial(), right.getMaterial())) {
            return false;
        }
        List<Lane> leftLanes = left.getLanes();
        List<Lane> rightLanes = right.getLanes();
        if (leftLanes.size() != rightLanes.size()) {
            return false;
        }
        for (int i = 0; i < leftLanes.size(); i++) {
            Lane leftLane = leftLanes.get(i);
            Lane rightLane = rightLanes.get(i);
            if (!Objects.equals(leftLane.getWidth(), rightLane.getWidth())
                    || !Objects.equals(leftLane.getMaterial(), rightLane.getMaterial())) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalsMarkings(Markings left, Markings right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getLaneDividers(), right.getLaneDividers())
            && Objects.equals(left.getCenterLine(), right.getCenterLine())
            && left.getCenterLineStyle() == right.getCenterLineStyle()
            && Objects.equals(left.getMaterial(), right.getMaterial());
    }

    private static boolean equalsDrain(Drain left, Drain right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getEnabled(), right.getEnabled());
    }

    private static boolean equalsSlopeBatter(SlopeBatter left, SlopeBatter right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getEnabled(), right.getEnabled())
            && Objects.equals(left.getFillRatio(), right.getFillRatio())
            && Objects.equals(left.getCutRatio(), right.getCutRatio())
            && Objects.equals(left.getFillMaterial(), right.getFillMaterial())
            && Objects.equals(left.getCutMaterial(), right.getCutMaterial());
    }

    private static boolean equalsStreetFurniture(StreetFurniture left, StreetFurniture right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getStreetlightSpacing(), right.getStreetlightSpacing())
            && Objects.equals(left.getStreetlightBlock(), right.getStreetlightBlock());
    }

    private static boolean equalsEnabledWidthMaterial(
            Object leftComponent,
            Object rightComponent) {
        if (leftComponent == rightComponent) {
            return true;
        }
        if (leftComponent == null || rightComponent == null) {
            return false;
        }
        if (leftComponent instanceof Median left && rightComponent instanceof Median right) {
            return Objects.equals(left.getEnabled(), right.getEnabled())
                && Objects.equals(left.getWidth(), right.getWidth())
                && Objects.equals(left.getMaterial(), right.getMaterial());
        }
        if (leftComponent instanceof Shoulder left && rightComponent instanceof Shoulder right) {
            return Objects.equals(left.getEnabled(), right.getEnabled())
                && Objects.equals(left.getWidth(), right.getWidth())
                && Objects.equals(left.getMaterial(), right.getMaterial());
        }
        if (leftComponent instanceof BikeLane left && rightComponent instanceof BikeLane right) {
            return Objects.equals(left.getEnabled(), right.getEnabled())
                && Objects.equals(left.getWidth(), right.getWidth())
                && Objects.equals(left.getMaterial(), right.getMaterial());
        }
        if (leftComponent instanceof Sidewalk left && rightComponent instanceof Sidewalk right) {
            return Objects.equals(left.getEnabled(), right.getEnabled())
                && Objects.equals(left.getWidth(), right.getWidth())
                && Objects.equals(left.getMaterial(), right.getMaterial());
        }
        return false;
    }

    private static boolean equalsMaterial(MaterialMix left, MaterialMix right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getPrimaryMaterial(), right.getPrimaryMaterial())
            && Objects.equals(left.getAccentMaterial(), right.getAccentMaterial())
            && Float.compare(left.getAccentRatio(), right.getAccentRatio()) == 0;
    }
}
