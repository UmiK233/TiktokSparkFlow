package com.umik.tiktoksparkflow.mapper;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.FriendListEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendListMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void 保存并读取完整好友列表() {
        Path file = tempDir.resolve("friends.json");
        TiktokSenderConfiguration configuration = new TiktokSenderConfiguration();
        configuration.setFriendListFile(file.toString());
        FriendListMapper mapper = new FriendListMapper(configuration, new ObjectMapper());
        FriendListEntity source = new FriendListEntity(
                List.of("星火", "南.", "麻辣红莉栖"),
                "2026-08-04T22:00:00+08:00");

        FriendListEntity saved = mapper.save(source);

        assertEquals(source, saved);
        assertEquals(source, mapper.load());
        assertTrue(file.toFile().isFile());
    }
}
