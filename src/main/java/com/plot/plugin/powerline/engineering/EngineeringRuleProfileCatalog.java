package com.plot.plugin.powerline.engineering;

import com.plot.plugin.powerline.PowerLineSagUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置工程规则配置目录（内部默认：垂度 / 离地净空）。
 * <p>
 * 玩家可见检查由 {@link com.plot.plugin.powerline.engineering.validation.PowerLineValidator}
 * 驱动，跨距阈值跟随线路 {@code min/maxPoleSpacing}，不再暴露 profile 选择。
 */
public final class EngineeringRuleProfileCatalog {
    private EngineeringRuleProfileCatalog() {
    }

    public static EngineeringRuleProfile genericPlanning() {
        EngineeringRuleProfile profile = new EngineeringRuleProfile(
            EngineeringRuleProfile.GENERIC_PLANNING_ID,
            "Generic Planning");
        profile.setDescription(
            "Planning defaults for Minecraft block-scale routes. Not a certified regulatory standard.");
        profile.getClearance().setMinimumGroundClearance(6.0);
        profile.getClearance().setMinimumConductorSeparation(2.0);
        profile.getClearance().setMinimumGroundWireSeparation(2.0);
        profile.getSpan().setPreferredSpan(20.0);
        profile.getSpan().setMaximumSpan(40.0);
        profile.getSpan().setMinimumSpan(6.0);
        profile.getSag().setMaxSagDepth(PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH);
        profile.getAngle().setSuspensionMaxAngle(5.0);
        profile.getAngle().setAngleTowerMaxAngle(60.0);
        profile.getTower().setPreferredHeightMargin(2.0);
        profile.getTower().setMaximumBaseUnevenness(4.0);
        return profile;
    }

    public static List<EngineeringRuleProfile> defaultProfiles() {
        List<EngineeringRuleProfile> profiles = new ArrayList<>();
        profiles.add(genericPlanning());
        return profiles;
    }

    public static Map<String, EngineeringRuleProfile> indexById() {
        Map<String, EngineeringRuleProfile> indexed = new LinkedHashMap<>();
        for (EngineeringRuleProfile profile : defaultProfiles()) {
            indexed.put(profile.getId(), profile);
        }
        return indexed;
    }

    public static EngineeringRuleProfile findBuiltin(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return indexById().get(id);
    }
}
