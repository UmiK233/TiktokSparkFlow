package com.umik.tiktoksparkflow.mapper;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.SendHistoryRecordEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendHistoryMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void 每个好友每天只记录一次成功发送() {
        TiktokSenderConfiguration configuration = new TiktokSenderConfiguration();
        configuration.setSendHistoryDir(tempDir.resolve("history").toString());
        SendHistoryMapper mapper = new SendHistoryMapper(configuration, new ObjectMapper());
        String sentAt = OffsetDateTime.now(ZoneId.of(configuration.getTimeZone())).toString();

        mapper.record(new SendHistoryRecordEntity("task-1", "星火", "第一次", sentAt));
        mapper.record(new SendHistoryRecordEntity("task-2", "星火", "第二次", sentAt));

        assertTrue(mapper.wasSentToday("星火"));
        assertEquals(1, mapper.records(mapper.today()).size());
        assertEquals("第一次", mapper.records(mapper.today()).get(0).message());
    }
}
