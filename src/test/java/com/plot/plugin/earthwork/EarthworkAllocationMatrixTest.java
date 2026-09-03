package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarthworkAllocationMatrixTest {

    @Test
    void greedyAllocationDistributesCutToFillZonesAndExport() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L));
        byZone.put("b", report(0L, 6_000L));
        byZone.put("c", report(0L, 3_000L));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);

        assertEquals(6_000L, findTransfer(matrix, "a", "b"));
        assertEquals(2_280L, findTransfer(matrix, "a", "c"));
        assertEquals(0L, findTransfer(matrix, "a", EarthworkAllocationMatrix.EXPORT));
        assertEquals(720L, findTransfer(matrix, EarthworkAllocationMatrix.IMPORT, "c"));
    }

    @Test
    void materialAwareAllocationRespectsReusableRatio() {
        MaterialConversionModel lowReuse = new MaterialConversionModel(0.50f, 1.0f);
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L, lowReuse));
        byZone.put("b", report(0L, 4_000L, lowReuse));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);

        assertEquals(4_000L, findTransfer(matrix, "a", "b"));
        assertEquals(1_000L, findTransfer(matrix, "a", EarthworkAllocationMatrix.EXPORT));
        assertEquals(0L, findTransfer(matrix, EarthworkAllocationMatrix.IMPORT, "b"));
    }

    @Test
    void importCreatedWhenFillExceedsCut() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("cut", report(4_000L, 0L));
        byZone.put("fill", report(0L, 7_000L));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);

        assertEquals(3_312L, findTransfer(matrix, "cut", "fill"));
        assertEquals(3_688L, findTransfer(matrix, EarthworkAllocationMatrix.IMPORT, "fill"));
    }

    @Test
    void sameGeometricCutAllocatesByMaterialSupply() {
        MaterialConversionModel highReuse = MaterialConversionModel.DEFAULT;
        MaterialConversionModel lowReuse = new MaterialConversionModel(0.50f, 1.0f);
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("high", report(10_000L, 0L, highReuse));
        byZone.put("low", report(10_000L, 0L, lowReuse));
        byZone.put("fill", report(0L, 10_000L));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);

        assertEquals(8_280L, findTransfer(matrix, "high", "fill"));
        assertEquals(1_720L, findTransfer(matrix, "low", "fill"));
        assertEquals(3_280L, findTransfer(matrix, "low", EarthworkAllocationMatrix.EXPORT));
        assertEquals(0L, findTransfer(matrix, EarthworkAllocationMatrix.IMPORT, "fill"));
    }

    @Test
    void projectReportAggregatesSiteTotalsAndAllocation() {
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
        assertEquals(9_000.0, report.reusableCut(), 1e-6);
        assertEquals(8_280L, report.allocationMatrix().volumeFrom("a"));
    }

    private static long volume(
            EarthworkAllocationMatrix matrix,
            String source,
            String destination) {
        return matrix.transfers().stream()
            .filter(transfer -> transfer.sourceZoneId().equals(source)
                && transfer.destinationZoneId().equals(destination))
            .mapToLong(EarthworkAllocationMatrix.Transfer::volume)
            .sum();
    }

    private static long findTransfer(
            EarthworkAllocationMatrix matrix,
            String source,
            String destination) {
        return volume(matrix, source, destination);
    }

    private static EarthworkVolumeReport report(long cut, long fill) {
        return report(cut, fill, MaterialConversionModel.DEFAULT);
    }

    private static EarthworkVolumeReport report(long cut, long fill, MaterialConversionModel materials) {
        return EarthworkVolumeReport.fromMetrics(cut, fill, materials, 0L, 0L);
    }
}
