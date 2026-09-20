package com.plot.plugin.powerline.ui;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 线路 Tab 概览区杆塔布局缓存（地图、缩略图、列表共用）。 */
final class PowerLineOverviewLayoutCache {
    private static final Map<String, Entry> CACHE = new HashMap<>();

    private record Entry(int fingerprint, List<PowerPoleSite> sites) {
    }

    private PowerLineOverviewLayoutCache() {
    }

    static List<PowerPoleSite> poleSites(PowerLineFootprint line, ICoordinateService coordinates) {
        if (line == null || coordinates == null) {
            return List.of();
        }
        int fingerprint = line.layoutFingerprint();
        Entry cached = CACHE.get(line.getId());
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached.sites;
        }
        List<PowerPoleSite> sites = List.copyOf(
            PowerPoleLayoutUtils.computePoleSites(line, coordinates));
        CACHE.put(line.getId(), new Entry(fingerprint, sites));
        return sites;
    }

    static int poleCount(PowerLineFootprint line, ICoordinateService coordinates) {
        return poleSites(line, coordinates).size();
    }

    static void retainOnly(Set<String> lineIds) {
        if (lineIds == null || lineIds.isEmpty()) {
            CACHE.clear();
            return;
        }
        CACHE.entrySet().removeIf(stringEntryEntry -> !lineIds.contains(stringEntryEntry.getKey()));
    }

    static void clear() {
        CACHE.clear();
    }
}
