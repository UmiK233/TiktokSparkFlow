package com.umik.tiktoksparkflow.service.impl;

import com.umik.tiktoksparkflow.dto.RuntimeSettingsDTO;
import com.umik.tiktoksparkflow.config.BrowserSettingsChangedEvent;
import com.umik.tiktoksparkflow.entity.RuntimeSettingsEntity;
import com.umik.tiktoksparkflow.mapper.RuntimeSettingsMapper;
import com.umik.tiktoksparkflow.service.RuntimeSettingsService;
import com.umik.tiktoksparkflow.vo.RuntimeSettingsVO;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class RuntimeSettingsServiceImpl implements RuntimeSettingsService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private final RuntimeSettingsMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public RuntimeSettingsServiceImpl(RuntimeSettingsMapper mapper, ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public RuntimeSettingsVO get() {
        RuntimeSettingsEntity settings = mapper.load().orElseGet(() -> mapper.save(defaultSettings()));
        return toVO(settings);
    }

    @Override
    public RuntimeSettingsVO update(RuntimeSettingsDTO settings) {
        boolean previousHeadless = mapper.load()
                .map(existing -> Boolean.TRUE.equals(existing.headless()))
                .orElse(false);
        String scheduleTime = normalizeTime(settings.scheduleTime());
        String message = settings.message() == null ? "" : settings.message().trim();
        String notificationEmail = settings.loginExpiryNotificationEmail() == null
                ? "" : settings.loginExpiryNotificationEmail().trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("发送消息不能为空");
        }
        RuntimeSettingsEntity saved = mapper.save(new RuntimeSettingsEntity(
                settings.autoSendEnabled(), scheduleTime, message, settings.sendMessage(),
                settings.allowRepeatedSend(), settings.headless(), notificationEmail,
                settings.smtpHost(), settings.smtpPort(), settings.smtpUsername(), settings.smtpPassword(),
                settings.smtpFromEmail(), settings.smtpStarttls(), settings.loginExpiryEmailSubject(),
                settings.loginExpiryEmailContent()));
        if (previousHeadless != Boolean.TRUE.equals(saved.headless())) {
            eventPublisher.publishEvent(new BrowserSettingsChangedEvent());
        }
        return toVO(saved);
    }

    private RuntimeSettingsEntity defaultSettings() {
        return new RuntimeSettingsEntity(true, "00:01", "🔥", true, false, false, "",
                "", 587, "", "", "", true,
                "TiktokSparkFlow：抖音登录已失效",
                "自动任务检测到抖音登录已失效。为避免自动续火花失效，请尽快打开 TiktokSparkFlow 进行登录。");
    }

    private String normalizeTime(String value) {
        try {
            return LocalTime.parse(value == null ? "" : value, TIME_FORMAT).format(TIME_FORMAT);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("自动任务时间必须为 HH:mm，例如 00:02");
        }
    }

    private RuntimeSettingsVO toVO(RuntimeSettingsEntity settings) {
        return new RuntimeSettingsVO(settings.autoSendEnabled(), settings.scheduleTime(),
                settings.message(), settings.sendMessage(), Boolean.TRUE.equals(settings.allowRepeatedSend()),
                Boolean.TRUE.equals(settings.headless()), settings.loginExpiryNotificationEmail(),
                settings.smtpHost(), settings.smtpPort(), settings.smtpUsername(), settings.smtpPassword(),
                settings.smtpFromEmail(), Boolean.TRUE.equals(settings.smtpStarttls()),
                settings.loginExpiryEmailSubject(), settings.loginExpiryEmailContent());
    }
}
