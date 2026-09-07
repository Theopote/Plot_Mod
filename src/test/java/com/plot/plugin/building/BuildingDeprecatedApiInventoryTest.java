package com.plot.plugin.building;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P3：{@code docs/development/BuildingDeprecatedApiInventory.md} 的自动化守卫。
 */
class BuildingDeprecatedApiInventoryTest {

    private static final Path BUILDING_MAIN = Path.of("src/main/java/com/plot/plugin/building");

    /** Batch A 已删除；生产代码中不应再出现这些符号。 */
    private static final List<String> BATCH_A_REMOVED_SYMBOLS = List.of(
        "isAxisAlignedSlopedRoofEligible",
        "baseElevation()",
        "earthworkPadElevation()",
        "usedEarthworkPad()",
        "getFootprint()",
        "resolveRoofType(BuildingFootprint footprint",
        "analyze(null, massing, world, coordinateService)"
    );

    @Test
    void batchARemovedSymbolsAbsentFromProduction() throws IOException {
        for (String symbol : BATCH_A_REMOVED_SYMBOLS) {
            long hits = countProductionReferences(symbol);
            assertEquals(0, hits,
                () -> "Batch A removed symbol reintroduced in production: " + symbol + " (" + hits + " hits)");
        }
    }

    @Test
    void noDeprecatedAnnotationsRemainInBuildingProduction() throws IOException {
        assertEquals(0, countProductionReferences("@Deprecated"),
            "Unexpected @Deprecated in com.plot.plugin.building production sources");
    }

    private static long countProductionReferences(String symbol) throws IOException {
        if (!Files.isDirectory(BUILDING_MAIN)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(BUILDING_MAIN)) {
            return paths
                .filter(path -> path.toString().endsWith(".java"))
                .mapToLong(path -> countOccurrences(path, symbol))
                .sum();
        }
    }

    private static long countOccurrences(Path file, String symbol) {
        try {
            String content = Files.readString(file);
            int count = 0;
            int index = 0;
            while ((index = content.indexOf(symbol, index)) >= 0) {
                count++;
                index += symbol.length();
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }
}
