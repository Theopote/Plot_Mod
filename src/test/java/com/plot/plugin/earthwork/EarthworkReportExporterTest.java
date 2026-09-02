package com.plot.plugin.earthwork;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.EarthworkReportExporter;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkReportExporterTest {

    private static final MaterialConversionModel LEGACY_LIKE =
        MaterialConversionModel.fromLegacyFillFactor(1.1f);

    @Test
    void exportsCsvAndJson(@TempDir Path dir) throws Exception {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = levelPadRegion(0, 3, 0, 3, 65, false);
        project.addRegion(region);

        EarthworkGenerationResult preview = new EarthworkGenerationResult();
        preview.volumeReport = EarthworkVolumeReport.fromMetrics(12L, 8L, LEGACY_LIKE, 4L, 3L);
        preview.projectReport = EarthworkProjectReport.Builder.buildFromSingleZone(
            project, project.getActiveSite(), region.getId(), preview.volumeReport);
        preview.calculationCellCount = 16;
        preview.resolvedElevation = 65;
        preview.resolvedElevationMin = 65;
        preview.resolvedElevationMax = 65;

        EarthworkReportExporter.ExportResult result =
            EarthworkReportExporter.exportPreview(preview, project, region, dir);

        assertTrue(Files.exists(result.csvPath()));
        assertTrue(Files.exists(result.jsonPath()));
        String csv = Files.readString(result.csvPath());
        String json = Files.readString(result.jsonPath());
        assertTrue(csv.contains("geometric_cut"));
        assertTrue(csv.contains("zone_volume"));
        assertTrue(json.contains("\"geometricCutVolume\""));
        assertTrue(json.contains(region.getId()));
    }

    @Test
    void csvContainsVolumeMetrics(@TempDir Path dir) throws Exception {
        GradingRegion region = levelPadRegion(0, 1, 0, 1, 66, false);

        EarthworkGenerationResult preview = new EarthworkGenerationResult();
        preview.volumeReport = EarthworkVolumeReport.fromMetrics(10L, 5L, region.getMaterialProperties(), 3L, 2L);

        EarthworkReportExporter.ExportResult result =
            EarthworkReportExporter.exportPreview(preview, null, region, dir);

        String content = Files.readString(result.csvPath());
        assertTrue(content.contains("geometric_cut,10"));
        assertTrue(content.contains("total_changed_blocks,5"));
    }
}
