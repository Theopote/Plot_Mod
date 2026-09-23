package com.plot.plugin.building;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.utils.PlotI18n;

/** 建筑轮廓名称唯一性：默认序号递增与冲突消解。 */
public final class BuildingNameHelper {
    private BuildingNameHelper() {
    }

    public static boolean nameExists(BuildingProject project, String candidate, String excludeBuildingId) {
        if (candidate == null || candidate.isBlank() || project == null) {
            return false;
        }
        for (BuildingFootprint existing : project.getBuildings().values()) {
            if (excludeBuildingId != null && excludeBuildingId.equals(existing.getId())) {
                continue;
            }
            String existingName = existing.getName();
            if (existingName != null && candidate.equals(existingName)) {
                return true;
            }
        }
        return false;
    }

    public static String nextDefaultName(BuildingProject project) {
        int number = 1;
        while (number < 10_000) {
            String candidate = PlotI18n.tr("plugin.building.default_name", number);
            if (!nameExists(project, candidate, null)) {
                return candidate;
            }
            number++;
        }
        throw new IllegalStateException("Unable to allocate unique building name");
    }

    /**
     * 在已有名称冲突时递增末尾序号（如「建筑 1」→「建筑 2」），否则追加「 2」「 3」…
     */
    public static String resolveUniqueName(BuildingProject project, String preferred, String excludeBuildingId) {
        String trimmed = preferred != null ? preferred.trim() : "";
        if (trimmed.isEmpty()) {
            return nextDefaultName(project);
        }
        if (!nameExists(project, trimmed, excludeBuildingId)) {
            return trimmed;
        }
        String incremented = incrementTrailingNumber(trimmed);
        if (incremented != null && !nameExists(project, incremented, excludeBuildingId)) {
            return incremented;
        }
        int suffix = 2;
        while (nameExists(project, trimmed + " " + suffix, excludeBuildingId)) {
            suffix++;
        }
        return trimmed + " " + suffix;
    }

    private static String incrementTrailingNumber(String name) {
        int lastSpace = name.lastIndexOf(' ');
        if (lastSpace < 0 || lastSpace >= name.length() - 1) {
            return null;
        }
        String suffix = name.substring(lastSpace + 1);
        if (!suffix.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            long number = Long.parseLong(suffix);
            return name.substring(0, lastSpace + 1) + (number + 1);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
