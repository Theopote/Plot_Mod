package com.plot.plugin.powerline.design.structure;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Keeps tower bays aligned with height-sorted station adjacency. */
public final class TowerStructureBayReconciliation {
    private TowerStructureBayReconciliation() {
    }

    /**
     * Ensures each consecutive station pair (by height) has exactly one bay,
     * removes orphaned or stale bays, and preserves bracing on surviving bays.
     */
    public static void rebuildOrReconcileBays(TowerStructureDesign structure) {
        if (structure == null) {
            return;
        }
        List<TowerStation> sorted = structure.sortedStations();
        structure.getBays().removeIf(bay ->
            structure.findStation(bay.getLowerStationId()) == null
                || structure.findStation(bay.getUpperStationId()) == null);

        if (sorted.size() < 2) {
            structure.getBays().clear();
            return;
        }

        Set<String> requiredPairs = new HashSet<>();
        for (int i = 1; i < sorted.size(); i++) {
            requiredPairs.add(pairKey(sorted.get(i - 1).getId(), sorted.get(i).getId()));
        }

        structure.getBays().removeIf(bay ->
            !requiredPairs.contains(pairKey(bay.getLowerStationId(), bay.getUpperStationId())));

        for (int i = 1; i < sorted.size(); i++) {
            String lowerId = sorted.get(i - 1).getId();
            String upperId = sorted.get(i).getId();
            if (structure.findBay(lowerId, upperId) == null) {
                structure.addBay(TowerStructurePresets.defaultBayBetween(lowerId, upperId));
            }
        }
    }

    private static String pairKey(String lowerStationId, String upperStationId) {
        return lowerStationId + '\0' + upperStationId;
    }
}
