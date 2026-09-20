package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.utils.PlotI18n;

/**
 * 线路级杆塔造型实例：相对 base preset / 共享 design 的 fork，仅绑定到一条线路。
 * <p>
 * 微调或从线路打开设计器时不改写内置预设或共享用户造型；生效值写入 footprint，
 * 完整 Legacy 几何 fork 存于 {@code line-instance:{lineId}}。
 */
public final class LinePoleDesignOverrides {
    public static final String ID_PREFIX = "line-instance:";

    private LinePoleDesignOverrides() {
    }

    public static boolean isLineInstanceDesignId(String designId) {
        return designId != null && designId.startsWith(ID_PREFIX);
    }

    public static String lineInstanceDesignId(PowerLineFootprint line) {
        if (line == null || line.getId() == null) {
            return null;
        }
        return ID_PREFIX + line.getId();
    }

    /**
     * 打开设计器时优先加载该线路已有实例 fork，否则从 base 造型加载。
     */
    public static String resolveOpenDesignId(
            PowerLineFootprint line,
            String baseDesignId,
            PowerLineDesignProject project) {
        if (line == null) {
            return baseDesignId;
        }
        String instanceId = lineInstanceDesignId(line);
        if (project != null && project.getDesign(instanceId) != null) {
            return instanceId;
        }
        if (isLineInstanceDesignId(line.getPoleDesignId())) {
            return line.getPoleDesignId();
        }
        if (baseDesignId != null && !baseDesignId.isBlank()) {
            return baseDesignId;
        }
        return line.getPoleDesignId();
    }

    /**
     * 将 draft 保存为该线路私有实例，并指向 footprint。不修改内置或共享模板 id。
     */
    public static boolean saveLineInstance(
            PowerLineFootprint line,
            PoleDesign draft,
            PowerLineDesignProject project) {
        if (line == null || draft == null || project == null) {
            return false;
        }
        String instanceId = lineInstanceDesignId(line);
        PoleDesign saved = copyAsInstance(draft, instanceId, line.getName());
        project.addDesign(saved);
        if (!instanceId.equals(line.getPoleDesignId())) {
            line.setPoleDesignId(instanceId);
        }
        PowerLineStyleEditor.afterStyleEdit(line);
        return true;
    }

    public static void removeLineInstance(PowerLineFootprint line, PowerLineDesignProject project) {
        if (line == null || project == null) {
            return;
        }
        project.removeDesign(lineInstanceDesignId(line));
    }

    private static PoleDesign copyAsInstance(PoleDesign source, String instanceId, String lineName) {
        PoleDesign copy = new PoleDesign(instanceId, instanceDisplayName(source, lineName));
        copy.setLayers(source.getLayers());
        copy.setAttachments(source.getAttachments());
        copy.setTowerStructure(source.getTowerStructure());
        copy.setPlanningMetadata(source.getPlanningMetadata());
        copy.setGeneratorConfig(source.getGeneratorConfig());
        return copy;
    }

    private static String instanceDisplayName(PoleDesign source, String lineName) {
        String baseName = source != null ? source.getName() : "";
        if (lineName != null && !lineName.isBlank()) {
            return PlotI18n.tr("plugin.powerline.design.line_instance_name", lineName, baseName);
        }
        if (baseName != null && !baseName.isBlank()) {
            return baseName;
        }
        return PlotI18n.tr("plugin.powerline.pole_design_default");
    }

    /** 是否应把 catalog 中的共享设计当作只读模板（不可就地覆盖）。 */
    public static boolean isSharedTemplateId(String designId, PowerLineDesignProject project) {
        if (designId == null || designId.isBlank() || isLineInstanceDesignId(designId)) {
            return false;
        }
        if (PoleDesignCatalog.isBuiltinId(designId)) {
            return true;
        }
        return project != null && project.getDesign(designId) != null;
    }
}
