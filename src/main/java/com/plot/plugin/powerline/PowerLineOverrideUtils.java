package com.plot.plugin.powerline;

import com.plot.plugin.powerline.model.PoleOverride;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.List;

/** 杆塔覆盖（按里程）读写辅助。 */
public final class PowerLineOverrideUtils {
    private static final double MATCH_TOLERANCE = 2.0;

    private PowerLineOverrideUtils() {
    }

    public static PoleOverride findOverride(PowerLineFootprint footprint, double stationing) {
        return findOverride(footprint.getPoleOverrides(), stationing);
    }

    public static PoleOverride findOverride(List<PoleOverride> overrides, double stationing) {
        PoleOverride best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PoleOverride override : overrides) {
            double distance = Math.abs(override.getPathDistance() - stationing);
            if (distance <= MATCH_TOLERANCE && distance < bestDistance) {
                best = override;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static void setRoleOverride(PowerLineFootprint footprint, double stationing, TowerRole roleOrNull) {
        List<PoleOverride> overrides = new ArrayList<>(footprint.getPoleOverrides());
        PoleOverride existing = findOverride(overrides, stationing);
        if (roleOrNull == null) {
            if (existing != null) {
                existing.setRoleOverride(null);
                if (existing.getRoleOverride() == null
                        && (existing.getPoleDesignOverrideId() == null
                        || existing.getPoleDesignOverrideId().isBlank())) {
                    overrides.remove(existing);
                }
            }
        } else if (existing != null) {
            existing.setRoleOverride(roleOrNull);
        } else {
            PoleOverride override = new PoleOverride(stationing);
            override.setRoleOverride(roleOrNull);
            overrides.add(override);
        }
        footprint.setPoleOverrides(overrides);
    }

    public static void setDesignOverride(PowerLineFootprint footprint, double stationing, String designIdOrNull) {
        List<PoleOverride> overrides = new ArrayList<>(footprint.getPoleOverrides());
        PoleOverride existing = findOverride(overrides, stationing);
        if (designIdOrNull == null || designIdOrNull.isBlank()) {
            if (existing != null) {
                existing.setPoleDesignOverrideId(null);
                if (existing.getRoleOverride() == null
                        && (existing.getPoleDesignOverrideId() == null
                        || existing.getPoleDesignOverrideId().isBlank())) {
                    overrides.remove(existing);
                }
            }
        } else if (existing != null) {
            existing.setPoleDesignOverrideId(designIdOrNull);
        } else {
            PoleOverride override = new PoleOverride(stationing);
            override.setPoleDesignOverrideId(designIdOrNull);
            overrides.add(override);
        }
        footprint.setPoleOverrides(overrides);
    }

    public static String roleShortCode(TowerRole role) {
        if (role == null) {
            return "?";
        }
        return switch (role) {
            case SUSPENSION -> "S";
            case ANGLE -> "A";
            case DEAD_END -> "D";
            case TERMINAL -> "T";
            case SPECIAL -> "X";
        };
    }
}
