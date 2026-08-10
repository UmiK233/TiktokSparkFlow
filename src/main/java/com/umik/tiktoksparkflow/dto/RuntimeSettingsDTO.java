package com.umik.tiktoksparkflow.dto;

public record RuntimeSettingsDTO(
        boolean autoSendEnabled,
        String scheduleTime,
        String message,
        boolean sendMessage,
        boolean allowRepeatedSend,
        boolean headless,
        String loginExpiryNotificationEmail,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        String smtpPassword,
        String smtpFromEmail,
        boolean smtpStarttls,
        String loginExpiryEmailSubject,
        String loginExpiryEmailContent
) {
}
