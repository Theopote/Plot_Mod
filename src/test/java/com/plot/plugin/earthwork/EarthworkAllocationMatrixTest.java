package com.plot.plugin.earthwork;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调配矩阵类仍可离线计算几何余缺匹配；主生成管线与项目报告不再使用它。
 * 数值按 Minecraft 1:1（挖−填）断言。
 */
class EarthworkAllocationMatrixTest {

    @Test
    void greedyAllocationDistributesGeometricSurplus() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L));
        byZone.put("b", report(0L, 6_000L));
        byZone.put("c", report(0L, 3_000L));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone);

        assertEquals(6_000L, findTransfer(matrix, "a", "b"));
        assertEquals(3_000L, findTransfer(matrix, "a", "c"));
        assertEquals(1_000L, findTransfer(matrix, "a", EarthworkAllocationMatrix.EXPORT));
        assertEquals(0L, findTransfer(matrix, EarthworkAllocationMatrix.IMPORT, "c"));
    }

    @Test
    void conversionRatiosDoNotAffectAllocationSupply() {
        MaterialConversionModel lowReuse = new MaterialConversionModel(0.50f, 1.0f);
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L, lowReuse));
        byZone.put("b", report(0L, 4_000L, lowReuse));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone);

        assertEquals(4_000L, findTransfer(matrix, "a", "b"));
        assertEquals(6_000L, findTransfer(matrix, "a", EarthworkAllocationMatrix.EXPORT));
    }

    @Test
    void importCreatedWhenFillExceedsCut() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("cut", report(4_000L, 0L));
        byZone.put("fill", report(0L, 7_000L));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone);

        assertEquals(4_000L, findTransfer(matrix, "cut", "fill"));
        assertEquals(3_000L, findTransfer(matrix, EarthworkAllocationMatrix.IMPORT, "fill"));
    }

    @Test
    void projectReportOmitsAllocationMatrix() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L));
        byZone.put("b", report(0L, 6_000L));
        byZone.put("c", report(0L, 3_000L));
        SiteEarthworkReport siteReport = new SiteEarthworkReport(
            EarthworkVolumeReport.fromMetrics(10_000L, 9_000L, MaterialConversionModel.DEFAULT, 0L, 0L),
            byZone);

        EarthworkProjectReport report = EarthworkProjectReport.Builder.build(null, siteReport);

        assertEquals(10_000L, report.totalCut());
        assertEquals(9_000L, report.totalFill());
        assertEquals(1_000L, report.netVolume());
        assertTrue(report.allocationMatrix().isEmpty());
    }

    private static long findTransfer(EarthworkAllocationMatrix matrix, String source, String destination) {
        return matrix.transfers().stream()
            .filter(t -> t.sourceZoneId().equals(source) && t.destinationZoneId().equals(destination))
            .mapToLong(EarthworkAllocationMatrix.Transfer::volume)
            .sum();
    }

    private static EarthworkVolumeReport report(long cut, long fill) {
        return report(cut, fill, MaterialConversionModel.DEFAULT);
    }

    private static EarthworkVolumeReport report(long cut, long fill, MaterialConversionModel model) {
        return EarthworkVolumeReport.fromMetrics(cut, fill, model, 0L, 0L);
    }
}
