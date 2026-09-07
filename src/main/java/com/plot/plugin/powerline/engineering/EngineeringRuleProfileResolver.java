package com.plot.plugin.powerline.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 查找内置与用户自定义工程规则配置。 */
public final class EngineeringRuleProfileResolver {
    public EngineeringRuleProfile find(String id) {
        if (id == null || id.isBlank()) {
            return EngineeringRuleProfileCatalog.genericPlanning();
        }
        EngineeringRuleProfile builtin = EngineeringRuleProfileCatalog.findBuiltin(id);
        return builtin != null ? builtin : EngineeringRuleProfileCatalog.genericPlanning();
    }

    public List<EngineeringRuleProfile> listAll() {
        List<EngineeringRuleProfile> profiles = new ArrayList<>(EngineeringRuleProfileCatalog.defaultProfiles());
        profiles.sort(Comparator.comparing(EngineeringRuleProfile::getName, String.CASE_INSENSITIVE_ORDER));
        return profiles;
    }
}
