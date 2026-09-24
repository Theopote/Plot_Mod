package com.plot.plugin.road.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线运行 N01–N05 / J01–J05 并输出 baseline 行（不经过 JUnit，避免 Windows 测试结果目录锁）。
 *
 * <pre>{@code
 * ./gradlew roadBenchmarkBaseline
 * }</pre>
 */
public final class RoadBenchmarkBaselineRunner {

    private RoadBenchmarkBaselineRunner() {
    }

    public static void main(String[] args) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Road benchmark baseline capture");
        lines.add("");

        int[] networkSizes = {100, 500, 1000, 2500, 5000};
        String[] networkIds = {"N01", "N02", "N03", "N04", "N05"};
        for (int i = 0; i < networkSizes.length; i++) {
            RoadBenchmarkResult result = RoadBenchmarkHarness.run(networkIds[i], networkSizes[i]);
            String line = "[RoadNetworkBenchmark] " + result.summary();
            System.out.println(line);
            lines.add(line);
        }

        for (JunctionBenchmarkHarness.JunctionCase junctionCase : JunctionBenchmarkHarness.standardCases()) {
            JunctionBenchmarkHarness.JunctionMetrics metrics = JunctionBenchmarkHarness.run(junctionCase);
            String line = "[JunctionRasterBenchmark] " + metrics.summary();
            System.out.println(line);
            lines.add(line);
        }

        Path output = Path.of("build", "road-benchmark-baseline.txt");
        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.println();
        System.out.println("Wrote " + output.toAbsolutePath());
    }
}
