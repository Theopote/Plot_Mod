package com.plot.plugin.powerline.design.structure;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 塔体结构校验（warning 为主，仅致命问题为 ERROR）。 */
public final class TowerStructureValidator {

    private TowerStructureValidator() {
    }

    public static List<TowerValidationIssue> validate(PoleDesign design) {
        List<TowerValidationIssue> issues = new ArrayList<>();
        if (design == null || !design.hasTowerStructure()) {
            return issues;
        }

        TowerStructureDesign structure = design.getTowerStructure();
        List<TowerStation> stations = structure.getStations();
        if (structure.sortedStations().size() < 2) {
            issues.add(TowerValidationIssue.of(
                TowerValidationSeverity.ERROR,
                "plugin.powerline.tower_validation.min_stations"));
        }

        Set<Double> heights = new HashSet<>();
        for (TowerStation station : stations) {
            if (!heights.add(station.getHeight())) {
                issues.add(TowerValidationIssue.of(
                    TowerValidationSeverity.ERROR,
                    "plugin.powerline.tower_validation.duplicate_station",
                    station.getHeight()));
            }
            if (station.getHalfWidth() < 0 || station.getHalfDepth() < 0) {
                issues.add(TowerValidationIssue.of(
                    TowerValidationSeverity.ERROR,
                    "plugin.powerline.tower_validation.negative_dimensions"));
            }
        }

        for (TowerBay bay : structure.getBays()) {
            if (structure.findStation(bay.getLowerStationId()) == null
                    || structure.findStation(bay.getUpperStationId()) == null) {
                issues.add(TowerValidationIssue.of(
                    TowerValidationSeverity.ERROR,
                    "plugin.powerline.tower_validation.missing_station_ref"));
            }
        }

        double maxHeight = structure.maxHeight();
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            if (attachment.getVerticalOffset() > maxHeight + 2) {
                issues.add(TowerValidationIssue.of(
                    TowerValidationSeverity.WARNING,
                    "plugin.powerline.tower_validation.attachment_above_top",
                    attachment.getName(),
                    attachment.getVerticalOffset(),
                    maxHeight));
            }
            double maxReach = Math.max(structure.maxHalfWidth(), structure.maxHalfDepth());
            for (TowerArm arm : structure.getArms()) {
                maxReach = Math.max(maxReach, arm.getLateralReach());
            }
            if (Math.abs(attachment.getLateralOffset()) > maxReach + 1) {
                issues.add(TowerValidationIssue.of(
                    TowerValidationSeverity.WARNING,
                    "plugin.powerline.tower_validation.attachment_lateral",
                    attachment.getName(),
                    attachment.getLateralOffset()));
            }
        }

        for (TowerArm arm : structure.getArms()) {
            if (arm.getBaseHeight() > maxHeight) {
                issues.add(TowerValidationIssue.of(
                    TowerValidationSeverity.WARNING,
                    "plugin.powerline.tower_validation.arm_above_top",
                    arm.getId(),
                    arm.getBaseHeight(),
                    maxHeight));
            }
        }

        return issues;
    }
}
