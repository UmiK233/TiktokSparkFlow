package com.umik.tiktoksparkflow.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gmt8TimeTest {
    @Test
    void utc旧时间会转换为统一的Gmt8格式() {
        assertEquals("2026-08-05 02:00:00",
                Gmt8Time.normalize("2026-08-04T18:00:00Z"));
    }

    @Test
    void 当前时间为不带后缀的统一格式() {
        assertTrue(Gmt8Time.now().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }
}
