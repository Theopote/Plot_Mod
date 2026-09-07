package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase G：{@code docs/development/RoadDeprecatedApiInventory.md} 的自动化守卫。
 */
class RoadDeprecatedApiInventoryTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    /** Batch A 已删除；生产代码中不应再出现这些符号。 */
    private static final List<String> BATCH_A_REMOVED_SYMBOLS = List.of(
        "linkEdgeToRoad",
        "RoadCrossSection.fromPreset",
        "CrossSectionLayout.fromPreset",
        "RoadPlanGeometry.planLength",
        "RoadPlanGeometry.instanceChainLength",
        "RoadStationing.totalLength",
        "RoadStationing.planLength",
        "mergeJunctionBlocksWithConfigDefaults",
        "countsAsEngineeringTerrain",
        "MinecraftTerrainSampler.isNaturalDecoration",
        "Road.applyPreset",
        "RoadSystemConfig.applyPreset",
        "getPresets("
    );

    /** 整类 deprecated；生产代码不应引用（测试除外）。 */
    private static final List<String> DEPRECATED_TYPES_PRODUCTION_FORBIDDEN = List.of(
        "VoxelGradeDiscretizer",
        "RoadSegmentTopologyAnalyzer"
    );

    @Test
    void batchARemovedSymbolsAbsentFromProduction() throws IOException {
        for (String symbol : BATCH_A_REMOVED_SYMBOLS) {
            long hits = countProductionReferences(symbol);
            assertTrue(
                hits == 0,
                () -> "Batch A removed symbol reintroduced in production: " + symbol + " (" + hits + " hits)");
        }
    }

    @Test
    void deprecatedDelegateTypesUnusedInProduction() throws IOException {
        for (String type : DEPRECATED_TYPES_PRODUCTION_FORBIDDEN) {
            long hits = countProductionReferences(type);
            assertTrue(
                hits == 0,
                () -> "Deprecated type still referenced in production: " + type + " (" + hits + " hits)");
        }
    }

    private static long countProductionReferences(String symbol) throws IOException {
        if (!Files.isDirectory(MAIN_SOURCES)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
            return paths
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !isExcludedReferenceFile(path, symbol))
                .mapToLong(path -> countOccurrences(path, symbol))
                .sum();
        }
    }

    private static boolean isExcludedReferenceFile(Path path, String symbol) {
        String fileName = path.getFileName().toString();
        if ("RoadSegmentTopologyAnalyzer".equals(symbol)) {
            return fileName.equals("RoadSegmentTopologyKind.java")
                || fileName.equals("RoadSegmentTopologyAnalyzer.java");
        }
        if ("VoxelGradeDiscretizer".equals(symbol)) {
            return fileName.equals("VoxelGradeDiscretizer.java");
        }
        return false;
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
