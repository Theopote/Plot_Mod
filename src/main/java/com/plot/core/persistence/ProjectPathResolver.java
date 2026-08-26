package com.plot.core.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 按主工程路径生成稳定的 sidecar 文件名（Road / Building / Earthwork 共用）。
 */
public final class ProjectPathResolver {
    private static final int HASH_PREFIX_LENGTH = 16;

    private ProjectPathResolver() {
    }

    public static String hashPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "default";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(filePath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, HASH_PREFIX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            return "default";
        }
    }

    public static String sidecarFileName(String projectPath) {
        return hashPath(projectPath) + ".json";
    }

    public static Path resolveSidecar(Path dataRoot, String subdir, String projectPath) {
        return dataRoot.resolve(subdir).resolve(sidecarFileName(projectPath));
    }
}
