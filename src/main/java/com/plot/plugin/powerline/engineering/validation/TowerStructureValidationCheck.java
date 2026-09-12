package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerStructureValidator;
import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.plugin.powerline.design.structure.TowerValidationSeverity;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssueLocation;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.PowerLineValidationI18n;
import com.plot.plugin.powerline.engineering.SimplePowerLineIssue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 生成结果中杆塔结构的致命/警告问题。 */
public final class TowerStructureValidationCheck implements LineValidationCheck {
    @Override
    public void apply(LineValidationContext context, PowerLineValidationReport report) {
        if (context.geometry() == null) {
            return;
        }
        Set<String> seenDesigns = new HashSet<>();
        for (PolePlacement placement : context.geometry().getPlacements()) {
            if (placement == null || placement.design() == null) {
                continue;
            }
            PoleDesign design = placement.design();
            String dedupeKey = design.getId() + ":" + design.getGeneratorConfig();
            if (!seenDesigns.add(dedupeKey)) {
                continue;
            }
            List<TowerValidationIssue> issues = TowerStructureValidator.validate(design);
            for (TowerValidationIssue issue : issues) {
                if (issue.severity() == TowerValidationSeverity.INFO) {
                    continue;
                }
                report.addIssue(new SimplePowerLineIssue(
                    EngineeringRuleIds.TOWER_STRUCTURE_INVALID,
                    toIssueSeverity(issue.severity()),
                    issue.localizedMessage(),
                    PowerLineIssueLocation.at(placement.planPosition(), 0.0),
                    0.0,
                    0.0,
                    design.getName(),
                    PowerLineValidationI18n.towerValidationMessage(issue)));
            }
        }
    }

    private static PowerLineIssueSeverity toIssueSeverity(TowerValidationSeverity severity) {
        return severity == TowerValidationSeverity.ERROR
            ? PowerLineIssueSeverity.ERROR
            : PowerLineIssueSeverity.WARNING;
    }
}
