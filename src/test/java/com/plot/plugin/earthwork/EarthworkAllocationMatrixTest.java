package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.model.EarthMaterialProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkAllocationMatrixTest {

    @Test
    void greedyAllocationDistributesCutToFillZonesAndExport() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L));
        byZone.put("b", report(0L, 6_000L));
        byZone.put("c", report(0L, 3_000L));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);

        assertEquals(6_000L, findTransfer(matrix, "a", "b"));
        assertEquals(3_000L, findTransfer(matrix, "a", "c"));
        assertEquals(1_000L, findTransfer(matrix, "a", EarthworkAllocationMatrix.EXPORT));
    }

    @Test
    void importCreatedWhenFillExceedsCut() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("cut", report(4_000L, 0L));
        byZone.put("fill", report(0L, 7_000L));

        EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, null);

        assertEquals(4_000L, findTransfer(matrix, "cut", "fill"));
        assertEquals(3_000L, findTransfer(matrix, EarthworkAllocationMatrix.IMPORT, "fill"));
    }

    @Test
    void projectReportAggregatesSiteTotalsAndAllocation() {
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        byZone.put("a", report(10_000L, 0L));
        byZone.put("b", report(0L, 6_000L));
        byZone.put("c", report(0L, 3_000L));
        SiteEarthworkReport siteReport = new SiteEarthworkReport(
            EarthworkVolumeReport.fromMetrics(10_000L, 9_000L, EarthMaterialProperties.DEFAULT, 0L, 0L),
            byZone);

        EarthworkProjectReport report = EarthworkProjectReport.Builder.build(null, siteReport);

        assertEquals(10_000L, report.totalCut());
        assertEquals(9_000L, report.totalFill());
        assertEquals(1_000L, report.netVolume());
        assertEquals(9_000.0, report.reusableCut(), 1e-6);
        assertEquals(10_000L, report.allocationMatrix().volumeFrom("a"));
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
        return EarthworkVolumeReport.fromMetrics(cut, fill, EarthMaterialProperties.DEFAULT, 0L, 0L);
    }
}
