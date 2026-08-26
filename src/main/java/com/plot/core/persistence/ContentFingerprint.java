package com.plot.core.persistence;

import java.nio.file.Path;

/**
 * 内容指纹：用于跳过 deactivate + disable 等场景下的重复写盘。
 */
public final class ContentFingerprint {
    private ContentFingerprint() {
    }

    public static int of(String content, Path file) {
        if (content == null || file == null) {
            return 0;
        }
        return 31 * content.hashCode() + file.toAbsolutePath().normalize().hashCode();
    }

    /**
     * 可变跟踪器，供各 PersistenceManager / Plugin 持有。
     */
    public static final class Tracker {
        private int lastHash;
        private boolean hasSaved;

        public boolean isUnchanged(String content, Path file) {
            return hasSaved && of(content, file) == lastHash;
        }

        public void markSaved(String content, Path file) {
            lastHash = of(content, file);
            hasSaved = true;
        }

        public void reset() {
            lastHash = 0;
            hasSaved = false;
        }
    }
}
