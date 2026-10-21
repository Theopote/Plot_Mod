package com.plot.plugin.earthwork.volume;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场地级土方算量报告：合计 + 分 Zone 明细。
 */
public final class SiteEarthworkReport {
    public static final SiteEarthworkReport EMPTY = new SiteEarthworkReport(
        EarthworkVolumeReport.empty(),
        Map.of(),
        0L, 0L, 0L);

    private final EarthworkVolumeReport totals;
    private final Map<String, EarthworkVolumeReport> byZone;
    private final long unassignedCutChangedBlocks;
    private final long unassignedFillChangedBlocks;
    private final long structureChangedBlocks;

    public SiteEarthworkReport(
            EarthworkVolumeReport totals,
            Map<String, EarthworkVolumeReport> byZone) {
        this(totals, byZone, 0L, 0L, 0L);
    }

    public SiteEarthworkReport(
            EarthworkVolumeReport totals,
            Map<String, EarthworkVolumeReport> byZone,
            long unassignedCutChangedBlocks,
            long unassignedFillChangedBlocks,
            long structureChangedBlocks) {
        this.totals = totals != null ? totals : EarthworkVolumeReport.empty();
        this.byZone = byZone != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(byZone))
            : Map.of();
        this.unassignedCutChangedBlocks = Math.max(0L, unassignedCutChangedBlocks);
        this.unassignedFillChangedBlocks = Math.max(0L, unassignedFillChangedBlocks);
        this.structureChangedBlocks = Math.max(0L, structureChangedBlocks);
    }

    public static SiteEarthworkReport empty() {
        return EMPTY;
    }

    public EarthworkVolumeReport totals() {
        return totals;
    }

    public Map<String, EarthworkVolumeReport> byZone() {
        return byZone;
    }

    public EarthworkVolumeReport zoneReport(String zoneId) {
        if (zoneId == null) {
            return EarthworkVolumeReport.empty();
        }
        return byZone.getOrDefault(zoneId, EarthworkVolumeReport.empty());
    }

    public long unassignedCutChangedBlocks() {
        return unassignedCutChangedBlocks;
    }

    public long unassignedFillChangedBlocks() {
        return unassignedFillChangedBlocks;
    }

    public long structureChangedBlocks() {
        return structureChangedBlocks;
    }

    public SiteEarthworkReport withTotalsChangedBlocks(long cutChangedBlocks, long fillChangedBlocks) {
        return new SiteEarthworkReport(
            totals.withChangedBlocks(cutChangedBlocks, fillChangedBlocks),
            byZone,
            unassignedCutChangedBlocks,
            unassignedFillChangedBlocks,
            structureChangedBlocks);
    }

    /**
     * 用最终放置记录回写全场与各 Zone 的实际改方块数，保留几何挖填方量不变。
     *
     * @param zoneChangedBlocks zoneId → [cutChanged, fillChanged]
     */
    public SiteEarthworkReport withChangedBlocks(
            long cutChangedBlocks,
            long fillChangedBlocks,
            Map<String, long[]> zoneChangedBlocks) {
        return withChangedBlocks(cutChangedBlocks, fillChangedBlocks, zoneChangedBlocks, 0L, 0L, 0L);
    }

    public SiteEarthworkReport withChangedBlocks(
            long cutChangedBlocks,
            long fillChangedBlocks,
            Map<String, long[]> zoneChangedBlocks,
            long unassignedCut,
            long unassignedFill,
            long structureChanged) {
        Map<String, EarthworkVolumeReport> updatedZones = new LinkedHashMap<>();
        for (Map.Entry<String, EarthworkVolumeReport> entry : byZone.entrySet()) {
            long[] counts = zoneChangedBlocks != null ? zoneChangedBlocks.get(entry.getKey()) : null;
            long cut = counts != null && counts.length > 0 ? counts[0] : 0L;
            long fill = counts != null && counts.length > 1 ? counts[1] : 0L;
            updatedZones.put(entry.getKey(), entry.getValue().withChangedBlocks(cut, fill));
        }
        return new SiteEarthworkReport(
            totals.withChangedBlocks(cutChangedBlocks, fillChangedBlocks),
            updatedZones,
            unassignedCut,
            unassignedFill,
            structureChanged);
    }

    public static SiteEarthworkReport fromMetrics(
            VolumeMetrics totalsMetrics,
            Map<String, VolumeMetrics> zoneMetrics,
            MaterialConversionModel siteMaterialModel) {
        return fromMetrics(null, totalsMetrics, zoneMetrics, siteMaterialModel);
    }

    public static SiteEarthworkReport fromMetrics(
            EarthworkSite site,
            VolumeMetrics totalsMetrics,
            Map<String, VolumeMetrics> zoneMetrics) {
        MaterialConversionModel siteMaterial = site != null
            ? site.getMaterialModel()
            : MaterialConversionModel.DEFAULT;
        return fromMetrics(site, totalsMetrics, zoneMetrics, siteMaterial);
    }

    private static SiteEarthworkReport fromMetrics(
            EarthworkSite site,
            VolumeMetrics totalsMetrics,
            Map<String, VolumeMetrics> zoneMetrics,
            MaterialConversionModel siteMaterialModel) {
        MaterialConversionModel materials = siteMaterialModel != null
            ? siteMaterialModel
            : MaterialConversionModel.DEFAULT;
        Map<String, EarthworkVolumeReport> byZone = new LinkedHashMap<>();
        if (zoneMetrics != null) {
            for (Map.Entry<String, VolumeMetrics> entry : zoneMetrics.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                MaterialConversionModel zoneMaterials = materials;
                if (site != null) {
                    GradingZone zone = site.getZone(entry.getKey());
                    if (zone != null) {
                        zoneMaterials = zone.resolveMaterialModel(materials);
                    }
                }
                byZone.put(entry.getKey(), entry.getValue().toReport(zoneMaterials));
            }
        }
        return new SiteEarthworkReport(totalsMetrics.toReport(materials), byZone);
    }

    /**
     * 几何方量与方块变更计数（挖填分开统计）。
     */
    public static final class VolumeMetrics {
        private long geometricCutVolume;
        private long geometricFillVolume;
        private long cutChangedBlocks;
        private long fillChangedBlocks;

        public void addCut(long volume, long changedBlocks) {
            geometricCutVolume += Math.max(0L, volume);
            cutChangedBlocks += Math.max(0L, changedBlocks);
        }

        public void addFill(long volume, long changedBlocks) {
            geometricFillVolume += Math.max(0L, volume);
            fillChangedBlocks += Math.max(0L, changedBlocks);
        }

        public void add(VolumeMetrics other) {
            if (other == null) {
                return;
            }
            geometricCutVolume += other.geometricCutVolume;
            geometricFillVolume += other.geometricFillVolume;
            cutChangedBlocks += other.cutChangedBlocks;
            fillChangedBlocks += other.fillChangedBlocks;
        }

        public EarthworkVolumeReport toReport(MaterialConversionModel materials) {
            return EarthworkVolumeReport.fromMetrics(
                geometricCutVolume,
                geometricFillVolume,
                materials,
                cutChangedBlocks,
                fillChangedBlocks);
        }
    }
}
