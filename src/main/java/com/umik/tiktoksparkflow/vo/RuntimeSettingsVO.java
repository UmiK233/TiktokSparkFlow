package com.umik.tiktoksparkflow.vo;

public record RuntimeSettingsVO(
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
