package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;

/** 单项常识性线路检查。 */
public interface LineValidationCheck {
    void apply(LineValidationContext context, LineEngineeringReport report);
}
