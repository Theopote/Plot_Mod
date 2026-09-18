package com.plot.plugin.pattern.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternTypeCatalogTest {

    @Test
    void allPatternTypesAreCategorizedExactlyOnce() {
        Set<ProceduralPatternConfig.PatternType> covered = new HashSet<>();
        for (PatternTypeCatalog.Category category : PatternTypeCatalog.categories()) {
            for (ProceduralPatternConfig.PatternType type : category.types()) {
                assertTrue(covered.add(type), () -> "duplicate category entry: " + type);
            }
        }
        assertEquals(
            Set.of(ProceduralPatternConfig.PatternType.values()),
            covered,
            "every PatternType must appear in PatternTypeCatalog");
    }
}
