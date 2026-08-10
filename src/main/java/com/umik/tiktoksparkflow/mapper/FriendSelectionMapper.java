package com.umik.tiktoksparkflow.mapper;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.FriendSelectionEntity;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class FriendSelectionMapper {
    private final Path file;
    private final ObjectMapper objectMapper;

    public FriendSelectionMapper(
            TiktokSenderConfiguration configuration,
            ObjectMapper objectMapper
    ) {
        this.file = configuration.friendSelectionPath();
        this.objectMapper = objectMapper;
    }

    public synchronized FriendSelectionEntity load() {
        if (!Files.exists(file)) {
            return FriendSelectionEntity.empty();
        }
        try {
            return objectMapper.readValue(file.toFile(), FriendSelectionEntity.class);
        } catch (RuntimeException error) {
            throw new IllegalStateException("读取好友选择配置失败：" + file, error);
        }
    }

    public synchronized FriendSelectionEntity save(FriendSelectionEntity selection) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), selection);
            moveTemporaryFile(temporary);
            return selection;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("保存好友选择配置失败：" + file, error);
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
