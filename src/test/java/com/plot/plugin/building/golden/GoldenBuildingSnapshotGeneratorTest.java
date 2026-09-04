package com.plot.plugin.building.golden;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发用：打印各 Golden Case 当前指标，用于更新 {@link GoldenBuildingExpectations}。
 */
class GoldenBuildingSnapshotGeneratorTest {

    @Test
    @Disabled("Dev helper: run manually to refresh GoldenBuildingExpectations")
    void printAllGoldenMetrics() throws Exception {
        List<String> lines = new ArrayList<>();
        for (GoldenBuildingCaseFactory.Case goldenCase : GoldenBuildingCaseFactory.all()) {
            GoldenBuildingMetrics metrics = GoldenBuildingHarness.generate(goldenCase.footprint());
            lines.add("=== " + goldenCase.id() + " " + goldenCase.description() + " ===");
            lines.add(formatExpectationField(goldenCase.id(), metrics));
        }
        Path out = Path.of("build", "golden-metrics-snapshot.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, String.join(System.lineSeparator(), lines));
        System.out.println(String.join(System.lineSeparator(), lines));
    }

    static String formatExpectationField(String id, GoldenBuildingMetrics m) {
        String warnings = m.warnings().isEmpty()
            ? "List.of()"
            : "List.of(\"" + String.join("\", \"", m.warnings()) + "\")";
        return """
            public static final GoldenBuildingMetrics %s = new GoldenBuildingMetrics(
                %d, %d, %d, %d, %d, %d, %d, %d, %d,
                %d, %d, %d, %d, %d, %d,
                "%s",
                %s);
            """.formatted(
            id,
            m.totalBlocks(), m.wallBlocks(), m.floorBlocks(), m.roofBlocks(),
            m.foundationBlocks(), m.openingBlocks(), m.otherBlocks(),
            m.cutVolume(), m.fillVolume(),
            m.minX(), m.maxX(), m.minY(), m.maxY(), m.minZ(), m.maxZ(),
            m.effectiveRoofType(),
            warnings);
    }
}
