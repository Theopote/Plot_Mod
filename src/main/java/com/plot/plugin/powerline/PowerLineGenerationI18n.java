package com.plot.plugin.powerline;

import com.plot.plugin.powerline.model.TowerRole;
import com.plot.utils.PlotI18n;

/** 预览/生成阶段 warning 文案（存储 i18n token，展示时再翻译）。 */
public final class PowerLineGenerationI18n {
    private PowerLineGenerationI18n() {
    }

    public static String missingAttachmentDownstream(String name, String id, double x, double y) {
        return token("plugin.powerline.warn.missing_attachment_downstream", name, id, x, y);
    }

    public static String attachmentRoleMismatch(String id, Object startRole, Object endRole) {
        return token("plugin.powerline.warn.attachment_role_mismatch", id, startRole, endRole);
    }

    public static String missingAttachmentUpstream(String name, String id, double x, double y) {
        return token("plugin.powerline.warn.missing_attachment_upstream", name, id, x, y);
    }

    public static String clearanceAtPoint(double x, double y, int wireY, int groundY) {
        return token("plugin.powerline.warn.clearance_at_point", x, y, wireY, groundY);
    }

    public static String poleDesignOverrideNotFound(String overrideId) {
        return token("plugin.powerline.warn.pole_design_override_not_found", overrideId);
    }

    public static String towerFamilyNotFound(String familyId) {
        return token("plugin.powerline.warn.tower_family_not_found", familyId);
    }

    public static String linePoleDesignNotFound(String designId) {
        return token("plugin.powerline.warn.line_pole_design_not_found", designId);
    }

    public static String autoSelectedDesignNotFound(String designId) {
        return token("plugin.powerline.warn.auto_selected_design_not_found", designId);
    }

    public static String noDesignForRole(TowerRole role, String familyId) {
        return token("plugin.powerline.warn.no_design_for_role", role, familyId);
    }

    public static String familyRoleDesignNotFound(String designId) {
        return token("plugin.powerline.warn.family_role_design_not_found", designId);
    }

    public static String towerBaseUneven(int deltaBlocks) {
        return token("plugin.powerline.warn.tower_base_uneven", deltaBlocks);
    }

    public static String parametricHeightClampedForLine(int maxHeight, int limitingSiteIndex) {
        return token("plugin.powerline.warn.parametric_height_clamped_for_line", maxHeight, limitingSiteIndex);
    }

    public static String parametricWorldHeightExceededOnLine() {
        return token("plugin.powerline.warn.parametric_world_height_exceeded_on_line");
    }

    public static String parametricHeightClampedForSite(int maxHeight, int siteIndex) {
        return token("plugin.powerline.warn.parametric_height_clamped_for_site", maxHeight, siteIndex);
    }

    public static String parametricWorldHeightExceededOnSite(int siteIndex) {
        return token("plugin.powerline.warn.parametric_world_height_exceeded_on_site", siteIndex);
    }

    public static String localize(String warning) {
        if (warning == null || warning.isBlank()) {
            return "";
        }
        if (!warning.startsWith("plugin.powerline.")) {
            return warning;
        }
        int separator = warning.indexOf('|');
        if (separator < 0) {
            return PlotI18n.tr(warning);
        }
        String key = warning.substring(0, separator);
        String[] rawArgs = warning.substring(separator + 1).split("\\|", -1);
        return PlotI18n.tr(key, (Object[]) rawArgs);
    }

    private static String token(String key, Object... args) {
        StringBuilder builder = new StringBuilder(key);
        for (Object arg : args) {
            builder.append('|').append(arg);
        }
        return builder.toString();
    }
}
