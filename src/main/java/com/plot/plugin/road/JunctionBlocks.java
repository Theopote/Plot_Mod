package com.plot.plugin.road;

import com.plot.plugin.road.pipeline.corridor.JunctionConstructionContext;
import com.plot.plugin.road.pipeline.corridor.JunctionConstructionReport;
import com.plot.plugin.road.pipeline.corridor.JunctionElevationField;
import com.plot.plugin.road.pipeline.corridor.JunctionValidationManifest;
import com.plot.plugin.road.pipeline.corridor.JunctionWorldFootprint;
import com.plot.plugin.road.solid.RoadSolidModel;

/**
 * 路口实体蓝图（平面坐标 + 标高 + 层），落地前不含 BlockPos。
 * <p>
 * 独立顶层类，避免 Loom/增量编译时嵌套 {@code $JunctionBlocks} 与外壳短暂不同步导致
 * {@link NoClassDefFoundError}。
 */
public final class JunctionBlocks {
    private final RoadSolidModel solids = new RoadSolidModel();
    private JunctionConstructionReport report = JunctionConstructionReport.empty("");
    private JunctionValidationManifest validationManifest = JunctionValidationManifest.empty("");
    private JunctionElevationField elevationField = JunctionElevationField.inactive(0);
    private JunctionConstructionContext constructionContext =
        JunctionConstructionContext.openOnly("", JunctionWorldFootprint.empty(),
            JunctionElevationField.inactive(0), null);

    public RoadSolidModel getSolids() {
        return solids;
    }

    public JunctionConstructionReport report() {
        return report;
    }

    public JunctionValidationManifest validationManifest() {
        return validationManifest;
    }

    public JunctionElevationField elevationField() {
        return elevationField;
    }

    public JunctionConstructionContext constructionContext() {
        return constructionContext;
    }

    void setReport(JunctionConstructionReport report) {
        this.report = report != null ? report : JunctionConstructionReport.empty("");
    }

    void setValidationManifest(JunctionValidationManifest validationManifest) {
        this.validationManifest = validationManifest != null
            ? validationManifest
            : JunctionValidationManifest.empty("");
    }

    void setElevationField(JunctionElevationField elevationField) {
        this.elevationField = elevationField != null
            ? elevationField
            : JunctionElevationField.inactive(0);
    }

    void setConstructionContext(JunctionConstructionContext constructionContext) {
        this.constructionContext = constructionContext != null
            ? constructionContext
            : JunctionConstructionContext.openOnly("", JunctionWorldFootprint.empty(),
                JunctionElevationField.inactive(0), null);
    }

    public boolean isEmpty() {
        return solids.isEmpty();
    }
}
