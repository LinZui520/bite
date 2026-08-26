package com.eamon.bite.config;

import com.eamon.bite.BiteMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 配置文件 IO 共用样板（config/bite/ 目录）。 */
final class ConfigFiles {
    private ConfigFiles() {}

    /**
     * 读取配置文件内容；文件不存在时写入默认内容并返回 null（调用方回退默认配置）。
     * IO 异常记日志后同样返回 null。JSON 语法错误不在本层处理（沿用 Gson 的
     * RuntimeException 直抛语义）。
     */
    static String readOrCreate(Path configDir, String fileName, String defaultJson) {
        Path file = configDir.resolve("bite").resolve(fileName);
        try {
            if (Files.exists(file)) {
                return Files.readString(file);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultJson);
            return null;
        } catch (IOException e) {
            BiteMod.LOGGER.error("Failed to load config {}, using defaults", fileName, e);
            return null;
        }
    }
}
