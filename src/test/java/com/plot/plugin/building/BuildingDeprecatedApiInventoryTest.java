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
 * <p>
 * 当前 Deprecated 符号仍保留在代码库中；本测试确保它们<strong>未被生产代码重新引用</strong>
 * （定义处除外）。Batch A 删除后，可扩展为与 Road 相同的「已删符号不得重现」断言。
 */
class BuildingDeprecatedApiInventoryTest {

    private static final Path BUILDING_MAIN = Path.of("src/main/java/com/plot/plugin/building");

    /**
     * 除定义文件外，生产代码不应调用这些符号。
     * 定义文件路径见 {@link #isDefinitionSite(String, String)}。
     */
    private static final List<String> MUST_NOT_BE_USED_IN_PRODUCTION = List.of(
        "isAxisAlignedSlopedRoofEligible",
        "baseElevation()",
        "earthworkPadElevation()",
        "usedEarthworkPad()",
        ".getFootprint()"
    );

    @Test
    void deprecatedSymbolsHaveNoProductionUsagesOutsideDefinitions() throws IOException {
        for (String symbol : MUST_NOT_BE_USED_IN_PRODUCTION) {
            long hits = countProductionReferences(symbol);
            assertEquals(0, hits,
                () -> "Deprecated symbol used in production outside definition: " + symbol + " (" + hits + " hits)");
        }
    }

    private static long countProductionReferences(String symbol) throws IOException {
        if (!Files.isDirectory(BUILDING_MAIN)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(BUILDING_MAIN)) {
            return paths
                .filter(path -> path.toString().endsWith(".java"))
                .mapToLong(path -> {
                    if (isDefinitionSite(path.toString(), symbol)) {
                        return 0;
                    }
                    return countOccurrences(path, symbol);
                })
                .sum();
        }
    }

    private static boolean isDefinitionSite(String filePath, String symbol) {
        return switch (symbol) {
            case "isAxisAlignedSlopedRoofEligible" -> filePath.endsWith("BuildingGeometryUtils.java");
            case "baseElevation()", "earthworkPadElevation()", "usedEarthworkPad()" ->
                filePath.endsWith("GenerationSiteResolver.java");
            case ".getFootprint()" -> filePath.endsWith("BuildingGenerationContext.java");
            default -> false;
        };
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
