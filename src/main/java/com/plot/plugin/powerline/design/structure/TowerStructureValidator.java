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
        List<TowerStation> stations = structure.sortedStations();
        if (stations.size() < 2) {
            issues.add(new TowerValidationIssue(
                TowerValidationSeverity.ERROR,
                "Tower structure requires at least 2 stations"));
        }

        Set<Double> heights = new HashSet<>();
        for (int i = 0; i < stations.size(); i++) {
            TowerStation station = stations.get(i);
            if (!heights.add(station.getHeight())) {
                issues.add(new TowerValidationIssue(
                    TowerValidationSeverity.ERROR,
                    "Duplicate station height: " + station.getHeight()));
            }
            if (i > 0 && station.getHeight() < stations.get(i - 1).getHeight()) {
                issues.add(new TowerValidationIssue(
                    TowerValidationSeverity.ERROR,
                    "Station heights must be non-decreasing"));
            }
            if (station.getHalfWidth() < 0 || station.getHalfDepth() < 0) {
                issues.add(new TowerValidationIssue(
                    TowerValidationSeverity.ERROR,
                    "Station dimensions must be non-negative"));
            }
        }

        for (TowerBay bay : structure.getBays()) {
            if (structure.findStation(bay.getLowerStationId()) == null
                    || structure.findStation(bay.getUpperStationId()) == null) {
                issues.add(new TowerValidationIssue(
                    TowerValidationSeverity.ERROR,
                    "Bay references missing station"));
            }
        }

        double maxHeight = structure.maxHeight();
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            if (attachment.getVerticalOffset() > maxHeight + 2) {
                issues.add(new TowerValidationIssue(
                    TowerValidationSeverity.WARNING,
                    String.format(
                        "Attachment '%s' height %.1f exceeds tower top %.1f",
                        attachment.getName(),
                        attachment.getVerticalOffset(),
                        maxHeight)));
            }
            double maxReach = Math.max(structure.maxHalfWidth(), structure.maxHalfDepth());
            for (TowerArm arm : structure.getArms()) {
                maxReach = Math.max(maxReach, arm.getLateralReach());
            }
            if (Math.abs(attachment.getLateralOffset()) > maxReach + 1) {
                issues.add(new TowerValidationIssue(
                    TowerValidationSeverity.WARNING,
                    String.format(
                        "Attachment '%s' lateral %.1f may be outside structural support envelope",
                        attachment.getName(),
                        attachment.getLateralOffset())));
            }
        }

        for (TowerArm arm : structure.getArms()) {
            if (arm.getBaseHeight() > maxHeight) {
                issues.add(new TowerValidationIssue(
                    TowerValidationSeverity.WARNING,
                    String.format(
                        "Arm '%s' base height %.1f is above tower top %.1f",
                        arm.getId(),
                        arm.getBaseHeight(),
                        maxHeight)));
            }
        }

        return issues;
    }
}
