package com.plot.core.persistence;

/**
 * 带 formatVersion 的版本化文档标记。
 */
public interface VersionedDocument {
    int formatVersion();
}
