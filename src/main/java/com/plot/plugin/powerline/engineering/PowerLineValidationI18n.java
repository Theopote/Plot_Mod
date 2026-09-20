package com.plot.plugin.powerline.engineering;

import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.utils.PlotI18n;

/** 塔体几何校验文案。 */
public final class PowerLineValidationI18n {
    private PowerLineValidationI18n() {
    }

    public static String towerValidationMessage(TowerValidationIssue issue) {
        if (issue == null) {
            return "";
        }
        return PlotI18n.tr(issue.messageKey(), issue.messageArgs());
    }
}
