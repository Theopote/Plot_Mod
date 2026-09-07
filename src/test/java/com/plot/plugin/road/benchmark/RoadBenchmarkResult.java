package com.plot.plugin.road.benchmark;

/**
 * 道路路网性能基准单次运行结果。
 */
public record RoadBenchmarkResult(
        String scaleId,
        int roadCount,
        int edgeCount,
        int nodeCount,
        long networkCreationMillis,
        long snapshotMillis,
        int serializedJsonChars,
        long validationMillis,
        long previewMillis,
        long generationMillis,
        long junctionGenerationMillis,
        int placementRecords,
        long approximateMemoryBytes) {

    public String summary() {
        return String.format(
            "%s roads=%d edges=%d nodes=%d createMs=%d snapshotMs=%d jsonChars=%d "
                + "validationMs=%d previewMs=%d genMs=%d junctionMs=%d placements=%d memKB=%d",
            scaleId,
            roadCount,
            edgeCount,
            nodeCount,
            networkCreationMillis,
            snapshotMillis,
            serializedJsonChars,
            validationMillis,
            previewMillis,
            generationMillis,
            junctionGenerationMillis,
            placementRecords,
            approximateMemoryBytes / 1024);
    }
}
