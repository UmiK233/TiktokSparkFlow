package com.umik.tiktoksparkflow.mapper;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.SendTaskEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

@Component
public class SendTaskMapper {
    private final Path directory;
    private final ObjectMapper objectMapper;

    public SendTaskMapper(
            TiktokSenderConfiguration configuration,
            ObjectMapper objectMapper
    ) {
        this.directory = configuration.sendTaskPath();
        this.objectMapper = objectMapper;
    }

    public synchronized List<SendTaskEntity> loadAll() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::read)
                    .sorted(Comparator.comparing(SendTaskEntity::createdAt).reversed())
                    .toList();
        } catch (IOException error) {
            throw new IllegalStateException("读取发送任务目录失败：" + directory, error);
        }
    }

    public synchronized void save(SendTaskEntity task) {
        try {
            Files.createDirectories(directory);
            Path file = taskFile(task.taskId());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), task);
            move(temporary, file);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("保存发送任务失败：" + task.taskId(), error);
        }
    }

    private SendTaskEntity read(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), SendTaskEntity.class);
        } catch (RuntimeException error) {
            throw new IllegalStateException("读取发送任务失败：" + file, error);
        }
    }

    private Path taskFile(String taskId) {
        return directory.resolve(taskId + ".json");
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
