package com.umik.tiktoksparkflow.utils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 项目内所有对外与持久化时间统一使用 GMT+8。 */
public final class Gmt8Time {
    public static final ZoneId ZONE = ZoneId.of("GMT+8");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Gmt8Time() {
    }

    public static String now() {
        return OffsetDateTime.now(ZONE).format(FORMATTER);
    }

    /** 将旧缓存的 ISO/UTC 时间转换为统一格式；无法识别的文本原样保留。 */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        if (value.endsWith(" GMT+8")) {
            return value.substring(0, value.length() - " GMT+8".length());
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).format(FORMATTER);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, FORMATTER).atZone(ZONE).format(FORMATTER);
            } catch (DateTimeParseException ignoredAgain) {
                return value;
            }
        }
    }
}
