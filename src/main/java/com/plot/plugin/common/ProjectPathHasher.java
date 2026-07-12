package com.plot.plugin.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 按主工程文件路径生成稳定的项目子目录文件名（Building / Earthwork / Road 共用）。
 */
public final class ProjectPathHasher {
    private static final int HASH_PREFIX_LENGTH = 16;

    private ProjectPathHasher() {
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

    public static String projectFileName(String filePath) {
        return hashPath(filePath) + ".json";
    }
}
