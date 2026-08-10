package com.umik.tiktoksparkflow.mapper;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.FriendListEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class FriendListMapper {
    private final Path file;
    private final ObjectMapper objectMapper;

    public FriendListMapper(
            TiktokSenderConfiguration configuration,
            ObjectMapper objectMapper
    ) {
        this.file = configuration.friendListPath();
        this.objectMapper = objectMapper;
    }

    public synchronized FriendListEntity load() {
        if (!Files.exists(file)) {
            return FriendListEntity.empty();
        }
        try {
            return objectMapper.readValue(file.toFile(), FriendListEntity.class);
        } catch (RuntimeException error) {
            throw new IllegalStateException("读取好友列表缓存失败：" + file, error);
        }
    }

    public synchronized FriendListEntity save(FriendListEntity friendList) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), friendList);
            moveTemporaryFile(temporary);
            return friendList;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("保存好友列表缓存失败：" + file, error);
        }
    }

    private void moveTemporaryFile(Path temporary) throws IOException {
        try {
            Files.move(temporary, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
