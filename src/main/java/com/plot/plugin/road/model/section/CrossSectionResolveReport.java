package com.plot.plugin.road.model.section;

import java.util.ArrayList;
import java.util.List;

/**
 * Side-channel notes produced while resolving a cross-section (geometry clamps, material swaps).
 */
public final class CrossSectionResolveReport {
    public record MaterialReplacement(String requested, String resolved, String role) {
    }

    private final List<MaterialReplacement> materialReplacements = new ArrayList<>();
    private final List<String> geometryNotes = new ArrayList<>();

    public void noteMaterialReplacement(String requested, String resolved, String role) {
        if (requested == null || resolved == null || requested.equals(resolved)) {
            return;
        }
        materialReplacements.add(new MaterialReplacement(requested, resolved, role));
    }

    public void noteGeometry(String note) {
        if (note != null && !note.isBlank()) {
            geometryNotes.add(note);
        }
    }

    public List<MaterialReplacement> materialReplacements() {
        return List.copyOf(materialReplacements);
    }

    public List<String> geometryNotes() {
        return List.copyOf(geometryNotes);
    }

    public boolean isEmpty() {
        return materialReplacements.isEmpty() && geometryNotes.isEmpty();
    }

    public record ResolveResult(ResolvedCrossSection section, CrossSectionResolveReport report) {
    }
}
