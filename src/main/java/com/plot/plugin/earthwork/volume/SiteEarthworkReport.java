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
        Map.of());

    private final EarthworkVolumeReport totals;
    private final Map<String, EarthworkVolumeReport> byZone;

    public SiteEarthworkReport(
            EarthworkVolumeReport totals,
            Map<String, EarthworkVolumeReport> byZone) {
        this.totals = totals != null ? totals : EarthworkVolumeReport.empty();
        this.byZone = byZone != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(byZone))
            : Map.of();
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
