package com.umik.tiktoksparkflow.mapper;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.SendHistoryDayEntity;
import com.umik.tiktoksparkflow.entity.SendHistoryRecordEntity;
import com.umik.tiktoksparkflow.utils.Gmt8Time;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class SendHistoryMapper {
    private final Path directory;
    private final ObjectMapper objectMapper;
    private final ZoneId zoneId;

    public SendHistoryMapper(
            TiktokSenderConfiguration configuration,
            ObjectMapper objectMapper
    ) {
        this.directory = configuration.sendHistoryPath();
        this.objectMapper = objectMapper;
        this.zoneId = ZoneId.of(configuration.getTimeZone());
    }

    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    public synchronized boolean wasSentToday(String targetNickname) {
        return records(today()).stream()
                .anyMatch(record -> targetNickname.equals(record.targetNickname()));
    }

    public synchronized List<SendHistoryRecordEntity> records(LocalDate date) {
        Path file = dayFile(date);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            SendHistoryDayEntity loaded = objectMapper.readValue(file.toFile(), SendHistoryDayEntity.class);
            List<SendHistoryRecordEntity> normalized = loaded.records().stream()
                    .map(record -> new SendHistoryRecordEntity(
                            record.taskId(), record.targetNickname(), record.message(),
                            Gmt8Time.normalize(record.sentAt())))
                    .toList();
            if (!normalized.equals(loaded.records())) {
                save(new SendHistoryDayEntity(loaded.date(), normalized));
            }
            return normalized;
        } catch (RuntimeException error) {
            throw new IllegalStateException("读取发送记录失败：" + file, error);
        }
    }

    public synchronized void record(SendHistoryRecordEntity record) {
        LocalDate date = LocalDate.parse(record.sentAt().substring(0, 10));
        List<SendHistoryRecordEntity> records = new ArrayList<>(records(date));
        boolean exists = records.stream().anyMatch(existing ->
                existing.targetNickname().equals(record.targetNickname()));
        if (exists) {
            return;
        }
        records.add(record);
        save(new SendHistoryDayEntity(date.toString(), records));
    }

    private void save(SendHistoryDayEntity history) {
        try {
            Files.createDirectories(directory);
            Path file = dayFile(LocalDate.parse(history.date()));
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), history);
            move(temporary, file);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("保存发送记录失败：" + history.date(), error);
        }
    }

    private Path dayFile(LocalDate date) {
        return directory.resolve(date + ".json");
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
