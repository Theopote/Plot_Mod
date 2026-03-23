package com.plot.api.model;

import java.util.List;

/**
 * 项目接口，定义了项目的基本操作
 */
public interface IProject {
    /**
     * 获取项目名称
     * @return 项目名称
     */
    String getName();

    /**
     * 设置项目名称
     * @param name 新的项目名称
     */
    void setName(String name);

    /**
     * 获取项目描述
     * @return 项目描述
     */
    String getDescription();

    /**
     * 设置项目描述
     * @param description 新的项目描述
     */
    void setDescription(String description);

    /**
     * 获取项目中的所有画布
     * @return 画布列表
     */
    List<ICanvas> getCanvases();

    /**
     * 添加新画布
     * @param canvas 要添加的画布
     */
    void addCanvas(ICanvas canvas);

    /**
     * 移除画布
     * @param canvas 要移除的画布
     */
    void removeCanvas(ICanvas canvas);

    /**
     * 获取当前活动画布
     * @return 当前活动画布
     */
    ICanvas getActiveCanvas();

    /**
     * 设置当前活动画布
     * @param canvas 要设置为活动的画布
     */
    void setActiveCanvas(ICanvas canvas);

    /**
     * 保存项目
     * @param filePath 保存路径
     * @return 是否保存成功
     */
    boolean save(String filePath);

    /**
     * 加载项目
     * @param filePath 项目文件路径
     * @return 是否加载成功
     */
    boolean load(String filePath);

    /**
     * 获取项目创建时间
     * @return 创建时间的时间戳
     */
    long getCreationTime();

    /**
     * 获取项目最后修改时间
     * @return 最后修改时间的时间戳
     */
    long getLastModifiedTime();

    /**
     * 获取项目版本号
     * @return 版本号
     */
    String getVersion();

    /**
     * 创建项目快照（用于撤销/重做）
     * @return 快照ID
     */
    String createSnapshot();

    /**
     * 恢复到指定快照
     * @param snapshotId 快照ID
     * @return 是否恢复成功
     */
    boolean restoreSnapshot(String snapshotId);
}
