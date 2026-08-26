package com.plot.plugin.common;

import com.plot.core.persistence.ProjectPathResolver;

/**
 * 按主工程文件路径生成稳定的项目子目录文件名（Building / Earthwork / Road 共用）。
 * <p>
 * 实现已抽到 {@link ProjectPathResolver}；本类保留为插件侧兼容门面。
 */
public final class ProjectPathHasher {
    private ProjectPathHasher() {
    }

    public static String hashPath(String filePath) {
        return ProjectPathResolver.hashPath(filePath);
    }

    public static String projectFileName(String filePath) {
        return ProjectPathResolver.sidecarFileName(filePath);
    }
}
