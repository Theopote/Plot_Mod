package com.plot.core.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 原子文件写入：先写 .tmp，再 ATOMIC_MOVE（不支持则回退 REPLACE）。
 * <p>
 * 抽自 Road / Building / Earthwork / Core Project 已验证的写盘路径。
 */
public final class AtomicFileWriter {
    public static final String TEMP_SUFFIX = ".tmp";

    @FunctionalInterface
    public interface ContentValidator {
        void validate(String content) throws Exception;
    }

    public record Options(boolean backup, boolean autosave, ContentValidator validator) {
        public static Options simple() {
            return new Options(false, false, null);
        }

        public static Options projectDocument(ContentValidator validator) {
            return new Options(true, true, validator);
        }
    }

    private AtomicFileWriter() {
    }

    public static void write(Path target, String content) throws IOException {
        write(target, content, Options.simple());
    }

    public static void write(Path target, String content, Options options) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        Options opts = options != null ? options : Options.simple();

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = resolveTemp(target);
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);

            if (opts.validator() != null) {
                try {
                    opts.validator().validate(Files.readString(tempFile, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new PersistenceException(
                        PersistenceException.Reason.VALIDATION_FAILED,
                        "Atomic write validation failed: " + target.getFileName(),
                        e);
                }
            }

            if (opts.backup()) {
                BackupManager.backupExisting(target);
            }

            atomicMove(tempFile, target);

            if (opts.autosave()) {
                BackupManager.updateAutosave(target);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    private static Path resolveTemp(Path target) {
        // Core 使用 target + ".tmp"；插件使用 sibling fileName + ".tmp" —— 统一为 sibling 风格，
        // 对 "a.json" → "a.json.tmp"，与 Road 行为一致且可覆盖同目录。
        return target.resolveSibling(target.getFileName().toString() + TEMP_SUFFIX);
    }

    public static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
