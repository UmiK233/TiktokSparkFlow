package com.umik.tiktoksparkflow.mapper;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.FriendSelectionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendSelectionMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void 原样保存并读取好友选择() {
        Path file = tempDir.resolve("friend-selection.json");
        TiktokSenderConfiguration configuration = new TiktokSenderConfiguration();
        configuration.setFriendSelectionFile(file.toString());
        FriendSelectionMapper mapper = new FriendSelectionMapper(configuration, new ObjectMapper());

        FriendSelectionEntity saved = mapper.save(
                new FriendSelectionEntity(List.of(" 星火 ", "南.", "星火")));
        FriendSelectionEntity loaded = mapper.load();

        assertEquals(List.of(" 星火 ", "南.", "星火"), saved.selectedFriends());
        assertEquals(saved, loaded);
        assertTrue(file.toFile().isFile());
    }
}
