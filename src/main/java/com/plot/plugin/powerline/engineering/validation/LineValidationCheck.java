package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;

/** 单项常识性线路检查。 */
public interface LineValidationCheck {
    void apply(LineValidationContext context, PowerLineValidationReport report);
}
