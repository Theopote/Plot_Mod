package com.plot.plugin.earthwork.volume;

/**
 * 项目级材料平衡三层数字（压实填方 m³）。
 * <ul>
 *   <li>Gross：各场地缺量/余量之和（未做跨场地抵消）</li>
 *   <li>Internal：跨场地内部调配</li>
 *   <li>External：抵消后仍需场外进出口</li>
 * </ul>
 */
public record ProjectMaterialBalance(
        double grossImportDemand,
        double grossExportSurplus,
        double internalTransferVolume,
        double externalImportRequired,
        double externalExportRequired) {

    public static final ProjectMaterialBalance EMPTY = new ProjectMaterialBalance(0.0, 0.0, 0.0, 0.0, 0.0);

    public ProjectMaterialBalance {
        grossImportDemand = Math.max(0.0, grossImportDemand);
        grossExportSurplus = Math.max(0.0, grossExportSurplus);
        internalTransferVolume = Math.max(0.0, internalTransferVolume);
        externalImportRequired = Math.max(0.0, externalImportRequired);
        externalExportRequired = Math.max(0.0, externalExportRequired);
    }

    public static ProjectMaterialBalance fromSiteVolumes(EarthworkVolumeReport volumes) {
        if (volumes == null) {
            return EMPTY;
        }
        double deficit = volumes.compactedFillDeficit();
        double surplus = volumes.compactedFillSurplus();
        return new ProjectMaterialBalance(deficit, surplus, 0.0, deficit, surplus);
    }
}
