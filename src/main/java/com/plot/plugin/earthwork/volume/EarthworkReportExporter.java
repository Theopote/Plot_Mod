package com.plot.plugin.earthwork.volume;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将预览方量报告导出为 CSV / JSON 文件（默认目录 {@code &lt;gameDir&gt;/plot/earthwork-reports/}）。
 */
public final class EarthworkReportExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_STAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter ISO_STAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private EarthworkReportExporter() {
    }

    public record ExportResult(Path csvPath, Path jsonPath) {
    }

    public static Path defaultExportDirectory() {
        return FabricLoader.getInstance()
            .getGameDir()
            .resolve("plot")
            .resolve("earthwork-reports");
    }

    public static ExportResult exportPreview(
            EarthworkGenerationResult preview,
            EarthworkProject project,
            GradingRegion previewRegion) throws IOException {
        return exportPreview(preview, project, previewRegion, defaultExportDirectory());
    }

    public static ExportResult exportPreview(
            EarthworkGenerationResult preview,
            EarthworkProject project,
            GradingRegion previewRegion,
            Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        if (preview == null || preview.volumeReport == null) {
            throw new IllegalArgumentException("preview result is empty");
        }

        Files.createDirectories(directory);
        String stamp = FILE_STAMP.format(LocalDateTime.now());
        String baseName = buildBaseName(project, previewRegion, stamp);
        Path csvPath = directory.resolve(baseName + ".csv");
        Path jsonPath = directory.resolve(baseName + ".json");

        Files.writeString(csvPath, buildCsv(preview, project, previewRegion), StandardCharsets.UTF_8);
        Files.writeString(jsonPath, buildJson(preview, project, previewRegion), StandardCharsets.UTF_8);
        return new ExportResult(csvPath, jsonPath);
    }

    private static String buildBaseName(
            EarthworkProject project,
            GradingRegion previewRegion,
            String stamp) {
        String projectPart = sanitizeFileToken(
            project != null ? project.getActiveSite().getName() : "earthwork");
        String regionPart = sanitizeFileToken(
            previewRegion != null ? previewRegion.getName() : "region");
        return "earthwork-report-" + projectPart + "-" + regionPart + "-" + stamp;
    }

    private static String sanitizeFileToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unnamed";
        }
        String cleaned = raw.trim()
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("\\s+", "_");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private static String buildJson(
            EarthworkGenerationResult preview,
            EarthworkProject project,
            GradingRegion previewRegion) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportedAt", ISO_STAMP.format(LocalDateTime.now()));
        if (project != null) {
            root.put("projectName", project.getActiveSite().getName());
            root.put("siteId", project.getActiveSite().getId());
        }
        if (previewRegion != null) {
            root.put("previewRegionId", previewRegion.getId());
            root.put("previewRegionName", previewRegion.getName());
        }
        root.put("calculationCellCount", preview.calculationCellCount);
        root.put("resolvedElevation", preview.resolvedElevation);
        root.put("resolvedElevationMin", preview.resolvedElevationMin);
        root.put("resolvedElevationMax", preview.resolvedElevationMax);
        root.put("slopedSurface", preview.slopedSurface);
        root.put("siteGeneration", preview.siteGeneration);
        root.put("warnings", List.copyOf(preview.warnings));
        root.put("volume", volumeMap(preview.volumeReport));
        root.put("terrainSnapshot", terrainMap(preview.existingTerrainSnapshot));
        if (preview.projectReport != null && preview.projectReport != EarthworkProjectReport.EMPTY) {
            root.put("projectBalance", projectBalanceMap(preview.projectReport, project));
        }
        return GSON.toJson(root);
    }

    private static Map<String, Object> volumeMap(EarthworkVolumeReport report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("geometricCutVolume", report.geometricCutVolume());
        map.put("geometricFillVolume", report.geometricFillVolume());
        map.put("reusableCutVolume", report.reusableCutVolume());
        map.put("compactedFillSupply", report.compactedFillSupply());
        map.put("compactedFillDemand", report.compactedFillDemand());
        map.put("importVolume", report.importVolume());
        map.put("exportVolume", report.exportVolume());
        map.put("cutChangedBlocks", report.cutChangedBlocks());
        map.put("fillChangedBlocks", report.fillChangedBlocks());
        map.put("totalChangedBlocks", report.totalChangedBlocks());
        return map;
    }

    private static Map<String, Object> terrainMap(TerrainSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (snapshot == null || snapshot.isEmpty()) {
            map.put("empty", true);
            return map;
        }
        TerrainSnapshot.Metadata metadata = snapshot.metadata();
        map.put("capturedAtEpochMs", metadata.capturedAtEpochMs());
        map.put("worldKey", metadata.worldKey());
        map.put("columnCount", metadata.columnCount());
        map.put("contentFingerprint", metadata.contentFingerprint());
        return map;
    }

    private static Map<String, Object> projectBalanceMap(
            EarthworkProjectReport report,
            EarthworkProject project) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCut", report.totalCut());
        map.put("totalFill", report.totalFill());
        map.put("netVolume", report.netVolume());
        map.put("reusableCut", report.reusableCut());
        map.put("importRequired", report.importRequired());
        map.put("exportRequired", report.exportRequired());
        map.put("balanceScope", report.balanceScope());
        map.put("siteWideVerticalOffset", report.siteWideVerticalOffset());
        map.put("zoneVerticalOffsets", report.zoneVerticalOffsets());

        List<Map<String, Object>> zones = new ArrayList<>();
        for (Map.Entry<String, EarthworkVolumeReport> entry : report.byZone().entrySet()) {
            Map<String, Object> zone = new LinkedHashMap<>();
            zone.put("zoneId", entry.getKey());
            zone.put("zoneName", resolveZoneName(project, entry.getKey()));
            zone.putAll(volumeMap(entry.getValue()));
            zones.add(zone);
        }
        map.put("zones", zones);

        List<Map<String, Object>> transfers = new ArrayList<>();
        for (EarthworkAllocationMatrix.Transfer transfer : report.allocationMatrix().transfers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceZoneId", transfer.sourceZoneId());
            row.put("sourceZoneName", resolveAllocationEndpoint(project, transfer.sourceZoneId(), true));
            row.put("destinationZoneId", transfer.destinationZoneId());
            row.put("destinationZoneName", resolveAllocationEndpoint(project, transfer.destinationZoneId(), false));
            row.put("volume", transfer.volume());
            transfers.add(row);
        }
        map.put("allocationTransfers", transfers);

        List<Map<String, Object>> overlaps = new ArrayList<>();
        for (EarthworkProjectReport.OverlapConflict overlap : report.overlaps()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("zoneIdA", overlap.zoneIdA());
            row.put("zoneNameA", overlap.zoneNameA());
            row.put("zoneIdB", overlap.zoneIdB());
            row.put("zoneNameB", overlap.zoneNameB());
            row.put("overlapCells", overlap.overlapCells());
            row.put("winnerZoneId", overlap.winnerZoneId());
            row.put("winnerZoneName", overlap.winnerZoneName());
            overlaps.add(row);
        }
        map.put("overlaps", overlaps);
        return map;
    }

    private static String buildCsv(
            EarthworkGenerationResult preview,
            EarthworkProject project,
            GradingRegion previewRegion) {
        StringBuilder csv = new StringBuilder();
        csv.append("section,key,value\n");
        appendRow(csv, "meta", "exported_at", ISO_STAMP.format(LocalDateTime.now()));
        if (project != null) {
            appendRow(csv, "meta", "project_name", project.getActiveSite().getName());
            appendRow(csv, "meta", "site_id", project.getActiveSite().getId());
        }
        if (previewRegion != null) {
            appendRow(csv, "meta", "preview_region_id", previewRegion.getId());
            appendRow(csv, "meta", "preview_region_name", previewRegion.getName());
        }
        appendRow(csv, "meta", "calculation_cell_count", preview.calculationCellCount);
        appendRow(csv, "meta", "resolved_elevation", preview.resolvedElevation);
        appendRow(csv, "meta", "resolved_elevation_min", preview.resolvedElevationMin);
        appendRow(csv, "meta", "resolved_elevation_max", preview.resolvedElevationMax);
        appendRow(csv, "meta", "sloped_surface", preview.slopedSurface);
        appendRow(csv, "meta", "site_generation", preview.siteGeneration);

        EarthworkVolumeReport volumes = preview.volumeReport;
        appendRow(csv, "volume", "geometric_cut", volumes.geometricCutVolume());
        appendRow(csv, "volume", "geometric_fill", volumes.geometricFillVolume());
        appendRow(csv, "volume", "reusable_cut", volumes.reusableCutVolume());
        appendRow(csv, "volume", "compacted_fill_supply", volumes.compactedFillSupply());
        appendRow(csv, "volume", "compacted_fill_demand", volumes.compactedFillDemand());
        appendRow(csv, "volume", "import", volumes.importVolume());
        appendRow(csv, "volume", "export", volumes.exportVolume());
        appendRow(csv, "volume", "cut_changed_blocks", volumes.cutChangedBlocks());
        appendRow(csv, "volume", "fill_changed_blocks", volumes.fillChangedBlocks());
        appendRow(csv, "volume", "total_changed_blocks", volumes.totalChangedBlocks());

        if (preview.existingTerrainSnapshot != null && !preview.existingTerrainSnapshot.isEmpty()) {
            TerrainSnapshot.Metadata metadata = preview.existingTerrainSnapshot.metadata();
            appendRow(csv, "terrain", "captured_at_epoch_ms", metadata.capturedAtEpochMs());
            appendRow(csv, "terrain", "world_key", metadata.worldKey());
            appendRow(csv, "terrain", "column_count", metadata.columnCount());
        }

        EarthworkProjectReport projectReport = preview.projectReport;
        if (projectReport != null && projectReport != EarthworkProjectReport.EMPTY) {
            appendRow(csv, "project_balance", "total_cut", projectReport.totalCut());
            appendRow(csv, "project_balance", "total_fill", projectReport.totalFill());
            appendRow(csv, "project_balance", "net_volume", projectReport.netVolume());
            appendRow(csv, "project_balance", "reusable_cut", projectReport.reusableCut());
            appendRow(csv, "project_balance", "import_required", projectReport.importRequired());
            appendRow(csv, "project_balance", "export_required", projectReport.exportRequired());
            appendRow(csv, "project_balance", "balance_scope", projectReport.balanceScope());
            appendRow(csv, "project_balance", "site_wide_vertical_offset", projectReport.siteWideVerticalOffset());

            appendRow(csv, "project_balance", "sites_with_volume", projectReport.sitesWithVolume());

            if (!projectReport.bySite().isEmpty()) {
                csv.append("\nsection,site_id,site_name,geometric_cut,geometric_fill,reusable_cut,import,export,balance_scope,site_wide_offset\n");
                for (var entry : projectReport.bySite().entrySet()) {
                    var snapshot = entry.getValue();
                    EarthworkVolumeReport siteVolumes = snapshot.volumes();
                    csv.append(csvCell("site_volume"))
                        .append(',')
                        .append(csvCell(entry.getKey()))
                        .append(',')
                        .append(csvCell(snapshot.siteName()))
                        .append(',')
                        .append(siteVolumes.geometricCutVolume())
                        .append(',')
                        .append(siteVolumes.geometricFillVolume())
                        .append(',')
                        .append(siteVolumes.reusableCutVolume())
                        .append(',')
                        .append(siteVolumes.importVolume())
                        .append(',')
                        .append(siteVolumes.exportVolume())
                        .append(',')
                        .append(csvCell(snapshot.balanceScope()))
                        .append(',')
                        .append(snapshot.siteWideVerticalOffset())
                        .append('\n');
                }
            }

            csv.append("\nsection,zone_id,zone_name,geometric_cut,geometric_fill,reusable_cut,import,export,total_changed_blocks\n");
            for (Map.Entry<String, EarthworkVolumeReport> entry : projectReport.byZone().entrySet()) {
                EarthworkVolumeReport zoneReport = entry.getValue();
                csv.append(csvCell("zone_volume"))
                    .append(',')
                    .append(csvCell(entry.getKey()))
                    .append(',')
                    .append(csvCell(resolveZoneName(project, entry.getKey())))
                    .append(',')
                    .append(zoneReport.geometricCutVolume())
                    .append(',')
                    .append(zoneReport.geometricFillVolume())
                    .append(',')
                    .append(zoneReport.reusableCutVolume())
                    .append(',')
                    .append(zoneReport.importVolume())
                    .append(',')
                    .append(zoneReport.exportVolume())
                    .append(',')
                    .append(zoneReport.totalChangedBlocks())
                    .append('\n');
            }

            if (!projectReport.allocationMatrix().isEmpty()) {
                csv.append("\nsection,source_zone_id,source_zone_name,destination_zone_id,destination_zone_name,volume\n");
                for (EarthworkAllocationMatrix.Transfer transfer : projectReport.allocationMatrix().transfers()) {
                    csv.append(csvCell("allocation"))
                        .append(',')
                        .append(csvCell(transfer.sourceZoneId()))
                        .append(',')
                        .append(csvCell(resolveAllocationEndpoint(project, transfer.sourceZoneId(), true)))
                        .append(',')
                        .append(csvCell(transfer.destinationZoneId()))
                        .append(',')
                        .append(csvCell(resolveAllocationEndpoint(project, transfer.destinationZoneId(), false)))
                        .append(',')
                        .append(transfer.volume())
                        .append('\n');
                }
            }
            if (!projectReport.crossSiteAllocationMatrix().isEmpty()) {
                csv.append("\nsection,source_site_id,source_site_name,destination_site_id,destination_site_name,volume\n");
                for (EarthworkAllocationMatrix.Transfer transfer : projectReport.crossSiteAllocationMatrix().transfers()) {
                    csv.append(csvCell("cross_site_allocation"))
                        .append(',')
                        .append(csvCell(transfer.sourceZoneId()))
                        .append(',')
                        .append(csvCell(resolveSiteName(projectReport, transfer.sourceZoneId())))
                        .append(',')
                        .append(csvCell(transfer.destinationZoneId()))
                        .append(',')
                        .append(csvCell(resolveSiteName(projectReport, transfer.destinationZoneId())))
                        .append(',')
                        .append(transfer.volume())
                        .append('\n');
                }
            }
        }
        return csv.toString();
    }

    private static String resolveSiteName(EarthworkProjectReport report, String siteId) {
        if (EarthworkAllocationMatrix.EXPORT.equals(siteId)) {
            return "EXPORT";
        }
        if (EarthworkAllocationMatrix.IMPORT.equals(siteId)) {
            return "IMPORT";
        }
        var snapshot = report.bySite().get(siteId);
        return snapshot != null ? snapshot.siteName() : siteId;
    }

    private static void appendRow(StringBuilder csv, String section, String key, Object value) {
        csv.append(csvCell(section))
            .append(',')
            .append(csvCell(key))
            .append(',')
            .append(csvCell(value))
            .append('\n');
    }

    private static String csvCell(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String text = String.valueOf(value);
        if (text.indexOf('"') >= 0) {
            text = text.replace("\"", "\"\"");
        }
        if (text.indexOf(',') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return "\"" + text + "\"";
        }
        return text;
    }

    private static String resolveZoneName(EarthworkProject project, String zoneId) {
        if (project == null || zoneId == null) {
            return zoneId != null ? zoneId : "";
        }
        GradingRegion region = project.getRegion(zoneId);
        if (region != null && region.getName() != null && !region.getName().isBlank()) {
            return region.getName();
        }
        return zoneId;
    }

    private static String resolveAllocationEndpoint(EarthworkProject project, String zoneId, boolean source) {
        if (EarthworkAllocationMatrix.EXPORT.equals(zoneId)) {
            return "EXPORT";
        }
        if (EarthworkAllocationMatrix.IMPORT.equals(zoneId)) {
            return "IMPORT";
        }
        return resolveZoneName(project, zoneId);
    }
}
