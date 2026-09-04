package com.plot.plugin.building.golden;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发用：刷新 <strong>Regression Golden</strong>（{@link GoldenBuildingExpectations}）快照。
 * <p>
 * 仅用于 A 类指标锁定。不要把输出当成 Semantic Acceptance expected——
 * 正确性断言必须手写在 {@link SemanticAcceptanceAssertions}。
 */
class GoldenBuildingSnapshotGeneratorTest {

    @Test
    @Disabled("Dev helper: run manually to refresh GoldenBuildingExpectations (Regression only)")
    void printAllGoldenMetrics() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("// Regression Golden snapshot — NOT semantic acceptance");
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
