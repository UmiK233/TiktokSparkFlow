package com.umik.tiktoksparkflow.mapper;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.RuntimeSettingsEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Component
public class RuntimeSettingsMapper {
    private final Path file;
    private final ObjectMapper objectMapper;

    public RuntimeSettingsMapper(TiktokSenderConfiguration configuration, ObjectMapper objectMapper) {
        this.file = configuration.runtimeSettingsPath();
        this.objectMapper = objectMapper;
    }

    public synchronized Optional<RuntimeSettingsEntity> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), RuntimeSettingsEntity.class));
        } catch (RuntimeException error) {
            throw new IllegalStateException("读取运行配置失败：" + file, error);
        }
    }

    public synchronized RuntimeSettingsEntity save(RuntimeSettingsEntity settings) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), settings);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            return settings;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("保存运行配置失败：" + file, error);
        }
    }
}
