package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.section.CrossSectionDraft;

import java.util.Objects;

/** 比较两条道路的横断面草稿是否等价。 */
final class CrossSectionDraftComparer {

    private CrossSectionDraftComparer() {
    }

    static boolean equals(CrossSectionDraft left, CrossSectionDraft right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.width() == right.width()
            && left.laneCount() == right.laneCount()
            && Objects.equals(left.laneWidths(), right.laneWidths())
            && Objects.equals(left.material(), right.material())
            && left.includeShoulder() == right.includeShoulder()
            && left.shoulderWidth() == right.shoulderWidth()
            && left.includeSidewalk() == right.includeSidewalk()
            && left.sidewalkWidth() == right.sidewalkWidth()
            && Objects.equals(left.sidewalkMaterial(), right.sidewalkMaterial())
            && left.includeBikeLane() == right.includeBikeLane()
            && left.bikeLaneWidth() == right.bikeLaneWidth()
            && left.includeMedian() == right.includeMedian()
            && left.medianWidth() == right.medianWidth()
            && left.streetlightSpacing() == right.streetlightSpacing()
            && left.laneDividers() == right.laneDividers()
            && left.centerLineStyle() == right.centerLineStyle()
            && Objects.equals(left.markingMaterial(), right.markingMaterial())
            && left.includeSlopeBatter() == right.includeSlopeBatter()
            && left.fillSlopeRatio() == right.fillSlopeRatio()
            && left.cutSlopeRatio() == right.cutSlopeRatio()
            && Objects.equals(left.fillSlopeMaterial(), right.fillSlopeMaterial())
            && Objects.equals(left.cutSlopeMaterial(), right.cutSlopeMaterial())
            && left.maxSlope() == right.maxSlope();
    }
}
