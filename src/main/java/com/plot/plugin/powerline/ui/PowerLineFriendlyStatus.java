package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.engineering.PowerLineIssue;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 将工程分析结果翻译为玩家友好的提示文案。 */
public final class PowerLineFriendlyStatus {
    private static final double SPACING_TOLERANCE = 0.15;
    private static final double POLE_POSITION_TOLERANCE = 0.15;

    private PowerLineFriendlyStatus() {
    }

    public enum CornerKind {
        OK,
        NO_PATH,
        MISSING
    }

    public record CornerEvaluation(CornerKind kind, int missingCount) {
        public static CornerEvaluation ok() {
            return new CornerEvaluation(CornerKind.OK, 0);
        }
    }

    public enum SpacingKind {
        INVALID_SETTINGS,
        SETTINGS_ONLY,
        OK,
        TOO_CLOSE,
        TOO_FAR
    }

    public record SpacingEvaluation(
            SpacingKind kind,
            double worstSpan,
            double limit) {
        public static SpacingEvaluation of(SpacingKind kind) {
            return new SpacingEvaluation(kind, 0.0, 0.0);
        }
    }

    public static boolean hasTerrainIssues(PowerLineValidationReport report) {
        if (report == null) {
            return false;
        }
        return report.getIssues().stream()
            .anyMatch(i -> EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(i.ruleId()));
    }

    public static int terrainIssueCount(PowerLineValidationReport report) {
        if (report == null) {
            return 0;
        }
        int count = 0;
        for (PowerLineIssue issue : report.getIssues()) {
            if (EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(issue.ruleId())) {
                count++;
            }
        }
        return count;
    }

    public static int lineCheckIssueCount(PowerLineValidationReport report) {
        if (report == null) {
            return 0;
        }
        return report.issuesExcluding(EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM).size();
    }

    public static boolean spacingSettingsValid(PowerLineFootprint line) {
        return line != null;
    }

    public static SpacingEvaluation evaluateSpacing(
            PowerLineFootprint line,
            ICoordinateService coordinates) {
        if (!spacingSettingsValid(line)) {
            return SpacingEvaluation.of(SpacingKind.INVALID_SETTINGS);
        }

        List<PowerPoleSite> sites = new ArrayList<>(PowerPoleLayoutUtils.computePoleSites(line, coordinates));
        if (sites.size() < 2) {
            return SpacingEvaluation.of(SpacingKind.SETTINGS_ONLY);
        }

        sites.sort(Comparator.comparingDouble(PowerPoleSite::getStationing));
        double minAllowed = line.getCloseSpacingWarningThreshold();
        double maxAllowed = line.getMaxPoleSpacing();

        double worstShort = Double.MAX_VALUE;
        double worstLong = 0.0;
        boolean tooClose = false;
        boolean tooFar = false;

        for (int i = 0; i < sites.size() - 1; i++) {
            double span = sites.get(i + 1).getStationing() - sites.get(i).getStationing();
            if (span < minAllowed - SPACING_TOLERANCE) {
                tooClose = true;
                worstShort = Math.min(worstShort, span);
            }
            if (span > maxAllowed + SPACING_TOLERANCE) {
                tooFar = true;
                worstLong = Math.max(worstLong, span);
            }
        }

        if (tooClose) {
            return new SpacingEvaluation(SpacingKind.TOO_CLOSE, worstShort, minAllowed);
        }
        if (tooFar) {
            return new SpacingEvaluation(SpacingKind.TOO_FAR, worstLong, maxAllowed);
        }
        return SpacingEvaluation.of(SpacingKind.OK);
    }

    public static CornerEvaluation evaluateCornerPoles(
            PowerLineFootprint line,
            ICoordinateService coordinates) {
        if (line == null || line.getPathPoints().size() < 2) {
            return new CornerEvaluation(CornerKind.NO_PATH, 0);
        }

        List<Vec2d> mandatory = PowerPoleLayoutUtils.mandatoryPolePoints(
            line.getPathPoints(),
            line.getCornerAngleThreshold());
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(line, coordinates);

        int missing = 0;
        for (Vec2d required : mandatory) {
            if (!PowerPoleLayoutUtils.hasSiteNear(sites, required, POLE_POSITION_TOLERANCE, coordinates)) {
                missing++;
            }
        }
        if (missing > 0) {
            return new CornerEvaluation(CornerKind.MISSING, missing);
        }
        return CornerEvaluation.ok();
    }
}
